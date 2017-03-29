package com.example.fanwe.bluetoothlocation;
/*
 * Created by fanwe on 2017/3/29.
 */

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ScanActivity extends AppCompatActivity{
    TextView rssiText;
    Map<String,ArrayList<Double>> m1 = new HashMap<>();  //储存RSSI的MAP
    Map<String,Double> m2 = new HashMap<>();     //过滤后的RSSI的Map
    Map<String,Double[]> bleDevLoc = new HashMap<>(); //固定节点的位置Map
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        setContentView(R.layout.activity_scan);
        rssiText = (TextView)findViewById(R.id.rssiText);
    }
}
