package activity;
/*
 * Created by fanwe on 2017/3/29.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.example.fanwe.bluetoothlocation.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ScanActivity extends AppCompatActivity{
    private static final int ENABLE_BLUETOOTH = 1;
    int RSSI_LIMIT = 15, BLE_CHOOSED_NUM = 3;

    TextView scanText1, scanText2, scanText3;  //注册两块文字块
    Double[] location = new Double[2];
    Map<String,ArrayList<Double>> m1 = new HashMap<>();  //储存RSSI的MAP
    Map<String,Double> m2 = new HashMap<>();     //过滤后的RSSI的Map
    ArrayList<String> m3 = new ArrayList<>();
    StringBuffer stringBuffer = new StringBuffer();
    Map<String,Double[]> bleDevLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    Integer TIME0 = 200;
    float[] rotVecValues = {0,0,0,0}, accValues = {0,0,0}, gyroValues = {0,0,0};
    SensorManager sensorManager;   //注册SensorManager

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String remoteMAC;
            final Short rssi;
            if (remoteDevice != null) {
                remoteMAC = remoteDevice.getAddress();
                if (bleDevLoc.containsKey(remoteMAC)) {
                    rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                    if (!dFinished.equals(intent.getAction())) {
                        if (m1.containsKey(remoteMAC)) {
                            ArrayList<Double> list1 = m1.get(remoteMAC);
                            list1.add(0, (double) rssi);
                            m1.put(remoteMAC, list1);
                        } else {
                            ArrayList<Double> list = new ArrayList<>();
                            list.add((double) rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
                            m1.put(remoteMAC, list);
                        }
                        m2.put(remoteMAC, LogarNormalDistribution(m1.get(remoteMAC)));   //更新MAC地址对应信号强度的map
                        if (m2.size() > 2) {
                            m3 = Sort(m2, BLE_CHOOSED_NUM);     //得到最近的
//                            Log.d("m3", m3.toString());
                            location = MassCenterLocation(m3, bleDevLoc);   //通过质心定位得到位置
                            Calendar now = Calendar.getInstance();
                            Integer minute = now.get(Calendar.MINUTE);
                            Integer second = now.get(Calendar.SECOND);
                            String string = minute.toString() + ":" + second.toString() + " " + Arrays.toString(location) + "\n";
                            stringBuffer.append(string);
                            scanText1.setText(stringBuffer);
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        scanText1 = (TextView)findViewById(R.id.scanText1);
        scanText2 = (TextView)findViewById(R.id.scanText2);
        scanText3 = (TextView)findViewById(R.id.scanText3);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,accSensor,SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,gyroSensor,SensorManager.SENSOR_DELAY_GAME);

        Double[] location21 = {11.5,0.7};
        Double[] location22 = {15.8,0.7};
        Double[] location23 = {7.8, 4.7};
        Double[] location24 = {11.8, 4.7};
        Double[] location25 = {15.8, 4.7};
        Double[] location26 = {19.8, 4.7};
        Double[] location27 = {7.8, 8.7};
        Double[] location28 = {11.8, 8.7};
        Double[] location29 = {15.8, 8.7};
        Double[] location30 = {19.8, 8.7};
        bleDevLoc.put("19:18:FC:01:F1:0E",location21);
        bleDevLoc.put("19:18:FC:01:F1:0F",location22);
        bleDevLoc.put("19:18:FC:01:F0:F8",location23);
        bleDevLoc.put("19:18:FC:01:F0:F9",location24);
        bleDevLoc.put("19:18:FC:01:F0:FA",location25);
        bleDevLoc.put("19:18:FC:01:F0:FB",location26);
        bleDevLoc.put("19:18:FC:01:F0:FC",location27);
        bleDevLoc.put("19:18:FC:01:F0:FD",location28);
        bleDevLoc.put("19:18:FC:01:F0:FE",location29);
        bleDevLoc.put("19:18:FC:01:F0:FF",location30);

        initBluetooth();
        startDiscovery();

        //设置每隔TIME0更新UI
        Timer updateTimer = new Timer("Update");
        updateTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                updateGUI();
            }
        },0,TIME0);
    }

    //事件监听器
    private SensorEventListener listener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()){
                case Sensor.TYPE_ROTATION_VECTOR :
                    rotVecValues = event.values.clone();
                    break;
                case Sensor.TYPE_GYROSCOPE :
                    gyroValues = event.values.clone();
                    break;
                case Sensor.TYPE_ACCELEROMETER :
                    accValues = event.values.clone();
                    break;
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    //UI 更新方法
    private void updateGUI(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                String need1 = String.valueOf(rotVecValues[0]) + '\n' + String.valueOf(rotVecValues[1]) + '\n' + String.valueOf(rotVecValues[2]) + '\n' + String.valueOf(rotVecValues[3]) + '\n';  //展示Sx，Sy，ax，ay
//                scanText1.setText(need1);
//                String need2 = String.valueOf(accValues[0]) + '\n' + String.valueOf(accValues[1]) + '\n' + String.valueOf(accValues[2]) + '\n';  //展示Sx，Sy，ax，ay
//                scanText2.setText(need2);
//                String need3 = String.valueOf(gyroValues[0]) + '\n' + String.valueOf(gyroValues[1]) + '\n' + String.valueOf(gyroValues[2]) + '\n' ;  //展示Sx，Sy，ax，ay
//                scanText3.setText(need3);
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, rotVecValues);

            }
        });
    }



    private void initBluetooth(){
        if(!bluetoothAdapter.isEnabled()){
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }
    }
    private void startDiscovery(){
        registerReceiver(mReceiver,new IntentFilter((BluetoothDevice.ACTION_FOUND)));
        if(bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering())
            bluetoothAdapter.startDiscovery();
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    private Double LogarNormalDistribution(ArrayList<Double> m1list){
        ArrayList<Double> value = new ArrayList<>();
        if( m1list.size() > RSSI_LIMIT){             //截取长度合适RSSI字符串,长于15时截取前15个
            for (int i = 0; i <RSSI_LIMIT ; i++){
                value.add(m1list.get(i));
            }
        }else {
            for (int i = 0; i < m1list.size(); i++) { //截取长度合适RSSI字符串，短于15时全部复制
                value.add(m1list.get(i));
            }
        }

        Double avg, rssiValue = 0.0, staDev, proLowLim, proHighLim, pdfAltered ;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = GetAvg(logarNormalList);   //求均值
        staDev = GetStaDev(logarNormalList, avg, "logarNormal");  //求标准差

        if(staDev != 0) {
            proHighLim = Math.exp(0.5 * Math.pow(staDev,2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
//            String scanTextString = value.toString() + "\n" + proLowLim + "\n" + proHighLim;
//            scanText.setText(scanTextString);
//            Log.d("before", value.toString());
//            Log.d("staDev", staDev.toString());
//            Log.d("Low", proLowLim.toString());
//            Log.d("high",proHighLim.toString());
            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI
                Double exponent = - Math.pow(logarNormalList.get(i) - avg, 2) / (2 * Math.pow(staDev, 2));
                pdfAltered = Math.exp(exponent) / ((0 - value.get(i)) * staDev * Math.sqrt(2 * Math.PI));
//                Log.d("exponent", exponent.toString());
//                Log.d("pdf", pdfAltered.toString());
                if (pdfAltered < proLowLim || pdfAltered > proHighLim) {
                    logarNormalList.remove(i);                              //删除不在高概率区域内的数据
                    value.remove(i);
                    i -= 1;
                }
            }
        }

//        String scanTextString1= value.toString() + "\n";
//        scanText1.setText(scanTextString1);
//        Log.d("After", value.toString());

        if(value.size() != 0) {
            avg = GetAvg(logarNormalList);               //重新获取RSSI的平均值
            rssiValue = 0 - Math.exp(avg);
        }
        return rssiValue;
    }

    //用来给ArrayLIst产生均值的函数
    private Double GetAvg(ArrayList<Double> list){
        Double sum = 0.0, avg;
        for(int i=0; i< list.size(); i++){
            sum += list.get(i);
        }
        avg = sum / list.size();
        return avg;
    }

    //用来给ArrayList产生标准差的函数
    private Double GetStaDev(ArrayList<Double> list, Double avg, String distribution){
        Double stadardDev = 0.0;
        if (list.size() >1) {
            for (int i = 0; i < list.size(); i++) {
                stadardDev += Math.pow((list.get(i) - avg), 2);
            }
            if(distribution.equals("logarNormal"))
                stadardDev = Math.sqrt(stadardDev / list.size());
            else
                stadardDev = Math.sqrt(stadardDev / (list.size() - 1));
//            Log.d("staDev",stadardDev.toString());
        }
        return stadardDev;
    }

    //用来给ArrayList每个值取对数，以应用于对数正态运算的函数
    private ArrayList<Double> GetLogarNormalList(ArrayList<Double> list){
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++){
            list1.add(Math.log( 0 - list.get(i)));
        }
        return list1;
    }

    //用来给RSSI排序
    public ArrayList<String> Sort(Map<String, Double> m2, int BLE_CHOOSED_NUM){
        List<Map.Entry<String, Double>> infoIds =
                new ArrayList<>(m2.entrySet());
        ArrayList<String> list = new ArrayList<>();
        int limit = BLE_CHOOSED_NUM < m2.size() ? BLE_CHOOSED_NUM:m2.size();

        Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {        //排序
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return  o2.getValue().compareTo(o1.getValue());
            }
        });
        for (int i = 0; i < limit; i++) {        //排序完,取前limit个
            String id = infoIds.get(i).toString();
            list.add(id.split("=")[0]);   //string.split后变为字符串数组。
//            System.out.println(id);
        }
        return list;     //排序好的MAC地址的列表
    }

    //通过质心方式给出初始基本定位
    public Double[] MassCenterLocation (ArrayList<String> m3,Map<String,Double[]> bleDevLoc){
        Double[] location =  new Double[2];
        String A = m3.get(0),B = m3.get(1), C = m3.get(2);
        Double Ax = bleDevLoc.get(A)[0], Ay = bleDevLoc.get(A)[1], Bx = bleDevLoc.get(B)[0], By = bleDevLoc.get(B)[1], Cx = bleDevLoc.get(C)[0], Cy = bleDevLoc.get(C)[1];
        location[0] = 1.0/3 * ((Ax) + 0.5 * (Ax+Bx) + 0.5 * (Bx+Cx));
        location[1] = 1.0/3 * ((Ay) + 0.5 * (Ay+By) + 0.5 * (By+Cy));
        Log.d("m3",m3.toString());
        Log.d("location", Arrays.toString(location));
        return location;
    }






    @Override
    protected void onDestroy() {
        super.onDestroy();

//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//
//                FileCache.saveFile(stringBuffer + "\n");  //位置输出到文件中。
//            }
//        });
//        thread.start();
    }
    @Override
    protected void onResume() {
        super.onResume();
        Sensor rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,accSensor,SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,gyroSensor,SensorManager.SENSOR_DELAY_GAME);
    }
    @Override
    protected void onPause() {
        sensorManager.unregisterListener(listener);
        super.onPause();
    }
}


