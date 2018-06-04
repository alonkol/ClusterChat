package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";
    private static final int discoveryPermissionPeriodSeconds = 5 * 60;
    private static final int discoveryPermissionPeriodMillis =
            discoveryPermissionPeriodSeconds * 1000;
    public static DeviceContact myDeviceContact;
    private MainActivity mainActivity;
    public static PackageQueue packageQueue;
    static UsersAdapter mConnectedDevicesArrayAdapter;
    public static BluetoothService mBluetoothService;
    public static ConversationsManager mConversationManager;
    public static RoutingTable mRoutingTable;
    private static myBroadcastReceiver mReceiver;
    public static DeliveryMan mDeliveryMan;
    private static Integer runningMessageID;
    private static final Integer MAXIMUM_MESSAGE_ID = 1000000;
    static final int REQUEST_ENABLE_BT = 3;
    private MediaPlayer mMediaPlayerOnConnect;
    private MediaPlayer mMediaPlayerOnDisconnect;

    // This flag is used to create complex network
    // Full explanation is found under myBroadcastReceiver/tryConnect
    public static final boolean LEVEL_ROUTING_FLAG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;

        mMediaPlayerOnConnect = MediaPlayer.create(this, R.raw.light);
        mMediaPlayerOnDisconnect = MediaPlayer.create(this, R.raw.case_closed);

        // Request bluetooth permissions
        ActivityCompat.requestPermissions(this,new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001); //Any number

        // If BT is not on, request that it be enabled.
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_broadcast, menu);
        // set up broadcast message button
        MenuItem broadcastButton = menu.findItem(R.id.broadcast);
        broadcastButton.setOnMenuItemClickListener(mBroadcastMessageClickListener);
        return true;
    }

    private void Init(){
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ensureDiscoverable();
            }
        }, 0, discoveryPermissionPeriodMillis);

        // Find and set up the ListView for newly discovered devices
        mConnectedDevicesArrayAdapter = new UsersAdapter(this);
        ListView newDevicesListView = findViewById(R.id.conversations);
        View emptyText = findViewById(R.id.empty);
        newDevicesListView.setEmptyView(emptyText);
        newDevicesListView.setAdapter(mConnectedDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        mDeliveryMan = new DeliveryMan();
        mBluetoothService = new BluetoothService(this);
        mReceiver = new myBroadcastReceiver(mBluetoothService, this);
        this.registerReceiver(mReceiver, filter);

        startPeriodicDiscovery();

        mConversationManager = new ConversationsManager();
        mRoutingTable = new RoutingTable(this);

        getMessagesFromHistory();
        // the address will update on first handshake
        myDeviceContact = new DeviceContact("00-00-00-00-00-00",
                mBluetoothService.mAdapter.getName());

        Random rand = new Random();
        runningMessageID = rand.nextInt(MAXIMUM_MESSAGE_ID);

        packageQueue = new PackageQueue();
        startPackageBuilder();

        if (LEVEL_ROUTING_FLAG){
            getSupportActionBar().setTitle("ClusterChat - " + myDeviceContact.getDeviceName());
        }

    }

    public static Integer getNewMessageID(){
        runningMessageID += 1;
        runningMessageID %= MAXIMUM_MESSAGE_ID;
        return runningMessageID;
    }

    void startPackageBuilder(){
        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                new PackageBuilder(mainActivity).start();
            }
        }, 1000);
    }

    void startPeriodicDiscovery() {
        new Timer().schedule(new TimerTask() {
            final String TIMER_TAG = "TIMER";
            @Override
            public void run() {
                try {
                    Log.d(TIMER_TAG, "Acquiring all locks...");
                    mBluetoothService.mSemaphore.acquire(BluetoothService.MAX_CONNECTED_THREADS);
                }
                catch (Exception e) {
                    Log.e(TIMER_TAG, "Discovery init failed. ", e);
                }
                Log.d(TIMER_TAG, "All locks acquired. Discovering...");
                mBluetoothService.mAdapter.startDiscovery();
            }
        }, 10 * 1000, 30 * 1000);
    }

    // Adding device to UI
    public void addDeviceToUi(DeviceContact deviceContact) {
        if (mConnectedDevicesArrayAdapter.getPosition(deviceContact) == -1) {
            mConnectedDevicesArrayAdapter.add(deviceContact);

            // Play sound
            try {
                mMediaPlayerOnConnect.start();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void newMessageNotification(DeviceContact contact, String message) {
        try {
            DeviceContact stored_contact = findDeviceContact(contact.getDeviceId());
            if (stored_contact == null || stored_contact.getUnreadMessages() == 0) {
                return;
            }

            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("clusterchat.deviceAddress", contact.getDeviceId());
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder b = new NotificationCompat.Builder(this);

            b.setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("New message from " + contact.getDeviceName())
                    .setContentText(message)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setContentIntent(contentIntent)
                    .setVibrate(new long[0]);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, b.build());
        } catch (Exception e) {
            Log.w(TAG, "Failed to generate notification", e);
        }
    }

    // Removing device from UI
    public void removeDeviceFromUi(DeviceContact deviceContact) {
        if (mConnectedDevicesArrayAdapter.getPosition(deviceContact) != -1) {
            mConnectedDevicesArrayAdapter.remove(deviceContact);

            // Play disconnection sound
            try {
                mMediaPlayerOnDisconnect.start();
            } catch (Exception e) {
                // ignore
            }
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
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivity(discoverableIntent);
        }
    }

    /**
     * The on-click listener for all devices in the ListViews
     */
    private AdapterView.OnItemClickListener mDeviceClickListener  = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Access user from within the tag
            DeviceContact user = (DeviceContact) view.getTag();
            Intent intent = new Intent(mainActivity, ChatActivity.class);
            intent.putExtra("clusterchat.deviceAddress", user.getDeviceId());
            startActivity(intent);
            view.findViewById(R.id.new_messages).setVisibility(View.INVISIBLE);
            user.clearUnread();
            view.setTag(user);
        }
    };

    private MenuItem.OnMenuItemClickListener mBroadcastMessageClickListener = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            builder.setTitle("Message to broadcast");

            // Set up the input
            final EditText input = new EditText(mainActivity);
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mDeliveryMan.prepareAndBroadcastMessage(input.getText().toString());
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
            return false;
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

    public void writeFileToDevice(byte[] fileBytes, int fileSize, String filename)
            throws IOException {
        String path = Environment.getExternalStorageDirectory() + "/ClusterChat";
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(path,  filename);
        FileOutputStream fileOutputStream = new FileOutputStream(file.getPath());
        fileOutputStream.write(fileBytes, 0, fileSize);
        fileOutputStream.close();
    }
}
