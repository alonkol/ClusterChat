package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final long CONNECT_TIMEOUT_MS = 30 * 1000;

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private final Handler mUiConnectHandler;
    private final MessageHandler mMessageHandler;
    private AcceptThread mSecureAcceptThread;
    private HashMap<String, ConnectedThread> mConnectedThreadsMap;
    private HashSet<String> mConnectionAttempts;
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
        MainActivity.myDeviceName = mAdapter.getName();
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

        // do discovery periodically
        Timer t = new Timer();
        t.schedule(new discoverTask(), 5 * 1000, 60 * 1000);

        // Start thread to kill old connect attempts
        mKillOldConnectAttemptsThread = new KillOldConnectAttemptsThread();
        mKillOldConnectAttemptsThread.start();
    }

    public ConnectedThread getConnectedThread(String device_id) {
        return mConnectedThreadsMap.get(device_id);
    }

    public void connect(final BluetoothDevice device){
        // if already connected to device, return.
        if (mConnectedThreadsMap.containsKey(device.getAddress())) {
            Log.i(TAG, "Already connected to " + device.getName());
            return;
        }

        // If already tried to connect recently, return.
        if (mConnectionAttempts.contains(device.getAddress())) {
            Log.i(TAG, "Already attempted to connect to " + device.getName());
            return;
        }
        mConnectionAttempts.add(device.getAddress());

        // Allow retry in 5 seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                mConnectionAttempts.remove(device.getAddress());
            }
        }, 5 * 1000);

        ConnectThread thread = new ConnectThread(device);
        thread.start();
        mConnectThreads.add(thread);
    }

    public synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device) {
        Log.d(TAG, "connected");

        // Update UI
        mUiConnectHandler.post(new Runnable() {
            public void run() {
                MainActivity.addDeviceToUi(device);
            }
        });

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread thread = new ConnectedThread(socket, device.getAddress());
        mConnectedThreadsMap.put(device.getAddress(), thread);
        thread.start();
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
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private long mStartTime;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            mConnectionAttempts.add(device.getAddress());
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

        private UUID findUuid(ParcelUuid[] uuids){
            if (uuids == null) {
                return null;
            }

            for (int i = 0; i < uuids.length; i++) {
                if (uuids[i].getUuid().toString().endsWith("ffffffff")){
                    return uuids[i].getUuid();
                }
            }
            return null;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread - " + mmDevice.getName());
            mStartTime = System.currentTimeMillis();
            setName("ConnectThread-" + mmDevice.getName());

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                mmDevice.fetchUuidsWithSdp();
                ParcelUuid[] uuids = mmDevice.getUuids();
                UUID uuid = findUuid(uuids);
                while (uuid == null) {
                    mmDevice.fetchUuidsWithSdp();
                    Thread.sleep(5000);
                    uuids = mmDevice.getUuids();
                    uuid = findUuid(uuids);
                }

                // TODO: when is this swapped and when is not?
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(byteSwappedUuid(uuid));
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
            }

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

            if (mmSocket == null){
                return;
            }

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + mmDevice.getName(), e);

                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                return;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                if (mmSocket != null)
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

        public void write(byte[] buffer) throws IOException {
            mmOutStream.write(buffer);
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
                    if (thread != null && thread.getStartTime() + CONNECT_TIMEOUT_MS < System.currentTimeMillis()){
                        thread.cancel();
                    }
                    mConnectThreads.remove(i);
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
