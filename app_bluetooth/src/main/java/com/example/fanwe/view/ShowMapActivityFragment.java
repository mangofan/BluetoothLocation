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
import android.nfc.Tag;
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

import java.sql.SQLNonTransientException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import utils.FileCache;
import utils.MyUtils;

import static com.example.fanwe.bluetoothlocation.R.id.textView1;
import static com.example.fanwe.bluetoothlocation.R.id.textView2;
import static com.example.fanwe.bluetoothlocation.R.id.time;
import static com.example.fanwe.bluetoothlocation.R.id.webview;
// TODO: 2017/5/9 增加上慢速移动坐标点的代码
// TODO: 2017/5/11 发热量大，减少计算量
public class ShowMapActivityFragment extends Fragment implements Cloneable {

    float angleBias = 19f;  //建筑物方向与正北向的夹角，建筑物方向是正北向的的北偏东多少度。
    private static final int ENABLE_BLUETOOTH = 1;
    WebView webView;
    TextView mTextAcc, mTextGyro;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4;
    SensorManager sensorManager;

    //蓝牙有关的参数
    long timeLast;
    long lastTime = 0;
    double staDevLimit = 0.5;
    double rssiThreshold = -55;
    double[] locationLast = new double[2];
    StringBuffer stringBuffer = new StringBuffer();
    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    Map<String, double[]> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    ArrayList<String> locationListOfNearest = new ArrayList<>();

    //传感器有关的参数
    ArrayList<String> A = new ArrayList<>();
    float[] rotationMatrix = new float[9];
    
    ArrayList<Long> listOfTime = new ArrayList<>();
    ArrayList<Double> listOfOrientation = new ArrayList<>();
    int TIME0 = 200, LOCATION_NUM_LIMIT = 25, conutForInitialize = 0;
    double[] rotVecValues = {0, 0, 0, 0}, accValues = {0, 0, 0}, gyroValues = {0, 0, 0};
    SparseArray<ArrayList<Double>> accValueList = new SparseArray<>();   //维持一定时间内的加速计历次读数的列表

    //传感器事件监听器
    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ROTATION_VECTOR:
                    float[] rotVecValuesCopy = event.values.clone();
                    rotVecValues[0] = rotVecValuesCopy[0];
                    rotVecValues[1] = rotVecValuesCopy[1];
                    rotVecValues[2] = rotVecValuesCopy[2];
                    rotVecValues[3] = rotVecValuesCopy[3];
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotVecValuesCopy);
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(rotationMatrix, orientation);
                    double angleFixed  = MyUtils.getAngleFixed(orientation[0], angleBias);
                    listOfOrientation.add(angleFixed);
                    break;
//                case Sensor.TYPE_GYROSCOPE:
//                    float[] gyroValuesCopy = event.values.clone();
//                    gyroValues = MyUtils.filterGyroValue(gyroValuesCopy);
//                    break;
//                case Sensor.TYPE_ACCELEROMETER:
////                    float[] accValuesCopy = event.values.clone();
////                    double[] accCopy = new double[3];
////                    accCopy[0] = accValuesCopy[0];
////                    accCopy[1] = accValuesCopy[1];
////                    accCopy[2] = accValuesCopy[2];
////                    accValues = MyUtils.filterAccValues(accCopy,accValueList);
//                    break;
                case Sensor.TYPE_STEP_DETECTOR:
                    double[] location = MyUtils.makeOneStepProcess(listOfOrientation, listOfTime);
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
        initSensor();
        initAccFilterSparseArray();   //初始化用来进行加速计过滤的sparsearray
        initBluetooth();
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

    //蓝牙广播监听器
    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

        @Override
        public void onReceive(Context context, Intent intent) {
            Calendar calendar = Calendar.getInstance();
            Log.d("hh","receive");
            long currentMillisecond = calendar.getTimeInMillis();
            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String remoteMAC;
            final Short rssi;
            if (remoteDevice != null) {    //判断接受到的信息中设备是否为空
                remoteMAC = remoteDevice.getAddress();
                if (bleNodeLoc.containsKey(remoteMAC)) {   //判断接受到的蓝牙节点是否在已知的蓝牙节点map中
                    rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                    if (!dFinished.equals(intent.getAction())) {   //接收到的事件不是结束时
                        if (mAllRssi.containsKey(remoteMAC)) {    //判断之前是否接收过相同蓝牙节点的广播，分别处理
                            ArrayList<Double> list1 = mAllRssi.get(remoteMAC);
                            list1.add(0, (double) rssi);   //因为是引用，所以直接修改的是原对象本身
                        } else {
                            ArrayList<Double> list = new ArrayList<>();
                            list.add((double) rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
                            mAllRssi.put(remoteMAC, list);
                        }
                        String need = remoteMAC + " " + rssi + "\n";
                        Log.d("hh", need);
                        double getAvgOfFilterdRssiValueList = MyUtils.LogarNormalDistribution(mAllRssi.get(remoteMAC), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
                        mRssiFilterd.put(remoteMAC, getAvgOfFilterdRssiValueList);   //更新MAC地址对应信号强度的map
                        if (mRssiFilterd.size() > 2) {
                            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtils.sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
//                            String locationBasedOnMajority = MyUtils.filterLocation(locationListOfNearest, LOCATION_NUM_LIMIT);    //由多数法选出可能的点
//                            Double[] locationNearest = getNearestNode(listSortedNode, bleNodeLoc);   //定位为最近的节点的位置。
                            double[] locationOnBluetooth = getMassCenter(SortedNodeMacAndRssi, bleNodeLoc);   //通过质心定位得到位置
                            webView.loadUrl(setInsetJS(locationOnBluetooth[0] + "", locationOnBluetooth[1] + "","circle_point2"));
                            if(conutForInitialize == 1) {     //判断是否为第一次进入函数
                                if (!(locationOnBluetooth == locationLast)) {    //当新出现的节点与上一个定位点不相同时
                                    double[] locationConfirmed = MyUtils.getSensorConfirm(locationLast, locationOnBluetooth, timeLast, currentMillisecond, listOfTime);
                                    if(!(locationConfirmed == locationLast)){    //仅当定位点发生变化时，才修改记录的定位点和定位时间
                                        timeLast = currentMillisecond;
                                        locationLast = locationConfirmed;
                                    }
                                }
                            }else{
                                locationLast = locationOnBluetooth;
                                timeLast = currentMillisecond;
                                conutForInitialize = 1;
                            }
                            for(int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++){
                                need += SortedNodeMacAndRssi.get(1).get(i) + " " + SortedNodeMacAndRssi.get(1).get(i) + "\n";
                            }
                            mTextGyro.setText(need);
                            webView.loadUrl(setInsetJS(locationLast[0] + "", locationLast[1] + "","circle_point"));
                        }
                    }
                }
            }
        }
    };

    //使用质心定位得到坐标
    public double[] getMassCenter(SparseArray<ArrayList<String>> SortedNodeMacAndRssi, Map<String, double[]> bleNodeLoc) {
        ArrayList<String> SortedNodeMacList = SortedNodeMacAndRssi.get(1);   //获取排好序的节点的MAC地址的列表
        ArrayList<String> SortedNodeRssiList = SortedNodeMacAndRssi.get(2);  //获取排好序的节点的RSSI地址的列表
        int lenOfMacAndRssi = SortedNodeMacList.size();   //首先获取节点列表的长度
        double[] massCenter = {0.0, 0.0};
        for(int i = lenOfMacAndRssi; i > 0; i--){    //从多到少，分别计算方差，方差小于某个值时，认为这几个值相近，求这几个值的质心
            ArrayList<String> rssiList= MyUtils.cutList(SortedNodeRssiList,i);
            ArrayList<String> macList= MyUtils.cutList(SortedNodeMacList,i);
            double staDev = MyUtils.getStaDev(rssiList, MyUtils.getAvg(rssiList), "not sure");
            if(staDev < staDevLimit){
                for(int j = 0; j < i; j++){
                    double[] node =  bleNodeLoc.get(macList.get(j));
                    massCenter[0] += node[0];
                    massCenter[1] += node[1];
                }
                massCenter[0] = massCenter[0] / i;
                massCenter[1] = massCenter[1] / i;
                break;
            }
        }
        return massCenter;
    }




    //提示用户开启手机蓝牙
    private void initBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }
    }
    //和sensor有关的初始化
    private void initSensor() {
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        Sensor rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
//        Sensor accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(listener, accSensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(listener,stepSensor,SensorManager.SENSOR_DELAY_GAME);
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
        double[] location21 = {11.5, 0.7};
        double[] location22 = {15.8, 0.7};
        double[] location23 = {7.8, 4.7};
        double[] location24 = {11.8, 4.7};
        double[] location25 = {15.8, 4.7};
        double[] location26 = {19.8, 4.7};
        double[] location27 = {7.8, 8.7};
        double[] location28 = {11.8, 8.7};
        double[] location29 = {15.8, 8.7};
        double[] location30 = {19.8, 8.7};

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
    public void onResume() {
//        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(listener,stepSensor,SensorManager.SENSOR_DELAY_GAME);
        super.onResume();

    }

    @Override
    public void onDestroy() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                FileCache.saveFile(stringBuffer + "\n");  //位置输出到文件中。
            }
        });
        thread.start();
        EventBus.getDefault().unregister(this);
        cancelTask();
        super.onDestroy();
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



