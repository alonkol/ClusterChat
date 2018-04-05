package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BluetoothService {

    // Debugging
    private static final String TAG = "BluetoothService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    // Constants
    private static final long CONNECT_TIMEOUT_MS = 10000;

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private final Handler mUiConnectHandler;
    private final MessageHandler mMessageHandler;
    private AcceptThread mSecureAcceptThread;
    private HashMap<String, ConnectedThread> mConnectedThreadsMap;
    private ArrayList<ConnectThread> mConnectThreads;
    private KillOldConnectAttemptsThread mKillOldConnectAttemptsThread;

        /**
         * Constructor. Prepares a new BluetoothChat session.
         *
         * @param handler A Handler to send messages back to the UI Activity
         */
    public BluetoothService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // TODO: check (mBluetoothAdapter == null) - Bluetooth not available

        mHandler = handler;
        mUiConnectHandler = new Handler();
        mMessageHandler = new MessageHandler();
        mConnectedThreadsMap = new HashMap<>();
        mConnectThreads = new ArrayList<>();

        start();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Start the thread to listen on a BluetoothServerSocket
        mSecureAcceptThread = new AcceptThread();
        mSecureAcceptThread.start();

        // do discovery every 10 seconds
        Timer t = new Timer();
        t.schedule(new discoverTask(), 1000L, 60*1000);

        // Start thread to kill old connect attempts
        mKillOldConnectAttemptsThread = new KillOldConnectAttemptsThread();
        mKillOldConnectAttemptsThread.start();
    }

    public ConnectedThread getConnectedThread(String device_id) {
        return mConnectedThreadsMap.get(device_id);
    }

    public void connect(BluetoothDevice device){
        ConnectThread thread = new ConnectThread(device);
        thread.start();
        mConnectThreads.add(thread);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {
        Log.d(TAG, "connected");

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread thread = new ConnectedThread(socket, device.getAddress());
        mConnectedThreadsMap.put(device.getAddress(), thread);
        thread.start();
    }

    // TODO: Implement
    private void connectionFailed() {

    }

    // TODO: Implement
    private void connectionLost() {

    }

    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                // Create random guid with 'ffffffff' prefix
                UUID uuid = UUID.fromString("ffffffff" + UUID.randomUUID().toString().substring(8));
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, uuid);
            } catch (IOException e) {
                Log.e(TAG, "Socket listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            BluetoothSocket socket = null;

            while (true) {
                socket = null;

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    // if already connected, Terminate new socket.
                    if (mConnectedThreadsMap.containsKey(socket.getRemoteDevice().getAddress())) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Could not close unwanted socket", e);
                        }
                        continue;
                    }

                    // Normal situation - start the connected thread.
                    connected(socket, socket.getRemoteDevice());
                }
            }
            Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            Log.d(TAG, "Socket cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private long mStartTime;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                // mmDevice.fetchUuidsWithSdp();
                ParcelUuid[] uuids = mmDevice.getUuids();
                // TODO: make sure sorted
                UUID uuid = uuids[uuids.length - 1].getUuid();
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            mStartTime = System.currentTimeMillis();
            setName("ConnectThread-" + mmDevice.getName());

            // if already connected, return.
            if (mConnectedThreadsMap.containsKey(mmDevice.getAddress())) {
                Log.i(TAG, "Already connected to device: " + mmDevice.getName());
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close unwanted socket", e);
                }
                return;
            }

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);

            // Update UI
            mUiConnectHandler.post(new Runnable() {
                public void run() {
                    MainActivity.addDeviceToUi(mmDevice);
                }
            });
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

        public long getStartTime(){
            return mStartTime;
        }

    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final String deviceAddress;

        public ConnectedThread(BluetoothSocket socket, String deviceAddress) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            this.deviceAddress = deviceAddress;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;


            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            setName("ConnectedThread-" + mmSocket.getRemoteDevice().getName());
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    mMessageHandler.obtainMessage(MessageHandler.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void connectionLost() {
            mConnectedThreadsMap.remove(deviceAddress);
        }

        public void write(byte[] buffer) {
            this.write(buffer);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    class discoverTask extends TimerTask{

        @Override
        public void run() {
            Log.d(TAG, "discovering...");
            mAdapter.cancelDiscovery();
            mAdapter.startDiscovery();
        }
    }

    private class KillOldConnectAttemptsThread extends Thread {
        public void run(){
            setName("OldConnectThreadKiller");

            while (true) {
                for (int i = mConnectThreads.size() - 1; i >= 0; i--){
                    ConnectThread thread = mConnectThreads.get(i);
                    if (thread.getStartTime() + CONNECT_TIMEOUT_MS < System.currentTimeMillis()){
                        thread.cancel();
                        mConnectThreads.remove(i);
                    }
                }

                try {
                    Thread.sleep(5000);
                } catch (Exception e){
                    Log.d(TAG, "Exception: " + e.getMessage());
                }
            }
        }
    }

}
