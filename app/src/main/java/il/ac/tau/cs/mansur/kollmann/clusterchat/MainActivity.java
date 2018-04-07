package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";
    private static final int discoveryPeriodSeconds = 5 * 60;
    private static final int discoveryPeriodMillis = discoveryPeriodSeconds * 1000;
    public static String myDeviceName;
    public static String myDeviceId;
    private MainActivity mainActivity;
    private static ArrayAdapter<DeviceContact> mConnectedDevicesArrayAdapter;
    public static BluetoothService mBluetoothService;
    public static ConversationsManager mConversationManager;
    public static RoutingTable mRoutingTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        // Request bluetooth permissions
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            // TODO: static member?
            final int REQUEST_ENABLE_BT = 3;

            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ensureDiscoverable();
            }
        }, 0, discoveryPeriodMillis);

        // Find and set up the ListView for newly discovered devices
        mConnectedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);
        ListView newDevicesListView = findViewById(R.id.conversations);
        newDevicesListView.setAdapter(mConnectedDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        mBluetoothService = new BluetoothService();
        mConversationManager = new ConversationsManager();
        mRoutingTable = new RoutingTable();

        // TODO:? Load existing conversations
        File dir = getDir("Conversations", MODE_PRIVATE);
        for (File contact: dir.listFiles()) {
            mConversationManager.addMessagesFromHistory(contact);
        }
    }

    // Adding device to UI
    public static void addDeviceToUi(BluetoothDevice device) {
        DeviceContact deviceContact = new DeviceContact(device);
        if (mConnectedDevicesArrayAdapter.getPosition(deviceContact) == -1) {
            mConnectedDevicesArrayAdapter.add(deviceContact);
        }
    }

    // Removing device from UI
    public static void removeDeviceFromUi(DeviceContact deviceContact) {
        if (mConnectedDevicesArrayAdapter.getPosition(deviceContact) != -1) {
            mConnectedDevicesArrayAdapter.remove(deviceContact);
        }

    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * This is the maximal possible value.
     * */
    private void ensureDiscoverable() {
        if (BluetoothAdapter.getDefaultAdapter().getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoveryPeriodSeconds);
            startActivity(discoverableIntent);
        }
    }

    /**
     * The on-click listener for all devices in the ListViews
     */
    private AdapterView.OnItemClickListener mDeviceClickListener  = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);
            String name = info.substring(0, info.length() - 18);
            Intent intent = new Intent(mainActivity, ChatActivity.class);
            intent.putExtra("clusterchat.deviceName", name);
            intent.putExtra("clusterchat.deviceAddress", address);
            startActivity(intent);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device.getName() != null){
                    mBluetoothService.connect(device);
                }
            }
        }
    };

    public static DeviceContact findDeviceContact(String deviceId) {
        for(int i=0 ; i< mConnectedDevicesArrayAdapter.getCount() ; i++){
            DeviceContact dc = mConnectedDevicesArrayAdapter.getItem(i);
            if (dc.getDeviceId().equals(deviceId)){
                return dc;
            }
        }
        Log.e(TAG, "Cant find device contact with id " + deviceId);
        return null;
    }
}
