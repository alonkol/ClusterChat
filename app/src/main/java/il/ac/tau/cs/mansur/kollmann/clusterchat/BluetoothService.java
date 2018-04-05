package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BluetoothService {

    // Debugging
    private static final String TAG = "BluetoothService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Constants
    private static final long CONNECT_TIMEOUT_MS = 50000;

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
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
        mSecureAcceptThread = new AcceptThread(true);
        mSecureAcceptThread.start();
        mInsecureAcceptThread = new AcceptThread(false);
        mInsecureAcceptThread.start();

        // do discovery every 10 seconds
        Timer t = new Timer();
        t.schedule(new discoverTask(), 1000L, 10000L);

        // Start thread to kill old connect attempts
        mKillOldConnectAttemptsThread = new KillOldConnectAttemptsThread();
        mKillOldConnectAttemptsThread.start();
    }

    public ConnectedThread getConnectedThread(String device_id) {
        return mConnectedThreadsMap.get(device_id);
    }

    public void connect(BluetoothDevice device){
        ConnectThread thread = new ConnectThread(device, true);
        thread.start();
        mConnectThreads.add(thread);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread thread = new ConnectedThread(socket, socketType);
        mConnectedThreadsMap.put(device.getAddress(), thread);
        thread.start();

        // TODO: Send the name of the connected device back to the UI Activity
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
        private String mSocketType;

        AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            while (true) {
                socket = null;

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
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
                    connected(socket, socket.getRemoteDevice(),
                            mSocketType);
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
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
        private String mSocketType;
        private long mStartTime;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            mStartTime = System.currentTimeMillis();
            setName("ConnectThread" + mSocketType);

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
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
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

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
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
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // TODO: Set action as global const
                    final int MESSAGE_READ = 0;
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        // TODO: implement
        public void write(byte[] buffer) {

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
