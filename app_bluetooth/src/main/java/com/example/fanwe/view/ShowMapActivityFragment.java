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
import android.nfc.Tag;
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
import static com.example.fanwe.bluetoothlocation.R.id.webview;
import static com.example.fanwe.view.ShowMapActivity.bluetoothAdapter;

// TODO: 2017/5/9 增加上慢速移动坐标点的代码
// TODO: 2017/5/11 发热量大，减少计算量
public class ShowMapActivityFragment extends Fragment implements Cloneable {

    private static final int ENABLE_BLUETOOTH = 1;
    public static Map<String, double[]> bleNodeLoc = new HashMap<>();    //固定节点的位置Map
    float angleBias = 19f;  //建筑物方向与正北向的夹角，建筑物方向是正北向的的北偏东多少度。
    WebView webView;
    TextView mTextAcc, mTextGyro, mTexthh;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 4;
    SensorManager sensorManager;

    //蓝牙有关的参数
    long timeLast;
    double[] locationLast = new double[2];   //上次或者本次的确定位置
    int RECENT_LIMIT = 8;
    int TARGET_TIME = 1500, TIME_INTERVAL = 1000;
    StringBuffer stringBuffer = new StringBuffer();
    Map<String, ArrayList<Double>> mAllRssi = new HashMap<>();    //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    Map<String, Float> bleNodeRssiBias = new HashMap<>();   //节点信号强度的偏差值Map

    //传感器有关的参数
    float[] rotationMatrix = new float[9];
    ArrayList<Long> listOfTimeSensor = new ArrayList<>();
    ArrayList<Double> listOfOrientation = new ArrayList<>();
    int conutForInitialize = 0;   //标志是否第一次进入传感器确认函数，如果为第一次，值为零；否则值为1
    double[] rotVecValues = {0, 0, 0, 0}, accValues = {0, 0, 0}, gyroValues = {0, 0, 0};
    SparseArray<ArrayList<Double>> accValueList = new SparseArray<>();   //维持一定时间内的加速计历次读数的列表
    LongSparseArray<String> recentLocationMapRaw = new LongSparseArray<>();
    int sensorCount = 0;
    private BleService bleService;
    //建立Activity和service之间的连接
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //连接时调用,返回一个BleService对象
            bleService = ((BleService.MyBinder) service).getService();

            //注册回调接口来接收蓝牙信息
            bleService.setBleListener(new BleListener() {
                @Override
                public void onBleComing(String mac) {
                    handleMessage(mac);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //service意外断开时接收
            bleService = null;
        }
    };

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
                    double angleFixed = MyUtils.getAngleFixed(orientation[0], angleBias);
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
                    MyUtils.makeOneStepProcess(listOfOrientation, listOfTimeSensor);
                    sensorCount += 1;
                    String hh = sensorCount + "";
//                    mTextAcc.setText(hh);
                    break;
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

        Calendar calendar = Calendar.getInstance();
        long currentMillisecond = calendar.getTimeInMillis();
        if (bleNodeRssiBias.containsKey(remoteMac)) {
            rssi += bleNodeRssiBias.get(remoteMac);
        }
        if (mAllRssi.containsKey(remoteMac)) {    //判断之前是否接收过相同蓝牙节点的广播，分别处理
            ArrayList<Double> list1 = mAllRssi.get(remoteMac);
            list1.add(0, rssi);   //因为是引用，所以直接修改的是原对象本身
        } else {
            ArrayList<Double> list = new ArrayList<>();
            list.add(rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
            mAllRssi.put(remoteMac, list);
        }
        String need1 = remoteMac + " " + rssi + "\n";
        String need = " ";
        Log.d("hh", need1);
        double getAvgOfFilterdRssiValueList = MyUtils.LogarNormalDistribution(mAllRssi.get(remoteMac), RSSI_LIMIT);  //获取滤波后的信号强度表和强度平均值
        mRssiFilterd.put(remoteMac, getAvgOfFilterdRssiValueList);   //更新MAC地址对应信号强度的map
        if (mRssiFilterd.size() > 2) {
            SparseArray<ArrayList<String>> SortedNodeMacAndRssi = MyUtils.sortNodeBasedOnRssi(mRssiFilterd, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
            double[] locationOnBluetooth = MyUtils.getMassCenter(SortedNodeMacAndRssi, bleNodeLoc);   //通过质心定位得到位置
            locationOnBluetooth = getRecentConfirm(currentMillisecond, locationOnBluetooth);
//            if (conutForInitialize == 1) {     //判断是否为第一次进入函数
//                if (!(locationOnBluetooth == locationLast)) {    //当新出现的节点与上一个定位点不相同时
//                    double[] locationSensorConfirmed = MyUtils.getSensorConfirm(locationLast, locationOnBluetooth, timeLast, currentMillisecond, listOfTimeSensor);
//                    if (!(locationSensorConfirmed == locationLast)) {    //仅当定位点发生变化时，才修改记录的定位点和定位时间
//                        timeLast = currentMillisecond;
//                        locationLast = locationSensorConfirmed;   //此时的locationLast就是这次的真正位置，
//                    }
//                }
//            } else {
//                locationLast = locationOnBluetooth;
//                timeLast = currentMillisecond;
//                conutForInitialize = 1;
//            }
//            webView.loadUrl(setInsetJS(locationLast[0] + "", locationLast[1] + "", "circle_point"));
//            for (int i = 0; i < SortedNodeMacAndRssi.get(1).size(); i++) {
//                need += SortedNodeMacAndRssi.get(1).get(i) + " " + SortedNodeMacAndRssi.get(2).get(i) + "\n";
//            }
//            mTextGyro.setText(need);
        }
    }

    //根据最近几次确定的位置，应当排除的情况是定位点出现ABA这种来回的情况时, 要求几秒之内，定位的轨迹应该是一条线，不应该成环，即出现ABA这种情况，这种时候应该过滤掉B
    public double[] getRecentConfirm(long time, double[] locationOnBluetooth) {
        String locationString = locationOnBluetooth[0] + "," + locationOnBluetooth[1];
        String locationStringFinal = "hh";
        recentLocationMapRaw.put(time, locationString);
        int indexOfOldTimeEnd = 0;
        double[] toReturn = new double[2];
        long lastTime = recentLocationMapRaw.keyAt(recentLocationMapRaw.size() - 1);   //得到本次插入的时间
        long targetTime = lastTime - TARGET_TIME;  //求出目标时间，常数值标志现在时间与目标时间的差
        double oldTimeEnd = targetTime + 0.5 * TIME_INTERVAL;  //所要搜寻的时间范围的下界，常数值标志在targetTIme两侧总的搜寻时间的范围。
        double oldTimeStart = targetTime - 0.5 * TIME_INTERVAL;    //搜寻的时间范围的上界，常数值标志在targetTIme两侧总的搜寻时间的范围。
        Map<String, Integer> locationMap = new HashMap<>();
        int lengthOfMap = recentLocationMapRaw.size();


        for (int i = 0; i < lengthOfMap; i++) {
            if (recentLocationMapRaw.keyAt(i) < oldTimeStart) {  //查找到搜索的时间段的上界的index,并把上界之前的值都删除，这样上界肯定是0
                recentLocationMapRaw.removeAt(i);
                i -= 1;
                lengthOfMap -= 1;
            } else if (recentLocationMapRaw.keyAt(i) < oldTimeEnd) {
                stringBuffer.append(recentLocationMapRaw.valueAt(i));
                stringBuffer.append(" ");
                String locationThisTime = recentLocationMapRaw.valueAt(i);
                if(locationMap.containsKey(locationThisTime)){
                    int lastTimeOfThisLocation = locationMap.get(locationThisTime);
                    if(lastTimeOfThisLocation != (i-1)){     //当两个相同位置（A）在时间序列上出现的位置不相邻时
                        for(int j = lastTimeOfThisLocation + 1; j < i; j++){
                            recentLocationMapRaw.setValueAt(j, locationThisTime);   //将两次A之间的所有元素修改为A
                        }
                    }else{
                        locationMap.put(locationThisTime, i);   //两次A相邻时，更新locationMap
                    }
                }else{            //当locationMap没出现过这个元素时，在locationMap中添加。
                    locationMap.put(recentLocationMapRaw.valueAt(i), i);
                }
                indexOfOldTimeEnd = i;   //得知所求时间段的index为从0到indexOfOldTimeEnd
            }else{
                break;
            }
        }
        stringBuffer.append("\n");
        mTextAcc.setText(stringBuffer);

        //对这一秒内出现的节点进行计数，取最大。
        int flag = 0;
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < indexOfOldTimeEnd; i++) {
            String location = recentLocationMapRaw.valueAt(i);
            if (map.containsKey(location)) {
                Integer count = map.get(location);
                count = count + 1;
                map.put(location, count);
            } else {
                map.put(location, 0);
            }
        }
        for (String loc : map.keySet()) {
            int locNum = map.get(loc);
            if (locNum > flag) {
                flag = locNum;
                locationStringFinal = loc;
                toReturn[0] = Double.valueOf(loc.split(",")[0]);
                toReturn[1] = Double.valueOf(loc.split(",")[1]);
            }
        }

        webView.loadUrl(setInsetJS(toReturn[0] + "", toReturn[1] + "", "circle_point2"));
        return toReturn;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_show_map, null);
        webView = (WebView) root.findViewById(webview);
        mTextAcc = (TextView) root.findViewById(textView1);
        mTextGyro = (TextView) root.findViewById(textView2);
        mTexthh = (TextView)root.findViewById(textView);
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
        listOfTimeSensor.add(Calendar.getInstance().getTimeInMillis());    //初始化时给时间列表填充第一个元素
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
    public void onPause() {
//       sensorManager.unregisterListener(listener);
        super.onPause();
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
                FileCache.saveFile(stringBuffer + "\n");  //位置输出到文件中。
            }
        });
        thread.start();
        EventBus.getDefault().unregister(this);
        getActivity().stopService(new Intent(this.getActivity(), BleService.class));
        Log.d("hh","service stop");
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



