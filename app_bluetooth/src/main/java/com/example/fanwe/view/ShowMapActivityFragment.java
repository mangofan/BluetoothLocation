package com.example.fanwe.view;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
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
import com.example.fanwe.service.BleService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import utils.FileCache;
import utils.MyUtils;

import static android.app.Activity.RESULT_OK;
import static com.example.fanwe.bluetoothlocation.R.id.textView;
import static com.example.fanwe.bluetoothlocation.R.id.textView1;
import static com.example.fanwe.bluetoothlocation.R.id.textView2;
import static com.example.fanwe.bluetoothlocation.R.id.textView4;
import static com.example.fanwe.bluetoothlocation.R.id.textView5;
import static com.example.fanwe.bluetoothlocation.R.id.textView6;
import static com.example.fanwe.bluetoothlocation.R.id.webview;
import static com.example.fanwe.view.ShowMapActivity.bluetoothAdapter;

// TODO: 2017/5/9 增加上慢速移动坐标点的代码
// TODO: 2017/8/3 直线下的问题，解决，问学长

public class ShowMapActivityFragment extends Fragment implements Cloneable {

    private static final int ENABLE_BLUETOOTH = 1;
    public static Map<String, String> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
//    float angleBias = 19f;  //建筑物方向与正北向的夹角，建筑物方向是正北向的的北偏东多少度。
    WebView webView;
    TextView mTextAcc, mTextGyro, mTexthh, mTextMass, mTextVar, mTextRecent;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4;
    SensorManager sensorManager;

    //蓝牙有关的参数
    String lastLoc;   //上次或者本次的确定位置
    int TIME_INTERVAL = 1000, HALF_TIME_INTERVAL = 500;
    StringBuffer stringBuffer1 = new StringBuffer();
    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存所有RSSI的MAP
    Map<String, Double> mRssiAvg = new HashMap<>();     //过滤后的RSSI的Map
//    Map<String, Float> bleNodeRssiBias = new HashMap<>();   //节点信号强度的偏差值Map
    Map<String, ArrayList<String>> massCenter = new HashMap<>();
    LongSparseArray<String> recentLocationMapRaw = new LongSparseArray<>();
    LongSparseArray<String> locationMapOfOneSec = new LongSparseArray<>();

    //传感器有关的参数
    int sensorCount = 0;
    long lastTimeOfSensor ;
    int countForInitialize = 0;   //标志是否第一次进入传感器确认函数，如果为第一次，值为零；否则值为1
    SparseArray<ArrayList<Double>> accValueList = new SparseArray<>();   //维持一定时间内的加速计历次读数的列表
    //    float[] rotationMatrix = new float[9];
    //    ArrayList<Double> listOfOrientation = new ArrayList<>();
    //    double[] rotVecValues = {0, 0, 0, 0}, accValues = {0, 0, 0}, gyroValues = {0, 0, 0};


    private BleService bleService;
    private ServiceConnection mConnection = new ServiceConnection() {   //建立Activity和service之间的连接
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bleService = ((BleService.MyBinder) service).getService();  //连接时调用,返回一个BleService对象
            bleService.setBleListener(new BleListener() {
                @Override
                public void onBleComing(String mac) {
                    handleMessage(mac);
                }
            });  //注册回调接口来接收蓝牙信息
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {   //service意外断开时接收
            bleService = null;
        }
    };

    //传感器事件监听器
    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_STEP_DETECTOR:
                    sensorCount += 1;
                    String hh = sensorCount + "";
                    mTextAcc.setText(hh);
                    lastTimeOfSensor = Calendar.getInstance().getTimeInMillis();
//                    MyUtils.makeOneStepProcess(listOfOrientation, lastTimeOfSensor);
                    break;
                //                case Sensor.TYPE_ROTATION_VECTOR:
//                    float[] rotVecValuesCopy = event.values.clone();
//                    rotVecValues[0] = rotVecValuesCopy[0];
//                    rotVecValues[1] = rotVecValuesCopy[1];
//                    rotVecValues[2] = rotVecValuesCopy[2];
//                    rotVecValues[3] = rotVecValuesCopy[3];
//                    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotVecValuesCopy);
//                    float[] orientation = new float[3];
//                    SensorManager.getOrientation(rotationMatrix, orientation);
//                    double angleFixed = MyUtils.getAngleFixed(orientation[0], angleBias);
//                    listOfOrientation.add(angleFixed);
//                    break;
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
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    public ShowMapActivityFragment() {
    }

    public void handleMessage(String mac) {
        String[] macAndRssi = mac.split(",");
        String remoteMac = macAndRssi[0];
        Double rssi = Double.valueOf(macAndRssi[1]);

        if (mAllRssi.containsKey(remoteMac)) {    //判断之前是否接收过相同蓝牙节点的广播，分别处理
            mAllRssi.get(remoteMac).add(0, rssi);
        } else {
            ArrayList<Double> list = new ArrayList<>();
            list.add(rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
            mAllRssi.put(remoteMac, list);
        }

        String need = mac.split(":")[5] + "\n";
        Log.d("hh", need);

        double getAvgFilterdRssiValue = MyUtils.LogarNormalDistribution(mAllRssi.get(remoteMac), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
        mRssiAvg.put(remoteMac, getAvgFilterdRssiValue);   //更新MAC地址对应信号强度的map
        if (mRssiAvg.size() > 1) {
            String location = "";
            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtils.sortNodeBasedOnRssi(mRssiAvg, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
            String[] locationOnBluetoothTemp = MyUtils.getMassCenter(SortedNodeMacAndRssi, bleNodeLoc).split(":");   //通过质心定位得到位置
            mTextVar.setText(locationOnBluetoothTemp[1]);

            String locationOnBluetooth = locationOnBluetoothTemp[0];
            long nowTime = Calendar.getInstance().getTimeInMillis();
            recentLocationMapRaw.put(nowTime, locationOnBluetooth);   //将每次蓝牙算出的质心位置的放入map中
            
            if (countForInitialize == 1) {     //判断是否为第一次进入函数
                if(getRecentConfirm(lastLoc) != null) {    //如果接受到的数据时间跨度不足1秒，无法完成滤波，则继续接收信号，跨度长于1秒时继续进行处理
                    lastLoc = locationOnBluetooth;
                    int flag = MyUtils.getSensorState(nowTime, lastTimeOfSensor, locationOnBluetooth);
                    String needMassCenter = "";
                    if (flag == MyUtils.MOVING) {
                        location = locationOnBluetooth;   //如果在运动中，就使用运动中计算出来的
                        needMassCenter = "MOVING";
                    } else {
                        massCenter = MyUtils.getStandLocation();  //不在运动中就将这段时间的坐标求平均
                        location = massCenter.get("massCenter").get(0) + "," + massCenter.get("massCenter").get(1);
                        ArrayList<String> x = massCenter.get("x");
                        ArrayList<String> y = massCenter.get("y");
                        needMassCenter = "STANDING" + "\n";
                        for (int i = 0; i < x.size(); i++) {
                            needMassCenter += x.get(i) + "," + y.get(i) + "\n";
                        }
                    }
                    mTextMass.setText(needMassCenter);
                }


                问题在于蓝牙获得的是1秒之前的位置，而传感器是实时的消息，所以两者不匹配，解决方案：对每个时刻的最终确定位置进行确定，存在一个列表中，有另一个函数专门用来显示位置
            } else {   //第一次进入函数时
                location = locationOnBluetooth;
                lastLoc = locationOnBluetooth;
                countForInitialize = 1;
            }

            mTexthh.setText(locationOnBluetoothTemp[0] + "\n" + locationOnBluetooth);
            stringBuffer1.append(locationOnBluetooth);
            stringBuffer1.append("\n");

            webView.loadUrl(setInsetJS(Double.valueOf(location.split(",")[0]) + "", Double.valueOf(location.split(",")[1]) + "", "circle_point"));
            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
                need += SortedNodeMacAndRssi.get(1).get(i).split(":")[5] + " " + SortedNodeMacAndRssi.get(2).get(i) + "\n";
            }
            mTextGyro.setText(need);
        }
    }


    //根据最近几次确定的位置，应当排除的情况是定位点出现ABA这种来回的情况时, 要求几秒之内，定位的轨迹应该是一条线，不应该成环，即出现ABA这种情况，这种时候应该过滤掉B
    public String getRecentConfirm(String location) {
        long startTime = recentLocationMapRaw.keyAt(0);  //map中存在的最早的时间
        long endTime = recentLocationMapRaw.keyAt(recentLocationMapRaw.size() - 1);  //map中存在最晚的时间
        long stopTime = endTime - 1000;  //本次工作停止的时间,也是下次开始的时间

        if(stopTime < startTime){
            return null;  //如果整个map中的时间短于1秒，返回null， 期待更多结果。
        }

        for(int i = 0; recentLocationMapRaw.keyAt(i) < stopTime; i++){  //对map中每个值，向后数一秒，计算这一秒内的位置，存入locationMapOfOneSec
            int thisTimeEndIndex = MyUtils.searchTimeList(recentLocationMapRaw, recentLocationMapRaw.keyAt(i) + TIME_INTERVAL);  //查找本次循环中的结束时间的index
            String loc = MyUtils.findTheLoc(i, thisTimeEndIndex, recentLocationMapRaw);  //求这段时间内，出现次数最多的位置
            locationMapOfOneSec.put((recentLocationMapRaw.keyAt(i) + HALF_TIME_INTERVAL), loc);  //将每次算出来的位置存入map
            recentLocationMapRaw.removeAt(i);
            i--;
        }

        String flag = "flag";
        int count = 0;
        for(int i = 0; i < locationMapOfOneSec.size(); i++){   //map去掉一个元素之后，.size会不会跟着变化？
            if (locationMapOfOneSec.valueAt(i).equals(flag)){
                count += 1;
                if(count > 100){   //当满足100个时，返回此时的flag，确定是位置，并且将此i之前的所有元素删除，维持map不能太长
                    Log.d("hh", flag);
                    for(int j = i; j > 0; j--){
                        locationMapOfOneSec.removeAt(j);
                    }
                    return flag;
                }
            } else {
                flag = locationMapOfOneSec.valueAt(i);
                count = 1;
                for(int j = i-1 ; j > 0; j--){   //当flag值出现变化时，将之前的节点全部去掉，节省下次进入的遍历花费
                    locationMapOfOneSec.removeAt(j);
                }
                i = 0;
            }
        }
        return location;

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_show_map, null);
        webView = (WebView) root.findViewById(webview);
        mTextAcc = (TextView) root.findViewById(textView1);
        mTextGyro = (TextView) root.findViewById(textView2);
        mTexthh = (TextView)root.findViewById(textView);
        mTextMass = (TextView)root.findViewById(textView4);
        mTextRecent = (TextView)root.findViewById(textView5);
        mTextVar = (TextView)root.findViewById(textView6);
        initWebview();
        initlocation();
        initSensor();
        initAccFilterSparseArray();   //初始化用来进行加速计过滤的sparsearray
        EventBus.getDefault().register(this);
        Intent bindIntent = new Intent(this.getActivity(), BleService.class);
        getActivity().bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);

        return root;
    }

    //根据开启蓝牙的成功与否，开启Bleservice服务
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void startDiscovery(String controlStartDiscovery) {
        Log.d("hh", "button pressed");
        initBluetooth();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            getActivity().startService(new Intent(this.getActivity(), BleService.class));
        }
    }


    //提示用户开启手机蓝牙
    public void initBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        } else {
            getActivity().startService(new Intent(this.getActivity(), BleService.class));
        }
//        if (android.os.Build.VERSION.SDK_INT >= 23) {    //版本大于6.0时，需要申请位置权限，并且打开位置功能。
//            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
//                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);   //申请位置权限
//            if (isLocationOpen(getApplicationContext())) {
//                Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//                startActivityForResult(enableLocate, ENABLE_LOCATION);   //打开位置功能
//            }
//        }
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
        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_GAME);
        lastTimeOfSensor = Calendar.getInstance().getTimeInMillis();    //初始化时给时间列表填充第一个元素
    }

    //对加速计过滤时用到的sparsearray的初始化
    private void initAccFilterSparseArray() {
        for (int i = 0; i < 6; i++) {
            ArrayList<Double> list = new ArrayList<>();
            list.add(0.0);
            accValueList.put(i, list);
        }
    }

    //初始化已知蓝牙节点信息
    void initlocation() {

        String location21 = 14.2 + "," + 0.8;
        String location22 = 8.5 + "," + 4.7;
        String location23 = 14.2 + "," + 8.7;
        String location24 = 19.1 + "," + 5.4;

        String location25 = 24 + "," + 15;
        String location28 = 8 + "," + 15;
        String location37 = 16 + "," + 15;
        String location30 = 0 + "," + 15;

        bleNodeLoc.put("19:18:FC:01:F1:0E", location21);
        bleNodeLoc.put("19:18:FC:01:F1:0F", location22);
        bleNodeLoc.put("19:18:FC:01:F0:F8", location23);
        bleNodeLoc.put("19:18:FC:01:F0:F9", location24);
        bleNodeLoc.put("19:18:FC:01:F0:FA", location25);
        bleNodeLoc.put("19:18:FC:01:F0:FD", location28);
        bleNodeLoc.put("19:18:FC:01:F0:FF", location30);
        bleNodeLoc.put("19:18:FC:00:82:98", location37);


//        bleNodeRssiBias.put("19:18:FC:01:F1:0E", 6f);
//        bleNodeRssiBias.put("19:18:FC:01:F1:0F", 5f);
//        bleNodeRssiBias.put("19:18:FC:01:F0:FD", 2f);
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
    public void onResume() {
//        sensorManager.registerListener(listener, rotVectorSensor, SensorManager.SENSOR_DELAY_GAME);
//            sensorManager.registerListener(listener,stepSensor,SensorManager.SENSOR_DELAY_GAME);
        super.onResume();
    }

    @Override
    public void onDestroy() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                FileCache.saveFile(stringBuffer1 + "\n");  //位置输出到文件中。
            }
        });
        thread.start();
        EventBus.getDefault().unregister(this);
        getActivity().unbindService(mConnection);
        getActivity().stopService(new Intent(this.getActivity(), BleService.class));
        super.onDestroy();
    }

    private String setInsetJS(String rx, String ry, String cir_id) {
        return "javascript:{" +
                "\t$(\"#" + cir_id + "\").attr(\"cx\",\"" + rx + "\");\n" +
                "\t$(\"#" + cir_id + "\").attr(\"cy\",\"" + ry + "\");\n" +
                "\n" +
                "" +
                "}";
    }
}


//        getActivity().registerReceiver(mReceiver, new IntentFilter((BluetoothDevice.ACTION_FOUND)));
//        getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
//        if (bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering()) {
//            bluetoothAdapter.startDiscovery();
//        }
//        cancelTask();
//        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
//        future = executorService.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                bluetoothAdapter.startDiscovery();
//                Log.d("hh", "startDiscovery");
//            }
//        }, 5, 5, TimeUnit.SECONDS);

//    public void cancelTask(){
//        if(future!=null) {
//            future.cancel(false);
//            future = null;
//        }
//    }


//        String locationStringFinal = 0 + "," + 0;
//        int indexOfOldTimeEnd = 0;
//        double[] toReturn = new double[2];
//        long lastTime = recentLocationMapRaw.keyAt(recentLocationMapRaw.size() - 1);   //得到本次插入的时间
//        long targetTime = lastTime - TARGET_TIME;  //求出目标时间，常数值标志现在时间与目标时间的差
//        double oldTimeEnd = targetTime + 0.5 * TIME_INTERVAL;  //所要搜寻的时间范围的下界，常数值标志在targetTIme两侧总的搜寻时间的范围。
//        double oldTimeStart = targetTime - 0.5 * TIME_INTERVAL;    //搜寻的时间范围的上界，常数值标志在targetTIme两侧总的搜寻时间的范围。
//        Map<String, Integer> locationMap = new HashMap<>();
//        int lengthOfMap = recentLocationMapRaw.size();
//        for (int i = 0; i < lengthOfMap; i++) {
//        if (recentLocationMapRaw.keyAt(i) < oldTimeStart) {  //查找到搜索的时间段的上界的index,并把上界之前的值都删除，这样上界肯定是0
//        recentLocationMapRaw.removeAt(i);
//        i -= 1;
//        lengthOfMap -= 1;
//        } else if (recentLocationMapRaw.keyAt(i) < oldTimeEnd) {
//        stringBuffer.append(recentLocationMapRaw.valueAt(i));
//        stringBuffer.append(" ");
////                String locationThisTime = recentLocationMapRaw.valueAt(i);
////                if(locationMap.containsKey(locationThisTime)){
////                    int lastTimeOfThisLocation = locationMap.get(locationThisTime);
////                    if(lastTimeOfThisLocation != (i-1)){     //当两个相同位置（A）在时间序列上出现的位置不相邻时
////                        int flag = 0;
////                        int limit = (i + LIMIT_REAPPEAR < lengthOfMap) ? (i + LIMIT_REAPPEAR):lengthOfMap;
////                        for(int k = i+1; k < limit; k++){   //当连续出现LIMIT_REAPPEAR个以上相同的A时将中间的点都置为A，避免误差
////                            if(recentLocationMapRaw.valueAt(k).equals(recentLocationMapRaw.valueAt(k-1))){
////                                flag = 1;
////                            }else{
////                                flag = 0;
////                                break;
////                            }
////                        }
////                        if(flag == 1) {
////                            for (int j = lastTimeOfThisLocation + 1; j < i; j++) {
////                                recentLocationMapRaw.setValueAt(j, locationThisTime);   //将两次A之间的所有元素修改为A
////                            }
////                        }else{
////                          locationMap.put(locationThisTime, i);  //两次位置不相邻但是第二次出现次数不够时，仍然更新locationMap
////                          }
////                    }else{
////                        locationMap.put(locationThisTime, i);   //两次A相邻时，更新locationMap
////                    }
////                }else{            //当locationMap没出现过这个元素时，在locationMap中添加。
////                    locationMap.put(recentLocationMapRaw.valueAt(i), i);
////                }
//        indexOfOldTimeEnd = i;   //得知所求时间段的index为从0到indexOfOldTimeEnd
//        }else{
//        break;
//        }
//        }



