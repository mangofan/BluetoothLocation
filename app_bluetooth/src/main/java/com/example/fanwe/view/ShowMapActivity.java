package com.example.fanwe.view;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.example.fanwe.bluetoothlocation.R;

import org.greenrobot.eventbus.EventBus;

public class ShowMapActivity extends AppCompatActivity {
    public static BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_map);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bluetoothAdapter = ((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                    EventBus.getDefault().post("start");
                    Snackbar.make(view, "正在定位中....", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
