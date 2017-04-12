package com.example.fanwe.view;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root =inflater.inflate(R.layout.fragment_show_map,null);
     //   mPathView= (PathView) root.findViewById(pathView);
        webView= (WebView) root.findViewById(webview);
        mtext= (TextView) root.findViewById(textView2);
        initWebview();
        initlocation();
        init();
        EventBus.getDefault().register(this);

        /** 模拟 定位位置变化的 代码  测试*/
//        new Thread(new Runnable() {
//            Random random=new Random(10);
//
//            @Override
//            public void run() {
//                while(true) {
//                    final double rx = random.nextFloat()*20 + 1;
//                    final double ry = random.nextFloat()*12+ 1;
//                    getActivity().runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            webView.loadUrl(setInsetJS(rx + "", ry + ""));
//                        }
//                    });
//
//
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//        ).start();

        return root;
    }

    private void init() {
        m1=Collections.synchronizedMap(m1);
        m2=Collections.synchronizedMap(m2);
        m3=Collections.synchronizedList(m3);
        mTest=Collections.synchronizedMap(mTest);

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

    private static final int ENABLE_BLUETOOTH = 1;
    int RSSI_LIMIT = 8, BLE_CHOOSED_NUM = 3;

    Double[] location = new Double[2];
    Map<String,List<Double>> m1 = new HashMap<>();  //储存RSSI的MAP
    Map<String,List<Double>> mTest = new HashMap<>();  //储存键为MAC地址，值为过滤后的RSSI的MAP
    Map<String,Double> m2 = new HashMap<>();     //过滤后的RSSI的Map
    List<String> m3 = new ArrayList<>();
    StringBuffer stringBuffer = new StringBuffer();
    Map<String,Double[]> bleDevLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
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
                                List<Double> list1 = m1.get(remoteMAC);
                                list1.add(0, (double) rssi);
                                m1.put(remoteMAC, list1);
                            } else {
                                ArrayList<Double> list = new ArrayList<>();
                                list.add((double) rssi);   //如果这个MAC地址没有出现过，建立list存储历次rssi
                                m1.put(remoteMAC, list);
                            }
                            final List<Double> rssiValueFliterd = LogarNormalDistribution(m1.get(remoteMAC));  //获取滤波后的信号强度表
                            mTest.put(remoteMAC, rssiValueFliterd);
                            m2.put(remoteMAC, GetAvg(rssiValueFliterd));   //更新MAC地址对应信号强度的map
                            if (m2.size() > 2) {
                                m3 = getSort(m2, BLE_CHOOSED_NUM);     //得到按距离排序的蓝牙节点的列表
                                location = getNearestNode(m3, bleDevLoc);   //定位为最近的节点的位置。
    //                            location = getMassCenterLocation(m3, bleDevLoc);   //通过质心定位得到位置
    //                            String need = location[0].toString() + "," +location[1].toString();
                                String need = m3.get(0).split(":")[5];
                                mtext.setText(need);
                                webView.loadUrl(setInsetJS(location[0] + "", location[1] + ""));
    //                            Calendar now = Calendar.getInstance();
    //                            Integer minute = now.get(Calendar.MINUTE);
    //                            Integer second = now.get(Calendar.SECOND);
    //                            String string = minute.toString() + ":" + second.toString() + " " + Arrays.toString(location) + "\n";
    //                            stringBuffer.append(string);

                                Thread thread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Calendar now = Calendar.getInstance();
                                        Integer minute = now.get(Calendar.MINUTE);
                                        Integer second = now.get(Calendar.SECOND);
                                        Map<String, List<Double>> mapForStore = getMapForStore(m3, m1);
                                        Map<String, List<Double>> mapForStore1 = getMapForStore(m3, mTest);
                                        String need1 = "{" + location[0].toString() + "   " + location[1].toString() + "     "
                                                + minute.toString() + ":" + second.toString() + "\n"
                                                + mapForStore + "\n"
                                                + mapForStore1 + "\n" + "}";
                                        FileCache.saveFile(need1);
                                    }
                                });

                                thread.start();
                        }

                    }
                }
            }
        }
    };

    private List<Double> LogarNormalDistribution(List<Double> m1list){
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

        Double avg, staDev, proLowLim, proHighLim, pdfAltered ;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = GetAvg(logarNormalList);   //求均值
        staDev = GetStaDev(logarNormalList, avg, "logarNormal");  //求标准差

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
//            avg = GetAvg(value);               //重新获取RSSI的平均值
//        }

        return value;
    }

    //给ArrayLIst产生均值的函数
    private Double GetAvg(List<Double> list){
        Double sum = 0.0, avg = 0.0;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += list.get(i);
            }
            avg = sum / list.size();
        }
        return avg;
    }

    //给ArrayList产生标准差的函数
    private Double GetStaDev(ArrayList<Double> list, Double avg, String distribution){
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

    //给ArrayList每个值取对数，以应用于对数正态运算的函数
    private ArrayList<Double> GetLogarNormalList(ArrayList<Double> list){
        ArrayList<Double> list1 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++){
            list1.add(Math.log( 0 - list.get(i)));
        }
        return list1;
    }

    //给RSSI排序
    public ArrayList<String> getSort(Map<String, Double> m2, int BLE_CHOOSED_NUM){
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

    public Double[] getNearestNode (List<String> m3,Map<String,Double[]> bleDevLoc){
        Double[] location =  new Double[2];
        String A = m3.get(0);
        location[0] = bleDevLoc.get(A)[0];
        location[1] = bleDevLoc.get(A)[1];
        return location;
    }

    public Double[] getMassCenterLocation (ArrayList<String> m3,Map<String,Double[]> bleDevLoc){
        Double[] location =  new Double[2];
        String A = m3.get(0),B = m3.get(1), C = m3.get(2);
        Double Ax = bleDevLoc.get(A)[0], Ay = bleDevLoc.get(A)[1], Bx = bleDevLoc.get(B)[0], By = bleDevLoc.get(B)[1], Cx = bleDevLoc.get(C)[0], Cy = bleDevLoc.get(C)[1];
        location[0] = 1.0/3 * ((Ax) + 0.5 * (Ax+Bx) + 0.5 * (Bx+Cx));
        location[1] = 1.0/3 * ((Ay) + 0.5 * (Ay+By) + 0.5 * (By+Cy));
        Log.d("m3",m3.toString());
        Log.d("location", Arrays.toString(location));
        return location;
    }


    //从MAP中选出 list中元素作为键，对应的键值对
    public synchronized  Map<String, List<Double>> getMapForStore (List<String> m3, Map<String,List<Double>> map){
        Map<String,List<Double>> mapReturn = new HashMap<>();
        for (int i = 0 ; i < m3.size(); i++){
            String mac = m3.get(i);
            List<Double> listRssi = map.get(mac);
            mapReturn.put(mac,listRssi);
        }
        return mapReturn;
    }






























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

    private void initBluetooth(){
        if(!bluetoothAdapter.isEnabled()){
            //蓝牙未打开，提醒用户打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, ENABLE_BLUETOOTH);
        }
    }

    @Subscribe(threadMode= ThreadMode.MAIN)
    public void startDiscovery(String  startloaction){
        getActivity().registerReceiver(mReceiver,new IntentFilter((BluetoothDevice.ACTION_FOUND)));
        if(bluetoothAdapter.isEnabled() && !bluetoothAdapter.isDiscovering())
            bluetoothAdapter.startDiscovery();
        getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void stopDiscovery(Stop  stop){
        getActivity().unregisterReceiver(mReceiver);

    }
}

