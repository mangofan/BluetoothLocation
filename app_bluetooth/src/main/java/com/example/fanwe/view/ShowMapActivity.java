package com.example.fanwe.view;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.example.fanwe.bluetoothlocation.R;

import org.greenrobot.eventbus.EventBus;

public class ShowMapActivity extends AppCompatActivity {
    boolean isstop_location=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_map);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isstop_location){
                    EventBus.getDefault().post(new Stop());
                    Snackbar.make(view, "正在取消定位....", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }else {
                    EventBus.getDefault().post("定位");
                    Snackbar.make(view, "正在定位中....", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
    }

}
