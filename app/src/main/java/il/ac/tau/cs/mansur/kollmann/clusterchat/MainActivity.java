package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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
    public static RoutingTable mRoutingTable;
    ArrayList<BluetoothDevice> mDeviceList = new ArrayList<BluetoothDevice>();


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
        mConnectedDevicesArrayAdapter = new ArrayAdapter<>(
                this, R.layout.device_name);
        ListView newDevicesListView = findViewById(R.id.conversations);
        newDevicesListView.setAdapter(mConnectedDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_UUID);
        this.registerReceiver(mReceiver, filter1);
        this.registerReceiver(mReceiver, filter2);
        this.registerReceiver(mReceiver, filter3);


        mBluetoothService = new BluetoothService();
        mConversationManager = new ConversationsManager();
        mRoutingTable = new RoutingTable();

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
        if (BluetoothAdapter.getDefaultAdapter().getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoveryPeriodSeconds);
            startActivity(discoverableIntent);
        } else {
            // retry in 3 sec.
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    ensureDiscoverable();
                }
            }, 3000);
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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private UUID findUuid(Parcelable[] uuids){
            if (uuids == null) {
                return null;
            }

            for (Parcelable p : uuids) {
                UUID uuid = ((ParcelUuid) p).getUuid();

                // Handle Android bug swap bug
                if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1
                        && android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
                    uuid = byteSwappedUuid(uuid);
                }

                if (uuid.toString().startsWith("ffffffff")) {
                    return uuid;
                }
            }
            return null;
        }


        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                Log.d(TAG, "Discovery found a device!");
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getName() != null){
                    if (!mBluetoothService.checkIfDeviceConnected(device)){
                        mDeviceList.add(device);
                        Log.d(TAG, "Found new device, adding to waiting list " + device.getAddress());
                    }
                }
            }else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Discovery finished starting to handle waiting list");

                if (!mDeviceList.isEmpty()) {
                    BluetoothDevice device = mDeviceList.remove(0);
                    Log.d(TAG, "Fetching from device " + device.getAddress());
                    boolean result = device.fetchUuidsWithSdp();
                }else {
                    // Start discovery again
                    mBluetoothService.mAdapter.startDiscovery();
                    Log.d(TAG, "Waiting List empty, Discovering...");
                }
            } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                // This is when we can be assured that fetchUuidsWithSdp has completed.
                // So get the uuids and call fetchUuidsWithSdp on another device in list
                Log.d(TAG, "Done fetching ");

                BluetoothDevice deviceExtra = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                Log.d(TAG,"DeviceExtra address - " + deviceExtra.getAddress());
                if (uuidExtra != null) {
                    UUID uuid = findUuid(uuidExtra);
                    if (uuid == null){
                        Log.d(TAG, "No matching uuid found for device " +
                                deviceExtra.getAddress());
                    }
                    else {
                        mBluetoothService.connect(deviceExtra, uuid);
                    }
                } else {
                    Log.d(TAG,"uuidExtra is still null");
                }
                if (!mDeviceList.isEmpty()) {
                    BluetoothDevice device = mDeviceList.remove(0);
                    Log.d(TAG, "Fetching from device " + device.getAddress());
                    boolean result = device.fetchUuidsWithSdp();
                    // Check if good and if so connect
                } else {
                    // Start discovery again
                    mBluetoothService.mAdapter.startDiscovery();
                    Log.d(TAG, "Waiting List empty, Discovering...");
                }
            }
        }

        private UUID byteSwappedUuid(UUID toSwap) {
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer
                    .putLong(toSwap.getLeastSignificantBits())
                    .putLong(toSwap.getMostSignificantBits());
            buffer.rewind();
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return new UUID(buffer.getLong(), buffer.getLong());
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
        Log.d(TAG, "Writing History to files ");
        writeHistoryToFiles();
    }
}
