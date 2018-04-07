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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class BluetoothService {

    // Debugging
    private static final String TAG = "BluetoothService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Constants
    private static final long CONNECT_TIMEOUT_MS = 30 * 1000;

    // Member fields
    private final BluetoothAdapter mAdapter;
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
        MainActivity.myDeviceName = mAdapter.getName();
        MainActivity.myDeviceId = mAdapter.getAddress();

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

        // do discovery periodically
        new Timer().schedule(new discoverTask(), 5 * 1000, 30 * 1000);

        // Start thread to kill old connect attempts
        new KillOldConnectAttemptsThread().start();
    }

    synchronized void connect(BluetoothDevice device){
        DeviceContact contact = new DeviceContact(device);
        // if already connected to device, return.
        if (mConnectedThreads.containsKey(contact)) {
            Log.i(TAG, "Already connected to " + device.getName());
            return;
        }

        // If already trying to connect, return.
        if (mConnectThreads.containsKey(contact)) {
            Log.i(TAG, "Already attempting to connect " + device.getName());
            return;
        }

        ConnectThread thread = new ConnectThread(device);
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
        MainActivity.mRoutingTable.addDeviceToTable(contact, thread, true);
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
            BluetoothSocket socket;

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
                    if (mConnectedThreads.containsKey(new DeviceContact(socket.getRemoteDevice()))) {
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
                    Log.d(TAG, "Failed to get UUID for " + mmDevice.getName() + "." +
                            "Trying again...");
                    mmDevice.fetchUuidsWithSdp();
                    Thread.sleep(5000);
                    uuids = mmDevice.getUuids();
                    uuid = findUuid(uuids);
                }

                // Swapping bytes to handle Android bug.
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(byteSwappedUuid(uuid));
            } catch (InterruptedException e) {
                Log.d(TAG, "Failed to get UUID, killed thread for " + mmDevice.getName());
                return;
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
                return;
            }

            // if already connected, return.
            if (mConnectedThreads.containsKey(new DeviceContact(mmDevice))) {
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

        long getStartTime(){
            return mStartTime;
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

            for (ParcelUuid uuid : uuids) {
                if (uuid.getUuid().toString().endsWith("ffffffff")) {
                    return uuid.getUuid();
                }
            }
            return null;
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
        private final DeviceContact contact;

        ConnectedThread(BluetoothSocket socket, DeviceContact contact) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            this.contact = contact;
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

                    mMessageHandler.obtainMessage(myMessageHandler.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void connectionLost() {
            ConnectedThread t = mConnectedThreads.get(contact);
            MainActivity.mRoutingTable.removeThreadFromTable(t);
            mConnectedThreads.remove(contact);

            // Update UI
            mUiConnectHandler.post(new Runnable() {
                public void run() {
                    MainActivity.removeDeviceFromUi(contact);
                }
            });
        }

        void write(byte[] buffer) throws IOException {
            mmOutStream.write(buffer);
            // Share the sent message back to the UI Activity
            mMessageHandler.obtainMessage(myMessageHandler.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget();
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
                Iterator<HashMap.Entry<DeviceContact, ConnectThread>> it = mConnectThreads.entrySet().iterator();
                while (it.hasNext()) {
                    HashMap.Entry<DeviceContact, ConnectThread> entry = it.next();
                    ConnectThread thread = entry.getValue();
                    Log.d(TAG, "Killing Thread - " + entry.getKey());

                    if (thread != null && thread.getStartTime() + CONNECT_TIMEOUT_MS < System.currentTimeMillis()){
                        thread.interrupt();
                        it.remove();
                    }
                }

                try {
                    Thread.sleep(15 * 1000);
                } catch (Exception e){
                    Log.d(TAG, "Exception: " + e.getMessage());
                }
            }
        }
    }

}
