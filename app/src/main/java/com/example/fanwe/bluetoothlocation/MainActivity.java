package com.example.fanwe.bluetoothlocation;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Button locationButtuon;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
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
            startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
