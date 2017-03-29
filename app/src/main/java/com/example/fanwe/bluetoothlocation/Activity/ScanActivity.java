package com.example.fanwe.bluetoothlocation.Activity;
/*
 * Created by fanwe on 2017/3/29.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.example.fanwe.bluetoothlocation.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ScanActivity extends AppCompatActivity{
    private static final int ENABLE_BLUETOOTH = 1;
    int RSSI_LIMIT = 15, BLE_CHOOSED_NUM = 5;

    TextView rssiText, rssiText1;
    Map<String,ArrayList<Double>> m1 = new HashMap<>();  //储存RSSI的MAP
    Map<String,Double> m2 = new HashMap<>();     //过滤后的RSSI的Map
    Map<String,Double[]> bleDevLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        String dFinished = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String remoteMAC;
            final Short rssi;
            if (remoteDevice != null) {
                remoteMAC = remoteDevice.getAddress();
                if (remoteMAC.equals("19:18:FC:01:F1:0F")) {
                    Log.d("MAC",remoteMAC);
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
                        m2.put(remoteMAC, NormalDistribution(m1.get(remoteMAC)));   //更新MAC地址对应信号强度的map
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        setContentView(R.layout.activity_scan);
        rssiText = (TextView)findViewById(R.id.rssiText);
        rssiText1 = (TextView)findViewById(R.id.rssiText1);
        initBluetooth();
        Log.d("init", "init");
        startDiscovery();
        Log.d("start","start");
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

    private Double NormalDistribution(ArrayList<Double> m1list){
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

        Double avg =0.0, rssiValue = 0.0, staDev, proLowLim, proHighLim, pdfAltered ;  //rssiValue作为一个中间变量在多个计算过程中间使用

        ArrayList<Double> logarNormalList = GetLogarNormalList(value);   //转换成对数形式
        avg = GetAvg(logarNormalList);   //求均值
        staDev = GetStaDev(logarNormalList, avg, "logarNormal");  //求标准差
        proHighLim = Math.exp(0.5 * Math.pow(staDev,2) - avg);
        proLowLim = proHighLim * 0.6;
        String rssiTextString = logarNormalList.toString() + "\n" + staDev.toString() + "\n" + proLowLim + "\n" + proHighLim;
        rssiText.setText(rssiTextString);
        Log.d("before", logarNormalList.toString());
        Log.d("staDev", staDev.toString());
        Log.d("Low", proLowLim.toString());
        Log.d("high",proHighLim.toString());

        for (int i = 0; i < logarNormalList.size(); i++) {          //去掉value中的低概率RSSI
            if (staDev !=0) {
                Double exponent = - Math.pow(logarNormalList.get(i) - avg, 2) / (2 * Math.pow(staDev, 2));
                pdfAltered = Math.exp(exponent) * avg / (0 - value.get(i));
                Log.d("exponent", exponent.toString());
                Log.d("pdf", pdfAltered.toString());
                if (pdfAltered < proLowLim || pdfAltered > proHighLim) {
                    logarNormalList.remove(i);                              //删除不在高概率区域内的数据
                    i -= 1;
                }
            }
        }
        String rssiTextString1= logarNormalList.toString() + "\n";
        rssiText1.setText(rssiTextString1);
        Log.d("After", logarNormalList.toString());

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
}
