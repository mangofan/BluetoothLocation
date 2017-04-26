package com.example.fanwe.view;

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
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

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
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import utils.FileCache;

import static com.example.fanwe.bluetoothlocation.R.id.textView1;
import static com.example.fanwe.bluetoothlocation.R.id.textView2;
import static com.example.fanwe.bluetoothlocation.R.id.webview;

/**
 * A placeholder fragment containing a simple view.
 */
public class ShowMapActivityFragment extends Fragment implements Cloneable {

    private static final int ENABLE_BLUETOOTH = 1;
//    PathView mPathView;
    WebView webView;
    TextView mTextAcc;
    TextView mTextGyro;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 3;
//    float PERCENTILE_LIMIT = 0.3f;

    //蓝牙有关的参数
//    String locationFilterdinMac = "19:18:FC:01:F1:0E";

    Map<String, List<Double>> mAllRssi = new HashMap<>();  //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    StringBuffer stringBuffer = new StringBuffer();
    Map<String, Double[]> bleNodeLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    ArrayList<String> locationList = new ArrayList<>();
    Long timeLast;
    String locationLast;




    //传感器有关的参数
    int TIME0 = 200, LOCATION_NUM_LIMIT = 20, conutForInitialize = 0;
    long[] timeArray = new long[2];
    Double Sx = 0.0, Sy = 0.0, V0x = 0.0, V0y = 0.0, ax = 0.0, ay = 0.0;
    float[] rotVecValues = {0, 0, 0, 0}, accValues = {0, 0, 0}, gyroValues = {0, 0, 0};
    SparseArray<ArrayList<Double>> accValueList = new SparseArray<>();   //维持一定时间内的加速计历次读数的列表
    LongSparseArray<Double[]> locationBasedOnSensor = new LongSparseArray<>();
    Double[] accBias = {0.0,0.0,0.0}, accStaDev =  {0.0,0.0,0.0};
    ArrayList<Long> listOfTime = new ArrayList<>();

    //传感器事件监听器
    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ROTATION_VECTOR:
                    rotVecValues = event.values.clone();
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    gyroValues = event.values.clone();
                    gyroValues = filterGyroValue(gyroValues);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    accValues = event.values.clone();
                    tranformAndStoreAccValue();
                    break;
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_show_map, null);
        webView = (WebView) root.findViewById(webview);
        mTextAcc = (TextView) root.findViewById(textView1);
        mTextGyro = (TextView) root.findViewById(textView2);
        initWebview();
        initlocation();
        initBluetooth();
        initSynchronize();
        initSensor();
        initAccFilterSparseArray();   //初始化用来进行加速计过滤的sparsearray
        //设置每隔TIME0更新UI
        Timer updateTimer = new Timer("Update");
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateGUI();
            }
        }, 0, TIME0);
        EventBus.getDefault().register(this);
        return root;
    }

    //UI 更新方法
    private void updateGUI() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    //将加速度矢量转化到地理坐标系，计算行进路程，并且储存变化后的坐标。
    public void tranformAndStoreAccValue(){
        float[] pQuaternion = new float[4];
        SensorManager.getQuaternionFromVector(pQuaternion, rotVecValues);  //由旋转矢量获得四元数
        Double[] accConverted = getConvertAcc(getAccCompleted(accValues), pQuaternion);  //将加速度矢量转换到地理坐标系
        accConverted = filterAccValues(accConverted);
        ax = accConverted[0];
        ay = accConverted[1];

        Calendar calendar = Calendar.getInstance();
        Long currentMillisecond = calendar.getTimeInMillis();
        long timeDiff = currentMillisecond - listOfTime.get(0);

        if(ax.equals(0.0) && ay.equals(0.0)){
            V0x = 0.0;
            V0y = 0.0;
        }else{
            V0x = V0x + ax * timeDiff;
            V0y = V0y + ay * timeDiff;
        }

        Sx += V0x * timeDiff + 0.5 * ax * timeDiff * timeDiff;   //计算时时间单位应该用秒
        Sy += V0y * timeDiff + 0.5 * ay * timeDiff * timeDiff;
        Double[] location = {Sx,Sy};
        locationBasedOnSensor.put(currentMillisecond, location);

        //以时间差不能超过6秒钟的条件维持map大小。
        listOfTime.add(0,currentMillisecond);
        long oldestTime = listOfTime.get(listOfTime.size()-1);
        if((currentMillisecond - oldestTime) > 6000){
            listOfTime.remove(oldestTime);
            locationBasedOnSensor.delete(oldestTime);
        }
    }

    //根据四元数转换方式，将加速度矢量转换到地理坐标系
    public Double[] getConvertAcc(float[] p_1, float[] q) {
        float[] q_1p_1 = getQuaternionMulit(getQuaternionInverse(q), p_1);
        float[] p = getQuaternionMulit(q_1p_1, q);
        Double[] pDouble = new Double[3];
        pDouble[0] = Double.valueOf(String.valueOf(p[1]));
        pDouble[1] = Double.valueOf(String.valueOf(p[2]));
        pDouble[2] = Double.valueOf(String.valueOf(p[3]));
        return pDouble;
    }
    //过滤陀螺仪数据
    public float[] filterGyroValue(float[] gyroValues) {
        for (int i = 0; i < gyroValues.length; i++) {
            gyroValues[i] = (Math.abs(gyroValues[i]) > 0.1) ? gyroValues[i] : 0;  //如果陀螺仪的值小于0.01则认为直接为0
        }
        return gyroValues;
    }


    public Double[] filterAccValues(Double[] accValues) {
        for(int i = 0; i< accValues.length; i++) {
            //将得到的加速度值存储起来
            ArrayList<Double> valueList = accValueList.get(i);   //valueList 加速计一个轴的值的连续计数
            valueList.add(0, accValues[i]);
            if (valueList.size() > 10) {
                valueList.remove(10);   //维持长度小于10
            }
            accValues[i] = filterByBiasedNormalDistribution(valueList, i);
        }
        return accValues;
    }
    //主要用来获取加速计读数的零漂偏差值，在读数中去除
    public double filterByBiasedNormalDistribution(ArrayList<Double> valueList, int i){
        Double avg = getAvg(valueList);
        Double staDev = getStaDev(valueList, avg, "normal");
        if (staDev < 0.3) {           //加速计读数比较稳定时，一般来说应该处于比较静止的状态,此时更新偏差值;读数在发生变化时，偏差值应该与静止时差不多，所以偏差值不做处理
            accBias[i] = avg;
            return 0.0;
        }
        accStaDev[i] = staDev;
        return  valueList.get(0) - accBias[i];    //测量值减去偏差值即为真实值
    }


    //切割子list
    private ArrayList cutList(List list, int limit) {
        int trueLimit = limit < list.size() ? limit : list.size();
        ArrayList returnList = new ArrayList();
        for (int i = 0; i < trueLimit; i++) {
            returnList.add(list.get(i));
        }
        return returnList;
    }

    //加速度矢量补全为四元数
    public float[] getAccCompleted(float[] acc) {
        float[] accCompleted = new float[4];
        accCompleted[0] = 0.0f;
        accCompleted[1] = acc[0];
        accCompleted[2] = acc[1];
        accCompleted[3] = acc[2];
        return accCompleted;
    }
    //四元数乘法
    public float[] getQuaternionMulit(float[] Q1, float[] Q2) {
        float[] Q3 = new float[4];
        Q3[0] = Q1[0] * Q2[0] - Q1[1] * Q2[1] - Q1[2] * Q2[2] - Q1[3] * Q2[3];
        Q3[1] = Q1[0] * Q2[1] + Q1[1] * Q2[0] + Q1[2] * Q2[3] - Q1[3] * Q2[2];
        Q3[2] = Q1[0] * Q2[2] + Q1[2] * Q2[0] + Q1[3] * Q2[1] - Q1[1] * Q2[3];
        Q3[3] = Q1[0] * Q2[3] + Q1[3] * Q2[0] + Q1[1] * Q2[2] - Q1[2] * Q2[1];
        return Q3;
    }
    //四元数取逆
    public float[] getQuaternionInverse(float[] q) {
        float[] q_1 = new float[4];
        q_1[0] = q[0];
        q_1[1] = -q[1];
        q_1[2] = -q[2];
        q_1[3] = -q[3];
        return q_1;
    }


    //蓝牙广播监听器
    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;


        @Override
        public void onReceive(Context context, Intent intent) {

            Calendar calendar = Calendar.getInstance();
            Long currentMillisecondBluetooth = calendar.getTimeInMillis();
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
                        List<Double> rssiValueFilterd = LogarNormalDistribution(mAllRssi.get(remoteMAC));  //获取滤波后的信号强度表
                        mRssiFilterd.put(remoteMAC, getAvg(rssiValueFilterd));   //更新MAC地址对应信号强度的map
                        if (mRssiFilterd.size() > 2) {
                            List<String> listSortedNode = sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
                            String locationNearest = listSortedNode.get(0);
                            String locationBasedOnMajority = filterLocation();    //由多数法选出可能的点

                            locationList.add(0,listSortedNode.get(0));
//                            Double[] locationNearest = getNearestNode(listSortedNode, bleNodeLoc);   //定位为最近的节点的位置。
//                            location = getMassCenterLocation(listSortedNode, bleNodeLoc);   //通过质心定位得到位置
                            if(conutForInitialize == 1) {

                                if (!locationBasedOnMajority.equals(locationLast)) {    //当新出现的节点与上一个定位点不相同时
                                    String locationTrue = getSensorConfirm(locationLast, locationBasedOnMajority, timeLast,currentMillisecondBluetooth);
                                    timeLast = currentMillisecondBluetooth;
                                    locationLast = locationTrue;
                                }
                            }else{
                                locationLast = locationList.get(0);
                                timeLast = currentMillisecondBluetooth;
                                conutForInitialize = 1;
                            }
                            webView.loadUrl(setInsetJS(bleNodeLoc.get(locationLast)[0] + "", bleNodeLoc.get(locationLast)[1] + "","circle_point"));
                            webView.loadUrl(setInsetJS(bleNodeLoc.get(locationBasedOnMajority)[0] + "", bleNodeLoc.get(locationBasedOnMajority)[1] + "","circle_point2"));
                        }
                    }
                }
            }
        }
    };

    //当获得角度大于零时，传入的新坐标点是对的；小于零也包括传感器提示没有行动时，返回传入的旧坐标点
    private String getSensorConfirm(String locationLast, String locationOnMajority, long timeLast,long currentMillisecondBluetooth){
        Double[] locationOld = bleNodeLoc.get(locationLast);
        Double[] locationNew = bleNodeLoc.get(locationOnMajority);
        Double[] vectorBasedOnBluetooth = {locationNew[0]-locationOld[0], locationNew[1]-locationOld[1]};

        Double[] locationOldSensor = searchTimeList(timeLast);
        Double[] locationNewSensor = searchTimeList(currentMillisecondBluetooth);
        Double[] vectorBasedOnSensor = {locationNewSensor[0]-locationOldSensor[0],locationNewSensor[1]-locationOldSensor[1]};

        double angle = getVectorAngle(vectorBasedOnBluetooth,vectorBasedOnSensor);
        if(angle > 0){
            return locationOnMajority;
        }else {
            return locationLast;
        }
    }

    //寻找列表中与传入时间最接近的时间，返回这个时间对应的坐标。
    public Double[] searchTimeList(long time){
        long theDiff = 1000000;
        int j = 0;
        for(int i = 0; i<listOfTime.size(); i++){
            long diff = Math.abs(listOfTime.get(i) - time);
            if(diff <= theDiff){
                theDiff = diff;
                j = i;
            }else{
                j = i - 1;
                break;
            }
        }
        long timeQuery = listOfTime.get(j);
        return locationBasedOnSensor.get(timeQuery);
    }

    //求向量夹角的cos
    public double getVectorAngle(Double[] v1, Double[] v2){
        double up = v1[0]*v2[0] + v1[1]*v2[1];
        double down1 = Math.sqrt(v1[0]*v1[0] + v1[1]*v1[1]);
        double down2 = Math.sqrt(v2[0]*v2[0] + v2[1]*v2[1]);
        double angle;
        try {
            angle = up / (down1*down2);
        }catch (ArithmeticException e){
            angle = -2.0;   //当某个向量为0向量时，特殊处理
        }
        return angle;
    }

    //在locationList中选择多数作为当前的位置。
    public String filterLocation(){
        String toReturn;
        if (locationList.size() > LOCATION_NUM_LIMIT) {
            Map<String, Integer> locationCountMap = new HashMap<>();
            locationCountMap.put(locationList.get(0), 1);
            for (int i = 1; i < locationList.size(); i++) {
                String location = locationList.get(i);
                if(locationCountMap.containsKey(location)){
                    locationCountMap.put(location, locationCountMap.get(location) + 1);
                }else {
                    locationCountMap.put(location,1);
                }
            }
            toReturn = sortLocationBasedOnCount(locationCountMap);
            locationList.remove(locationList.size() - 1);
        }else{
            toReturn = locationList.get(0);   //在列表长度小于限制时，以列表头元素作为频率最高的值，
        }
        return  toReturn;
    }

    //返回map中对应最大值的键
    public String sortLocationBasedOnCount(Map<String,Integer> locationCountMap){
        List<Map.Entry<String, Integer>> infoIds =
                new ArrayList<>(locationCountMap.entrySet());
        Collections.sort(infoIds, new Comparator<Map.Entry<String, Integer>>() {        //排序
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        return infoIds.get(0).getKey();
    }



    public ArrayList<String> changeLocationListToCoordinate(ArrayList<String> locationList){
        ArrayList<String> toReturn = new ArrayList<>();
        for (int i = 0; i < locationList.size(); i++){
            Double[] test = bleNodeLoc.get(locationList.get(i));
            toReturn.add(test[0].toString());
            toReturn.add(test[1].toString());
            toReturn.add(" ");
        }
        return toReturn;
    }







    //对数正态滤波
    private List<Double> LogarNormalDistribution(List<Double> mAllRssilist) {
        ArrayList<Double> value = cutList(mAllRssilist, RSSI_LIMIT);

        Double avg, staDev, proLowLim, proHighLim, pdfAltered;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = getAvg(logarNormalList);   //求均值
        staDev = getStaDev(logarNormalList, avg, "logarNormal");  //求标准差

        if (staDev != 0) {
            proHighLim = Math.exp(0.5 * Math.pow(staDev, 2) - avg) / (staDev * Math.sqrt(2 * Math.PI));
            proLowLim = proHighLim * 0.6;
            for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI
                Double exponent = -Math.pow(logarNormalList.get(i) - avg, 2) / (2 * Math.pow(staDev, 2));
                pdfAltered = Math.exp(exponent) / ((0 - value.get(i)) * staDev * Math.sqrt(2 * Math.PI));
                if (pdfAltered < proLowLim || pdfAltered > proHighLim) {
                    logarNormalList.remove(i);                              //删除不在高概率区域内的数据
                    value.remove(i);            //未进行对数运算的原始数据中也进行对应的删除操作
                    i -= 1;
                }
            }
        }

        return value;
    }

    //根据RSSI强度，得到最近的蓝牙节点
    public Double[] getNearestNode(List<String> listSortedNode, Map<String, Double[]> bleNodeLoc) {
        Double[] location = new Double[2];
        String A = listSortedNode.get(0);
        location[0] = bleNodeLoc.get(A)[0];
        location[1] = bleNodeLoc.get(A)[1];
        return location;
    }

    //使用质心定位得到坐标
    public Double[] getMassCenterLocation(ArrayList<String> listSortedNode, Map<String, Double[]> bleNodeLoc) {
        Double[] location = new Double[2];
        String A = listSortedNode.get(0), B = listSortedNode.get(1), C = listSortedNode.get(2);
        Double Ax = bleNodeLoc.get(A)[0], Ay = bleNodeLoc.get(A)[1], Bx = bleNodeLoc.get(B)[0], By = bleNodeLoc.get(B)[1], Cx = bleNodeLoc.get(C)[0], Cy = bleNodeLoc.get(C)[1];
        location[0] = 1.0 / 3 * ((Ax) + 0.5 * (Ax + Bx) + 0.5 * (Bx + Cx));
        location[1] = 1.0 / 3 * ((Ay) + 0.5 * (Ay + By) + 0.5 * (By + Cy));
        Log.d("listSortedNode", listSortedNode.toString());
        Log.d("location", Arrays.toString(location));
        return location;
    }

    //求ArrayLIst均值
    private Double getAvg(List list) {
        Double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Double.valueOf(list.get(i).toString());
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //求ArrayList标准差
    private Double getStaDev(ArrayList list, Double avg, String distribution) {
        Double stadardDev = 0.0;
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                stadardDev += Math.pow((Double.valueOf(list.get(i).toString()) - avg), 2);
            }
            if (distribution.equals("logarNormal"))
                stadardDev = Math.sqrt(stadardDev / list.size());
            else
                stadardDev = Math.sqrt(stadardDev / (list.size() - 1));
//            Log.d("staDev",stadardDev.toString());
        }
        return stadardDev;
    }

    //对ArrayList每个值取对数，以应用于对数正态运算的函数
    private ArrayList<Double> GetLogarNormalList(ArrayList<Double> list) {
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list1.add(Math.log(0 - list.get(i)));
        }
        return list1;
    }

    //根据RSSI强度，对MAC地址排序
    public ArrayList<String> sortNodeBasedOnRssi(Map<String, Double> mRssiFilterd, int BLE_CHOOSED_NUM) {
        List<Map.Entry<String, Double>> infoIds =
                new ArrayList<>(mRssiFilterd.entrySet());
        ArrayList<String> list = new ArrayList<>();
        int limit = BLE_CHOOSED_NUM < mRssiFilterd.size() ? BLE_CHOOSED_NUM : mRssiFilterd.size();

        Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {        //排序
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        for (int i = 0; i < limit; i++) {        //排序完,取前limit个
            String id = infoIds.get(i).toString();
            list.add(id.split("=")[0]);   //string.split后变为字符串数组。
        }
        return list;     //排序好的MAC地址的列表
    }

    //从MAP中选出 list中元素作为键，对应的键值对
    public synchronized Map<String, List<Double>> getMapForStore(List<String> listSortedNode, Map<String, List<Double>> map) {
        Map<String, List<Double>> mapReturn = new HashMap<>();
        for (int i = 0; i < listSortedNode.size(); i++) {
            String mac = listSortedNode.get(i);
            List<Double> listRssi = map.get(mac);
            mapReturn.put(mac, listRssi);
        }
        return mapReturn;
    }

    //提示用户开启手机蓝牙
    private void initBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }
    }
    //初始化需要加同步锁的变量
    private void initSynchronize() {
        mAllRssi = Collections.synchronizedMap(mAllRssi);
        mRssiFilterd = Collections.synchronizedMap(mRssiFilterd);
    }
    //和sensor有关的初始化
    private void initSensor() {
        SensorManager sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener, accSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        listOfTime.add(Calendar.getInstance().getTimeInMillis());    //初始化时给时间列表填充第一个元素
    }
    //对加速计过滤时用到的sparsearray的初始化
    private void initAccFilterSparseArray() {
        for(int i = 0; i < 6; i++){
            ArrayList<Double> list = new ArrayList<>();
            list.add(0.0);
            accValueList.put(i, list);
        }
    }
    //初始化已知蓝牙节点信息
    void initlocation() {
        Double[] location21 = {11.5, 0.7};
        Double[] location22 = {15.8, 0.7};
        Double[] location23 = {7.8, 4.7};
        Double[] location24 = {11.8, 4.7};
        Double[] location25 = {15.8, 4.7};
        Double[] location26 = {19.8, 4.7};
        Double[] location27 = {7.8, 8.7};
        Double[] location28 = {11.8, 8.7};
        Double[] location29 = {15.8, 8.7};
        Double[] location30 = {19.8, 8.7};

        bleNodeLoc.put("19:18:FC:01:F1:0E", location21);
        bleNodeLoc.put("19:18:FC:01:F1:0F", location22);
        bleNodeLoc.put("19:18:FC:01:F0:F8", location23);
        bleNodeLoc.put("19:18:FC:01:F0:F9", location24);
        bleNodeLoc.put("19:18:FC:01:F0:FA", location25);
        bleNodeLoc.put("19:18:FC:01:F0:FB", location26);
        bleNodeLoc.put("19:18:FC:01:F0:FC", location27);
        bleNodeLoc.put("19:18:FC:01:F0:FD", location28);
        bleNodeLoc.put("19:18:FC:01:F0:FE", location29);
        bleNodeLoc.put("19:18:FC:01:F0:FF", location30);
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

        cancelTask();

    }

    ScheduledFuture<?> future;
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void startDiscovery(String controlStartDiscovery) {
        getActivity().registerReceiver(mReceiver, new IntentFilter((BluetoothDevice.ACTION_FOUND)));
       if (bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.startDiscovery();
        }
        getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        cancelTask();
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
       future = executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.startDiscovery();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void cancelTask(){
       if(future!=null) {
           future.cancel(false);
           future = null;
       }

   }

    private String setInsetJS(String rx, String ry,String cir_id) {
        return "javascript:{" +
                "\t$(\"#"+cir_id+"\").attr(\"cx\",\"" + rx + "\");\n" +
                "\t$(\"#"+cir_id+"\").attr(\"cy\",\"" + ry + "\");\n" +
                "\n" +
                "" +
                "}";
    }


    public ShowMapActivityFragment() {

    }


}

//加速计去除零漂,认为匀加速运动比较少，将匀加速和静止不动合并起来，都去掉
//    public float[] filterAccValues(float[] accValues) {
//        float[] accValueForReturn = new float[3];
//        for (int i = 0; i < accValues.length; i++) {
//            //将得到的加速度值存储起来
//            ArrayList<Float> valueList = accValueList.get(i);
//            valueList.add(0, accValues[i]);
//            if (valueList.size() > 15) {
//                valueList.remove(10);   //维持长度小于10
//            }
//            ArrayList<Float> percentileList = accValueList.get(i + 3);
//            valueList = cutList(valueList, ACC_LIMIT);
////            float percentile = Math.abs((valueList.get(1) - valueList.get(0)) / valueList.get(0));
//            float percentile = Math.abs(valueList.get(1) - valueList.get(0));
//            if (percentile > PERCENTILE_LIMIT) {   //当变化比例超过PERCENTILE_LIMIT时，认为是真实的变化。
//                percentileList.add(0, percentile);
//                accValueForReturn[i] = accValues[i];
//            }
//            else {
//                percentileList.add(0, 0.0f);
//                accValueForReturn[i] = 0.0f;
//            }
//        }
//        return accValueForReturn;
//    }

//                            String locationFilterdinMacTemp = filterLocation();
//                            if(!locationFilterdinMacTemp.equals(locationFilterdinMac)) {
//                                final Double[] locationFilterdInCoordinate = bleNodeLoc.get(locationFilterdinMac);
//                                webView.loadUrl(setInsetJS(locationFilterdInCoordinate[0] + "", locationFilterdInCoordinate[1] + ""));
//                                locationFilterdinMac = locationFilterdinMacTemp;
//                                final ArrayList testList = (ArrayList) locationList.clone();
//                                try {
//                                    Calendar now = Calendar.getInstance();
//                                    Long timeInMillis = now.getTimeInMillis();
//                                    String need1 = (timeInMillis - anchorTime) + "  " + locationFilterdInCoordinate[0] + "  " + locationFilterdInCoordinate[1] + "\n" + changeLocationListToCoordinate(testList) + "\n\n";
//                                    FileCache.saveFile(need1);
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }


//                            Thread thread = new Thread(
//                                    new Runnable() {
//                                @Override
//                                public void run() {
//                                    try {
//                                        Calendar now = Calendar.getInstance();
//                                        Integer minute = now.get(Calendar.MINUTE);
//                                        Integer second = now.get(Calendar.SECOND);
//                                        Map<String, List<Double>> mapForStore = getMapForStore(listSortedNode, mAllRssi);
//                                        Map<String, List<Double>> mapForStore1 = getMapForStore(listSortedNode, mTest);
//                                        String need1 = "{" + location[0].toString() + "   " + location[1].toString() + "     "
//                                                + minute.toString() + ":" + second.toString() + "\n"
//                                                + mapForStore + "\n"
//                                                + mapForStore1 + "\n" + "}";
//                                        FileCache.saveFile(need1);
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//
//                                    }
//                                }
//                            });
//                            thread.start();
//                            }



