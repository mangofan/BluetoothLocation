package activity;
/*
 * Created by fanwe on 2017/3/31.
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.example.fanwe.bluetoothlocation.R;

import java.util.Timer;
import java.util.TimerTask;

public class SensorActivity extends AppCompatActivity {
    private SensorManager sensorManager;
    private TextView sensorTextView1;

    Integer TIME0 = 200;
    float[] rotVecValues = new float[5];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);
        sensorTextView1 = (TextView)findViewById(R.id.sensor_TextView_1);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor rotvectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(listener, rotvectorSensor, sensorManager.SENSOR_DELAY_GAME);

        //设置每隔TIME0更新UI
        Timer updateTimer = new Timer("Update");
        updateTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                updateGUI();
            }
        },0,TIME0);
    }

    //UI 更新方法
    private void updateGUI(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String need = String.valueOf(rotVecValues[0]) + '\n' + String.valueOf(rotVecValues[1]) + '\n' + String.valueOf(rotVecValues[2]) + '\n' + String.valueOf(rotVecValues[3]);  //展示Sx，Sy，ax，ay
                sensorTextView1.setText(need);
            }
        });
    }

    private SensorEventListener listener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
                rotVecValues = event.values.clone();
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Sensor rotvectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(listener, rotvectorSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(listener);
        super.onPause();
    }
}
