package com.example.fanwe.bluetoothlocation.Activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.fanwe.bluetoothlocation.Activity.Service.ScanService;
import com.example.fanwe.bluetoothlocation.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Button locationButtuon;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
//        startService(new Intent(this, ScanService.class));
    }

    private void initView(){
        locationButtuon = (Button)findViewById(R.id.button_1);
        locationButtuon.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        switch (v.getId()){
            case R.id.button_1:
                intent = new Intent(this,ScanActivity.class);
                break;
        }
        if(intent != null)
            Log.d("button","button");
            startActivity(intent);
    }
}
