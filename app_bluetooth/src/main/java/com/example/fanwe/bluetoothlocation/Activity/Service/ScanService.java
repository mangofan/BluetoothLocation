package com.example.fanwe.bluetoothlocation.Activity.Service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.Nullable;

/**
 * Created by zhb_z on 2016/10/27 0027.
 */

public class ScanService extends Service {
    public static final String GET_BLUETOOTH_RSSI="GET_BLUETOOTH_RSSI";
    public static final int START_BLUETOOTH=0,STOP_BLUETOOTH=1,SET_A_N=2,SET_INTENTION=3;
    Handler handler=new MyHandler();
    Messenger messenger=new Messenger(handler);
    boolean searchflag;
    String Intention=null;
/*    double A,N;
    {
        double initA1 = -47;
        double initA2 = -57;
        A = Math.abs(initA1);
        N = Math.log10((Math.abs(initA2) - A) / Math.log10(4));
    }*/
    final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String name,MAC;
            int RSSI;
            //当设备开始扫描时。
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //从Intent得到blueDevice对象
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    //信号强度。
                    RSSI = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                    name=intent.getExtras().getString(BluetoothDevice.EXTRA_NAME);
                    MAC=device.getAddress();
                    //----------------------------------------------
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(GET_BLUETOOTH_RSSI);
                    sendIntent.putExtra("RSSI",RSSI);
                    sendIntent.putExtra("MAC",MAC);
                    sendIntent.putExtra("name",name);
                    //sendIntent.putExtra("distance",distance(RSSI));
                    ScanService.this.sendBroadcast(sendIntent);
                    //----------------------------------------------------
                    if(MAC!=null&&MAC.equals(Intention))
                        handler.sendEmptyMessage(START_BLUETOOTH);
                }
            }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                if(searchflag)
                    handler.sendEmptyMessage(START_BLUETOOTH);
                Message msg=Message.obtain();
            }
        }
    };
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return  messenger.getBinder();
    }

    @Override
    public void onCreate() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);
        filter=new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);
    }
    private class MyHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case START_BLUETOOTH:
                    startBlueTooth();
                    break;
                case STOP_BLUETOOTH:
                    searchflag=false;
                    break;
/*                case SET_A_N:
                    initN(msg.getData().getDouble("A1"),msg.getData().getDouble("A2"));
                    break;
                case SET_INTENTION:
                    if(Intention!=null&&Intention.equals(msg.getData().getString("intention"))) {
                        //Intention=null;
                    }
                    else {
                        Intention = msg.getData().getString("intention");
                        startBlueTooth();
                    }
                    break;*/
            }
        }
    }
    private void startBlueTooth(){
        searchflag=true;
        if(adapter.isDiscovering())
            adapter.cancelDiscovery();
        else
            adapter.startDiscovery();
    }

/*    private void initN(double A1,double A2){

        A=Math.abs(A1);
        N=Math.log10((Math.abs(A2)-A)/Math.log10(4));
    }
    private double distance(double RSSI){
        double ret=Math.pow(10,(Math.abs(RSSI)-A)/Math.pow(10,N));
        ret=Double.valueOf(String.format("%.2f",ret));
        return ret;
    }
    private double myDistance(double RSSI){
        double d1=-67;
        return Math.sqrt(Math.pow(10,(d1-RSSI)/10));
    }*/
}
