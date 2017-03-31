package Activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.fanwe.bluetoothlocation.R;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Button massCenterButtuon, sensorButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
//        startService(new Intent(this, ScanService.class));
    }

    private void initView(){
        massCenterButtuon = (Button)findViewById(R.id.button_mass_center);
        massCenterButtuon.setOnClickListener(this);
        sensorButton = (Button)findViewById(R.id.button_sensor);
        sensorButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        switch (v.getId()){
            case R.id.button_mass_center:
                intent = new Intent(this,ScanActivity.class);
                break;
            case R.id.button_sensor:
                intent = new Intent(this,SensorActivity.class);
                break;
        }
        if(intent != null) {
            Log.d("button", "button");
            startActivity(intent);
        }
    }
}
