package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.BluetoothThread;
import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.ConnectThread;
import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.ConnectedThread;
import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.MainAcceptThread;

public class BluetoothService {

    public static final UUID MAIN_ACCEPT_UUID = UUID.fromString("4fc2e17e-4ae5-11e8-842f-0ed5f89f718b");

    // Debugging
    private static final String TAG = "BluetoothService";
    private static final String SOCKET_TAG = "Sockets";
    // Member fields
    public final BluetoothAdapter mAdapter;

    static final int MAX_CONNECTED_THREADS = 7;
    public Semaphore mSemaphore = new Semaphore(MAX_CONNECTED_THREADS);
    public myMessageHandler mMessageHandler;
    private BluetoothThread mainAcceptThread;
    public final HashMap<DeviceContact, ConnectedThread> mConnectedThreads;
    public ConcurrentHashMap<DeviceContact, ConnectThread> mConnectThreads = new ConcurrentHashMap<>();

    /**
     * Constructor. Prepares a new BluetoothChat session.
     */
    BluetoothService(MainActivity mainActivity) {
        mMessageHandler = new myMessageHandler(mainActivity);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mConnectedThreads = new HashMap<>();

        start();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    private synchronized void start() {
        Log.d(TAG, "start");

        // Start the thread to listen on a BluetoothServerSocket
        if (MainActivity.LISTENER) {
            mainAcceptThread = new MainAcceptThread(this);
            mainAcceptThread.start();
        }
    }

    boolean checkIfDeviceConnected(BluetoothDevice device){
        DeviceContact contact = new DeviceContact(device);
        return checkIfContactConnected(contact);
    }

    private boolean checkIfContactConnected(DeviceContact contact){
        if (mConnectedThreads.containsKey(contact)) {
            Log.i(TAG, "Already connected to " + contact.getShortStr());
            return true;
        }
        return false;
    }

    ConnectedThread getConnectedThread(DeviceContact dc){
        return mConnectedThreads.get(dc);
    }

    synchronized void connect(BluetoothDevice device){
        DeviceContact contact = new DeviceContact(device);
        // if already connected to device, return.
        if (checkIfContactConnected(contact))
                return;

        // If already trying to connect, return.
        if (mConnectThreads.containsKey(contact)) {
            Log.i(TAG, "Already attempting to connect " + device.getName());
            return;
        }

        ConnectThread thread = new ConnectThread(this, device);
        mConnectThreads.put(contact, thread);
        thread.start();
    }

    public synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device) {
        // Start the thread to manage the connection and perform transmissions
        final DeviceContact contact = new DeviceContact(device);
        ConnectedThread thread = new ConnectedThread(this, socket, contact);
        boolean result = thread.initStreams();
        if (result) {
            mConnectedThreads.put(contact, thread);
            MainActivity.mRoutingTable.addDeviceToTable(contact, contact, 1, false);
            Log.d(SOCKET_TAG, "Connection with device " + device.getName() + " and socket " + socket);
            thread.start();
        }
    }

    public void write(OutputStream outStream, byte[] buffer) throws IOException {
        outStream.write(buffer);
        // Share the sent message back to the UI Activity
        mMessageHandler.obtainMessage(
                myMessageHandler.MESSAGE_OUT, -1, -1,
                buffer).sendToTarget();
    }


}
