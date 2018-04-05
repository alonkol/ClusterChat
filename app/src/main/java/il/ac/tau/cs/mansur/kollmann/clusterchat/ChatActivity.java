package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private String mdeviceName;
    private BluetoothService.ConnectedThread mThread;
    private String mdeviceAddress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_window);
        Intent intent = getIntent();
        mdeviceName = intent.getStringExtra("clusterchat.deviceName");
        mdeviceAddress = intent.getStringExtra("clusterchat.deviceAddress");
        mThread = MainActivity.mBluetoothService.mConnectedThreadList.get(0);
        Log.d(TAG, String.format("Init chatService for device %s address %s" ,mdeviceName, mdeviceAddress));
        // mThread = MainActivity.mBluetoothService.getConnectedThread(mdeviceAddress);
    }
}
