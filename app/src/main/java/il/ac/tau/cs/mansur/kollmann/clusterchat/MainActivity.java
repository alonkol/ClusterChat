package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TODO: create message handler
        Handler handler = new Handler();
        BluetoothService bluetoothService = new BluetoothService(handler);

    }
}
