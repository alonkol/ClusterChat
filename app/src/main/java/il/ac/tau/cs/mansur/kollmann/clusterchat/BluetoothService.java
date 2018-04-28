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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

class BluetoothService {

    // Debugging
    private static final String TAG = "BluetoothService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Member fields
    final BluetoothAdapter mAdapter;

    static final int MAX_CONNECTED_THREADS = 7;
    Semaphore mSemaphore = new Semaphore(MAX_CONNECTED_THREADS);
    private final Handler mUiConnectHandler;
    private final myMessageHandler mMessageHandler;
    private final HashMap<DeviceContact, ConnectedThread> mConnectedThreads;
    private final ConcurrentHashMap<DeviceContact, ConnectThread> mConnectThreads;

    /**
         * Constructor. Prepares a new BluetoothChat session.
         */
    BluetoothService() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // TODO: check (mBluetoothAdapter == null) - Bluetooth not available
        mUiConnectHandler = new Handler();
        mMessageHandler = new myMessageHandler();
        mConnectedThreads = new HashMap<>();
        mConnectThreads = new ConcurrentHashMap<>();

        start();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    private synchronized void start() {
        Log.d(TAG, "start");

        // Start the thread to listen on a BluetoothServerSocket
        new AcceptThread().start();
    }

    boolean checkIfDeviceConnected(BluetoothDevice device){
        DeviceContact contact = new DeviceContact(device);
        return checkIfContactConnected(contact);
    }

    boolean checkIfContactConnected(DeviceContact contact){
        if (mConnectedThreads.containsKey(contact)) {
            Log.i(TAG, "Already connected to " + contact.getDeviceName() + '/' +
                    contact.getDeviceId());
            return true;
        }
        return false;
    }

    public ConnectedThread getConnectedThread(DeviceContact dc){
        return mConnectedThreads.get(dc);
    }

    synchronized void connect(BluetoothDevice device, UUID uuid){
        DeviceContact contact = new DeviceContact(device);
        // if already connected to device, return.
        if (checkIfContactConnected(contact))
                return;

        // If already trying to connect, return.
        if (mConnectThreads.containsKey(contact)) {
            Log.i(TAG, "Already attempting to connect " + device.getName());
            return;
        }

        ConnectThread thread = new ConnectThread(device, uuid);
        mConnectThreads.put(contact, thread);
        thread.start();
    }

    private synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device) {
        Log.d(TAG, "connected");

        // Update UI
        mUiConnectHandler.post(new Runnable() {
            public void run() {
                MainActivity.addDeviceToUi(device);
            }
        });

        // Start the thread to manage the connection and perform transmissions
        final DeviceContact contact = new DeviceContact(device);
        ConnectedThread thread = new ConnectedThread(socket, contact);
        mConnectedThreads.put(contact, thread);
        MainActivity.mRoutingTable.addDeviceToTable(contact, contact, true);
        thread.start();
    }

    private class AcceptThread extends Thread {
        public void run() {
            Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            BluetoothServerSocket server_socket;
            BluetoothSocket socket;

            while (true) {
                socket = null;

                // Create a new listening server socket
                try {
                    // Create random guid with constant prefix
                    UUID uuid = getRandomUuid();
                    Log.d(TAG, "Listening using uuid " + uuid.toString());
                    server_socket = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, uuid);
                } catch (IOException e) {
                    Log.e(TAG, "Socket listen() failed", e);
                    continue;
                }

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    Log.d(TAG, "Listening to the next connection...");
                    socket = server_socket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    // if already connected, Terminate new socket.
                    if (mConnectedThreads.containsKey(new DeviceContact(socket.getRemoteDevice()))) {
                        try {
                            Log.d(TAG, "Accept thread established connection with: " +
                                    socket.getRemoteDevice().getName() + " but this exists so continue. " +
                                    "Don't close socket since it will close the original.");
                            // socket.close();
                            server_socket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Could not close unwanted socket", e);
                        }
                        continue;
                    }

                    // Normal situation - start the connected thread.
                    Log.d(TAG, "Accept thread established connection with: " +
                            socket.getRemoteDevice().getName());
                    connected(socket, socket.getRemoteDevice());
                }

                try {
                    Log.d(TAG, "Connected, closing Server Socket");
                    server_socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close server socket", e);
                }
            }
        }

        private UUID getRandomUuid(){
            return UUID.fromString(MainActivity.UUID_PREFIX +
                    UUID.randomUUID().toString().substring(MainActivity.UUID_PREFIX.length()));
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
        private final DeviceContact mmContact;
        private long mStartTime;
        private UUID mUuid;

        ConnectThread(BluetoothDevice device, UUID uuid) {
            mmDevice = device;
            mUuid = uuid;
            mmContact = new DeviceContact(mmDevice);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread - " + mmDevice.getName());
            mStartTime = System.currentTimeMillis();
            setName("ConnectThread-" + mmDevice.getName());

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(mUuid);
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
                mConnectThreads.remove(mmContact);
                return;
            }

            // if already connected, return.
            if (mConnectedThreads.containsKey(mmContact)) {
                Log.i(TAG, "Already connected to device: " + mmDevice.getName());
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close unwanted socket", e);
                }
                mConnectThreads.remove(mmContact);
                return;
            }

            if (mmSocket == null){
                mConnectThreads.remove(mmContact);
                return;
            }

            // Make a connection to the BluetoothSocket
            try {
                Log.d(TAG, "Acquiring connection lock");
                mSemaphore.acquire();
                Log.d(TAG, "Lock acquired, connecting...");
                mmSocket.connect();
            } catch (InterruptedException e3) {
                Log.e(TAG, "Connect/Acquire interrupted for " + mmDevice.getName(), e3);
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + mmDevice.getName(), e);

                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }

                return;
            } finally {
                mSemaphore.release();
                mConnectThreads.remove(mmContact);
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        long getStartTime(){
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
        private final DeviceContact mmContact;

        ConnectedThread(BluetoothSocket socket, DeviceContact contact) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            mmContact = contact;
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
            boolean sendHS = sendHandshake();
            if (!sendHS){
                return;
            }
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    mMessageHandler.obtainMessage(
                            myMessageHandler.MESSAGE_READ, bytes, -1,
                            buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void connectionLost() {
            MainActivity.mRoutingTable.removeLinkFromTable(mmContact);
            mConnectedThreads.remove(mmContact);

            // Update UI
            mUiConnectHandler.post(new Runnable() {
                public void run() {
                    MainActivity.removeDeviceFromUi(mmContact);
                }
            });
        }

        void write(byte[] buffer, int messageType) throws IOException {
            mmOutStream.write(buffer);
            // Share the sent message back to the UI Activity
            mMessageHandler.obtainMessage(
                    messageType, -1, -1,
                    buffer).sendToTarget();
        }

        boolean sendHandshake(){
            MessageBundle newMessage = new MessageBundle(
                    "", MessageTypes.HS, MainActivity.myDeviceContact, mmContact);
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = newMessage.toJson().getBytes();
            try {
                write(send, myMessageHandler.MESSAGE_OUT_HANDSHAKE);
                Log.d(TAG, "Sent HS to device: " + mmContact.getDeviceId());
            } catch (IOException e){
                Log.e(TAG, "Can't send HS message", e);
                connectionLost();
                return false;
            }
            return true;
        }

    }

}
