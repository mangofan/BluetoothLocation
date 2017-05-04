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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
import utils.Quaternion;

import static com.example.fanwe.bluetoothlocation.R.id.textView1;
import static com.example.fanwe.bluetoothlocation.R.id.textView2;
import static com.example.fanwe.bluetoothlocation.R.id.webview;
import static utils.MyUtils.LogarNormalDistribution;
import static utils.MyUtils.filterAccValues;
import static utils.MyUtils.filterGyroValue;
import static utils.MyUtils.getAccCompleted;
import static utils.MyUtils.getAvg;
import static utils.MyUtils.getConvertAcc;
import static utils.MyUtils.sortNodeBasedOnRssi;

/**
 * A placeholder fragment containing a simple view.
 */
public class ShowMapActivityFragment extends Fragment implements Cloneable {

    private static final int ENABLE_BLUETOOTH = 1;
    WebView webView;
    TextView mTextAcc;
    TextView mTextGyro;
    int RSSI_LIMIT = 5, BLE_CHOOSED_NUM = 3;
    SensorManager sensorManager;


    //蓝牙有关的参数
    Map<String, List<Double>> mAllRssi = new HashMap<>();  //储存RSSI的MAP
    Map<String, Double> mRssiFilterd = new HashMap<>();     //过滤后的RSSI的Map
    StringBuffer stringBuffer = new StringBuffer();
    Map<String, Double[]> bleNodeLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    ArrayList<String> locationListOfNearest = new ArrayList<>();
    Long timeLast;
    String locationLast;

    //传感器有关的参数
    int TIME0 = 200, LOCATION_NUM_LIMIT = 25, conutForInitialize = 0;
    long[] timeArray = new long[2];
    Double Sx = 0.0, Sy = 0.0, V0x = 0.0, V0y = 0.0, ax = 0.0, ay = 0.0;
    float[] rotVecValues = {0, 0, 0, 0}, accValues = {0, 0, 0}, gyroValues = {0, 0, 0}, magValues = {0, 0, 0};
    SparseArray<ArrayList<Double>> accValueList = new SparseArray<>();   //维持一定时间内的加速计历次读数的列表
    LongSparseArray<Double[]> locationBasedOnSensor = new LongSparseArray<>();
    ArrayList<Long> listOfTime = new ArrayList<>();
    ArrayList<String> A = new ArrayList<>();

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
                    float[] accValuesCopy = event.values.clone();
//                    tranformAndStoreAccValue();
                    BigDecimal[] accCopy = new BigDecimal[3];
                    accCopy[0] = new BigDecimal(accValuesCopy[0]);
                    accCopy[1] = new BigDecimal(accValuesCopy[1]);
                    accCopy[2] = new BigDecimal(accValuesCopy[2]);
                    accValues = filterAccValues(accCopy,accValueList);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    magValues = event.values.clone();
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
        initSensor();
        initAccFilterSparseArray();   //初始化用来进行加速计过滤的sparsearray
        //设置每隔TIME0更新UI
        Timer updateTimer = new Timer("Update");
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateGUI();
                Thread thread = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Long timeInMillis = Calendar.getInstance().getTimeInMillis();
                                    String need1 = timeInMillis.toString() + " " + A.toString();
                                    FileCache.saveFile(need1);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                thread.start();
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
        float[] p = new float[4];
        SensorManager.getQuaternionFromVector(p, rotVecValues);  //由旋转矢量获得四元数
        Quaternion pQuaternion = new Quaternion(p);
        Double[] accConverted = getConvertAcc(getAccCompleted(accValues), pQuaternion);  //将加速度矢量转换到地理坐标系
        accConverted = filterAccValues(accConverted, accValueList);
        ax = -accConverted[0];
        ay = accConverted[1];

        Long currentMillisecond = Calendar.getInstance().getTimeInMillis();
        double timeDiff = (currentMillisecond - listOfTime.get(0)) / 1000.0;

        if((Math.abs(ax) == 0.0) && (Math.abs(ay) == 0.0)){
            V0x = 0.0;
            V0y = 0.0;
        }else{
            V0x = V0x + ax * timeDiff;
            V0y = V0y + ay * timeDiff;
        }

        Sx += V0x * timeDiff + 0.5 * ax * timeDiff * timeDiff;   //计算时时间单位应该用秒
        Sy += V0y * timeDiff + 0.5 * ay * timeDiff * timeDiff;

        mTextAcc.setText(ax + "," + ay + "\n" + V0x + "," + V0y);
        mTextGyro.setText(Sx + "\n" + Sy + "");

        webView.loadUrl(setInsetJS(Sx + "", Sy + "","circle_point"));

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
//                            String locationNearest = listSortedNode.get(0);
                            locationListOfNearest.add(0,listSortedNode.get(0));
                            String locationBasedOnMajority = filterLocation();    //由多数法选出可能的点
//                            Double[] locationNearest = getNearestNode(listSortedNode, bleNodeLoc);   //定位为最近的节点的位置。
//                            location = getMassCenterLocation(listSortedNode, bleNodeLoc);   //通过质心定位得到位置
                            if(conutForInitialize == 1) {
                                if (!locationBasedOnMajority.equals(locationLast)) {    //当新出现的节点与上一个定位点不相同时
                                    String locationTrue = getSensorConfirm(locationLast, locationBasedOnMajority, timeLast,currentMillisecondBluetooth);
                                    timeLast = currentMillisecondBluetooth;
                                    locationLast = locationTrue;
                                }
                            }else{
                                locationLast = locationListOfNearest.get(0);
                                timeLast = currentMillisecondBluetooth;
                                conutForInitialize = 1;
                            }
//                            webView.loadUrl(setInsetJS(bleNodeLoc.get(locationLast)[0] + "", bleNodeLoc.get(locationLast)[1] + "","circle_point"));
//                            webView.loadUrl(setInsetJS(bleNodeLoc.get(locationNearest)[0] + "", bleNodeLoc.get(locationNearest)[1] + "","circle_point"));
//                            webView.loadUrl(setInsetJS(bleNodeLoc.get(locationBasedOnMajority)[0] + "", bleNodeLoc.get(locationBasedOnMajority)[1] + "","circle_point2"));
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

    //在locationListOfNearest中选择多数作为当前的位置。
    public String filterLocation(){
        final String toReturn;
        if (locationListOfNearest.size() > LOCATION_NUM_LIMIT) {
            Map<String, Integer> locationCountMap = new HashMap<>();
            locationCountMap.put(locationListOfNearest.get(0), 1);
            for (int i = 1; i < locationListOfNearest.size(); i++) {
                String location = locationListOfNearest.get(i);
                if(locationCountMap.containsKey(location)){
                    locationCountMap.put(location, locationCountMap.get(location) + 1);
                }else {
                    locationCountMap.put(location,1);
                }
            }
            toReturn = sortLocationBasedOnCount(locationCountMap);
            locationListOfNearest.remove(locationListOfNearest.size() - 1);
        }else{
            toReturn = locationListOfNearest.get(0);   //在列表长度小于限制时，以列表头元素作为频率最高的值，
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



    public ArrayList<String> changelocationListOfNearestToCoordinate(ArrayList<String> locationListOfNearest){
        ArrayList<String> toReturn = new ArrayList<>();
        for (int i = 0; i < locationListOfNearest.size(); i++){
            Double[] test = bleNodeLoc.get(locationListOfNearest.get(i));
            toReturn.add(test[0].toString());
            toReturn.add(test[1].toString());
            toReturn.add(" ");
        }
        return toReturn;
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
        sensorManager.unregisterListener(listener);
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

//    //初始化需要加同步锁的变量
//    private void initSynchronize() {
//        mAllRssi = Collections.synchronizedMap(mAllRssi);
//        mRssiFilterd = Collections.synchronizedMap(mRssiFilterd);
//    }

//从MAP中选出 list中元素作为键，对应的键值对
//    public synchronized Map<String, List<Double>> getMapForStore(List<String> listSortedNode, Map<String, List<Double>> map) {
//        Map<String, List<Double>> mapReturn = new HashMap<>();
//        for (int i = 0; i < listSortedNode.size(); i++) {
//            String mac = listSortedNode.get(i);
//            List<Double> listRssi = map.get(mac);
//            mapReturn.put(mac, listRssi);
//        }
//        return mapReturn;
//    }


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
//                                final ArrayList testList = (ArrayList) locationListOfNearest.clone();
//                                try {
//                                    Calendar now = Calendar.getInstance();
//                                    Long timeInMillis = now.getTimeInMillis();
//                                    String need1 = (timeInMillis - anchorTime) + "  " + locationFilterdInCoordinate[0] + "  " + locationFilterdInCoordinate[1] + "\n" + changelocationListOfNearestToCoordinate(testList) + "\n\n";
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

//    Thread thread = new Thread(
//            new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        String need1 = time.toString() + "  " + bleNodeLoc.get(toReturn)[0].toString() + ","
//                                + bleNodeLoc.get(toReturn)[0].toString() + "\n" + locationListOfNearestClone + "\n";
//                        FileCache.saveFile(need1);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//        thread.start();



