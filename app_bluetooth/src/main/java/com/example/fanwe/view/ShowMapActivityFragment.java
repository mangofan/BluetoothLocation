package com.example.fanwe.view;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import com.eftimoff.androipathview.PathView;
import com.example.fanwe.bluetoothlocation.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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

import utils.FileCache;

import static com.example.fanwe.bluetoothlocation.R.id.textView2;
import static com.example.fanwe.bluetoothlocation.R.id.webview;

/**
 * A placeholder fragment containing a simple view.
 */
public class ShowMapActivityFragment extends Fragment {

    PathView mPathView;
    WebView webView;
    TextView mtext;

    private static final int ENABLE_BLUETOOTH = 1;
    int RSSI_LIMIT = 8, BLE_CHOOSED_NUM = 3;

    //蓝牙有关的参数
    Double[] location = new Double[2];
    Map<String,List<Double>> mAllRssi = new HashMap<>();  //储存RSSI的MAP
    Map<String,List<Double>> mTest = new HashMap<>();  //储存键为MAC地址，值为过滤后的RSSI的MAP
    Map<String,Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    List<String> listSortedNode = new ArrayList<>();
    StringBuffer stringBuffer = new StringBuffer();
    Map<String,Double[]> bleNodeLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //传感器有关的参数
    Integer TIME0 = 200;
    float[] rotVecValues = {0,0,0,0}, accValues = {0,0,0}, gyroValues = {0,0,0};



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root =inflater.inflate(R.layout.fragment_show_map,null);
        webView= (WebView) root.findViewById(webview);
        mtext= (TextView) root.findViewById(textView2);
        initWebview();
        initlocation();
        initBluetooth();
        initSynchronize();
        initSensor();
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
        EventBus.getDefault().register(this);
        return root;
    }


    //UI 更新方法
    private void updateGUI(){
        getActivity().runOnUiThread(new Runnable() {
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

    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

        @Override
        public void onReceive(Context context, Intent intent) {

                BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String remoteMAC;
                final Short rssi;
                if (remoteDevice != null) {
                    remoteMAC = remoteDevice.getAddress();
                    if (bleNodeLoc.containsKey(remoteMAC)) {
                        rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                        if (!dFinished.equals(intent.getAction())) {
                            if (mAllRssi.containsKey(remoteMAC)) {
                                List<Double> list1 = mAllRssi.get(remoteMAC);
                                list1.add(0, (double) rssi);
                                mAllRssi.put(remoteMAC, list1);
                            } else {
                                ArrayList<Double> list = new ArrayList<>();
                                list.add((double) rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
                                mAllRssi.put(remoteMAC, list);
                            }
                            final List<Double> rssiValueFilterd = LogarNormalDistribution(mAllRssi.get(remoteMAC));  //获取滤波后的信号强度表
                            mTest.put(remoteMAC, rssiValueFilterd);
                            mRssiFilterd.put(remoteMAC, getAvg(rssiValueFilterd));   //更新MAC地址对应信号强度的map
                            if (mRssiFilterd.size() > 2) {
                                listSortedNode = getSort(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
                                location = getNearestNode(listSortedNode, bleNodeLoc);   //定位为最近的节点的位置。
    //                            location = getMassCenterLocation(listSortedNode, bleNodeLoc);   //通过质心定位得到位置
    //                            String need = location[0].toString() + "," +location[1].toString();
                                String need = listSortedNode.get(0).split(":")[5];
                                mtext.setText(need);
                                webView.loadUrl(setInsetJS(location[0] + "", location[1] + ""));

                                Thread thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Calendar now = Calendar.getInstance();
                                            Integer minute = now.get(Calendar.MINUTE);
                                            Integer second = now.get(Calendar.SECOND);
                                            Map<String, List<Double>> mapForStore = getMapForStore(listSortedNode, mAllRssi);
                                            Map<String, List<Double>> mapForStore1 = getMapForStore(listSortedNode, mTest);
                                            String need1 = "{" + location[0].toString() + "   " + location[1].toString() + "     "
                                                    + minute.toString() + ":" + second.toString() + "\n"
                                                    + mapForStore + "\n"
                                                    + mapForStore1 + "\n" + "}";
                                            FileCache.saveFile(need1);
                                        }catch(Exception e){
                                            e.printStackTrace();

                                        }
                                    }
                                });

                                thread.start();
                        }

                    }
                }
            }
        }
    };

    //对数正态滤波
    private List<Double> LogarNormalDistribution(List<Double> mAllRssilist){
        ArrayList<Double> value = new ArrayList<>();
        if( mAllRssilist.size() > RSSI_LIMIT){             //截取长度合适RSSI字符串,长于15时截取前15个
            for (int i = 0; i <RSSI_LIMIT ; i++){
                value.add(mAllRssilist.get(i));
            }
        }else {
            for (int i = 0; i < mAllRssilist.size(); i++) { //截取长度合适RSSI字符串，短于15时全部复制
                value.add(mAllRssilist.get(i));
            }
        }

        Double avg, staDev, proLowLim, proHighLim, pdfAltered ;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = getAvg(logarNormalList);   //求均值
        staDev = getStaDev(logarNormalList, avg, "logarNormal");  //求标准差

        if(staDev != 0) {
            proHighLim = Math.exp(0.5 * Math.pow(staDev,2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
//            String rssiTextString = value.toString() + "\n" + proLowLim + "\n" + proHighLim;
            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI
                Double exponent = - Math.pow(logarNormalList.get(i) - avg, 2) / (2 * Math.pow(staDev, 2));
                pdfAltered = Math.exp(exponent) / ((0 - value.get(i)) * staDev * Math.sqrt(2 * Math.PI));
                if (pdfAltered < proLowLim || pdfAltered > proHighLim) {
                    logarNormalList.remove(i);                              //删除不在高概率区域内的数据
                    value.remove(i);            //未进行对数运算的原始数据中也进行对应的删除操作
                    i -= 1;
                }
            }
        }

//        if(value.size() != 0) {
//            avg = getAvg(value);               //重新获取RSSI的平均值
//        }

        return value;
    }

    //求ArrayLIst均值
    private Double getAvg(List<Double> list){
        Double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += list.get(i);
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //求ArrayList标准差
    private Double getStaDev(ArrayList<Double> list, Double avg, String distribution){
        Double stadardDev = 0.0;
        if (list.size() > 1) {
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

    //对ArrayList每个值取对数，以应用于对数正态运算的函数
    private ArrayList<Double> GetLogarNormalList(ArrayList<Double> list){
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++){
            list1.add(Math.log( 0 - list.get(i)));
        }
        return list1;
    }

    //根据RSSI强度，对MAC地址排序
    public ArrayList<String> getSort(Map<String, Double> mRssiFilterd, int BLE_CHOOSED_NUM){
        List<Map.Entry<String, Double>> infoIds =
                new ArrayList<>(mRssiFilterd.entrySet());
        ArrayList<String> list = new ArrayList<>();
        int limit = BLE_CHOOSED_NUM < mRssiFilterd.size() ? BLE_CHOOSED_NUM:mRssiFilterd.size();

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

    //根据RSSI强度，得到最近的蓝牙节点
    public Double[] getNearestNode (List<String> listSortedNode,Map<String,Double[]> bleNodeLoc){
        Double[] location =  new Double[2];
        String A = listSortedNode.get(0);
        location[0] = bleNodeLoc.get(A)[0];
        location[1] = bleNodeLoc.get(A)[1];
        return location;
    }

    //使用质心定位得到坐标
    public Double[] getMassCenterLocation (ArrayList<String> listSortedNode,Map<String,Double[]> bleNodeLoc){
        Double[] location =  new Double[2];
        String A = listSortedNode.get(0),B = listSortedNode.get(1), C = listSortedNode.get(2);
        Double Ax = bleNodeLoc.get(A)[0], Ay = bleNodeLoc.get(A)[1], Bx = bleNodeLoc.get(B)[0], By = bleNodeLoc.get(B)[1], Cx = bleNodeLoc.get(C)[0], Cy = bleNodeLoc.get(C)[1];
        location[0] = 1.0/3 * ((Ax) + 0.5 * (Ax+Bx) + 0.5 * (Bx+Cx));
        location[1] = 1.0/3 * ((Ay) + 0.5 * (Ay+By) + 0.5 * (By+Cy));
        Log.d("listSortedNode",listSortedNode.toString());
        Log.d("location", Arrays.toString(location));
        return location;
    }

    //从MAP中选出 list中元素作为键，对应的键值对
    public synchronized  Map<String, List<Double>> getMapForStore (List<String> listSortedNode, Map<String,List<Double>> map){
        Map<String,List<Double>> mapReturn = new HashMap<>();
        for (int i = 0 ; i < listSortedNode.size(); i++){
            String mac = listSortedNode.get(i);
            List<Double> listRssi = map.get(mac);
            mapReturn.put(mac,listRssi);
        }
        return mapReturn;
    }

    //初始化需要加同步锁的变量
    private void initSynchronize() {
        mAllRssi=Collections.synchronizedMap(mAllRssi);
        mRssiFilterd=Collections.synchronizedMap(mRssiFilterd);
        listSortedNode=Collections.synchronizedList(listSortedNode);
        mTest=Collections.synchronizedMap(mTest);
    }

    //和sensor有关的初始化
    private void initSensor(){
        SensorManager sensorManager = (SensorManager)getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,accSensor,SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,gyroSensor,SensorManager.SENSOR_DELAY_GAME);
    }
































    //初始化已知蓝牙节点信息
    void initlocation() {
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

        bleNodeLoc.put("19:18:FC:01:F1:0E",location21);
        bleNodeLoc.put("19:18:FC:01:F1:0F",location22);
        bleNodeLoc.put("19:18:FC:01:F0:F8",location23);
        bleNodeLoc.put("19:18:FC:01:F0:F9",location24);
        bleNodeLoc.put("19:18:FC:01:F0:FA",location25);
        bleNodeLoc.put("19:18:FC:01:F0:FB",location26);
        bleNodeLoc.put("19:18:FC:01:F0:FC",location27);
        bleNodeLoc.put("19:18:FC:01:F0:FD",location28);
        bleNodeLoc.put("19:18:FC:01:F0:FE",location29);
        bleNodeLoc.put("19:18:FC:01:F0:FF",location30);
    }

    @Override
    public void onPause() {
//        sensorManager.unregisterListener(listener);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                FileCache.saveFile(stringBuffer + "\n");  //位置输出到文件中。
            }
        });
        thread.start();

        EventBus.getDefault().unregister(this);

    }

    //提示用户开启手机蓝牙
    private void initBluetooth(){
        if(!bluetoothAdapter.isEnabled()){
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }
    }

//    @Subscribe(threadMode= ThreadMode.MAIN)
//    public void startDiscovery(String  startloaction){
//        getActivity().registerReceiver(mReceiver,new IntentFilter((BluetoothDevice.ACTION_FOUND)));
//        if(bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering())
//            bluetoothAdapter.startDiscovery();
//        getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
//    }
//    @Subscribe(threadMode= ThreadMode.MAIN)
//    public void stopDiscovery(Stop  stop){
//        getActivity().unregisterReceiver(mReceiver);
//
//    }

    public ShowMapActivityFragment() {

    }

    private String setInsetJS(String rx,String ry) {
        return "javascript:{" +
                "\t$(\"#circle_point\").attr(\"cx\",\""+rx+"\");\n" +
                "\t$(\"#circle_point\").attr(\"cy\",\""+ry+"\");\n" +
                "\n" +
                "" +
                "}";
    }
    private void initWebview() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        //设置字符编码
        settings.setDefaultTextEncodingName("utf-8");
        // 支持缩放
        settings.setSupportZoom(true);
        // //启用内置缩放装置
        settings.setBuiltInZoomControls(true);
        // 支持自动加载图片
        settings.setLoadsImagesAutomatically(true);
        // 支持内容重新布局
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
//        webView.addJavascriptInterface(new CommodityData(getApplicationContext(), serializableMap, "ProductManageDetail"), "ProductManage");
        // webView.loadUrl("file:///android_asset/svg/10072_1.svg");
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.loadUrl("file:///android_asset/svg/10072_1.html");
    }
}

