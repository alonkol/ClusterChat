package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";
    private static final int discoveryPeriodSeconds = 5 * 60;
    private static final int discoveryPeriodMillis = discoveryPeriodSeconds * 1000;
    public static DeviceContact myDeviceContact;
    private MainActivity mainActivity;
    private static ArrayAdapter<DeviceContact> mConnectedDevicesArrayAdapter;
    public static BluetoothService mBluetoothService;
    public static ConversationsManager mConversationManager;
    public static RoutingTable<BluetoothService.ConnectedThread> mRoutingTable;
    private static myBroadcastReceiver mReceiver;
    public static final String UUID_PREFIX = "ffffffff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        // Request bluetooth permissions
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number

        // If BT is not on, request that it be enabled.
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            // TODO: static member?
            final int REQUEST_ENABLE_BT = 3;

            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            Init();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            Init();
        } else {
            finishAndRemoveTask();
        }
    }

    private void Init(){
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ensureDiscoverable();
            }
        }, 0, discoveryPeriodMillis);

        // Find and set up the ListView for newly discovered devices
        mConnectedDevicesArrayAdapter = new ArrayAdapter<>(
                this, R.layout.device_name);
        ListView newDevicesListView = findViewById(R.id.conversations);
        newDevicesListView.setAdapter(mConnectedDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_UUID);

        mBluetoothService = new BluetoothService();
        mReceiver = new myBroadcastReceiver(mBluetoothService);
        this.registerReceiver(mReceiver, filter);

        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        mReceiver.mDeviceList.addAll(pairedDevices);
        mReceiver.tryFetchNextDevice();

        new Timer().schedule(new TimerTask() {
            final String TIMER_TAG = "TIMER";
            @Override
            public void run() {
                try {
                    // Waiting for devices list to be empty.
                    while (mReceiver.mIsFetchingUuids) {
                        Thread.sleep(100);
                    }
                    Log.d(TIMER_TAG, "Acquiring all locks...");
                    mBluetoothService.mSemaphore.acquire(BluetoothService.MAX_CONNECTED_THREADS);
                }
                catch (Exception e) {
                    // ignore
                }
                Log.d(TIMER_TAG, "All locks acquired. Discovering...");
                mBluetoothService.mAdapter.startDiscovery();
            }
        }, 1000, 30 * 1000);

        mConversationManager = new ConversationsManager();
        mRoutingTable = new RoutingTable<>();

        getMessagesFromHistory();
        // the address will update on first handshake
        myDeviceContact = new DeviceContact("00-00-00-00-00-00",
                mBluetoothService.mAdapter.getName());
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

    public void getMessagesFromHistory(){
        File dir = getDir("Conversations", MODE_PRIVATE);
        for (File contact: dir.listFiles()) {
            mConversationManager.addMessagesFromHistory(contact);
        }
    }

    public void writeHistoryToFiles(){
        // Create directory if missing
        File dir = getDir("Conversations", MODE_PRIVATE);
        mConversationManager.writeMessagesToFiles(dir);
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * This is the maximal possible value.
     * */
    private void ensureDiscoverable() {
        while (BluetoothAdapter.getDefaultAdapter().getScanMode() == BluetoothAdapter.SCAN_MODE_NONE){
            try {
                Thread.sleep(1000);
            } catch(Exception e){
                // ignore
            }
        }

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
            Intent intent = new Intent(mainActivity, ChatActivity.class);
            intent.putExtra("clusterchat.deviceAddress", address);
            startActivity(intent);
        }
    };

    public static DeviceContact findDeviceContact(String deviceId) {
        int position = mConnectedDevicesArrayAdapter.getPosition(new DeviceContact(deviceId));
        if (position == -1) {
            Log.e(TAG, "Cant find device contact with id " + deviceId);
            return null;
        }

        return mConnectedDevicesArrayAdapter.getItem(position);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mReceiver);
        Log.d(TAG, "Writing History to files ");
        writeHistoryToFiles();
    }
}
