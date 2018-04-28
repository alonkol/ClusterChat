package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

class BluetoothService {

    private static final UUID MAIN_ACCEPT_UUID = UUID.fromString("4fc2e17e-4ae5-11e8-842f-0ed5f89f718b");

    // Debugging
    private static final String TAG = "BluetoothService";

    private Context mContext;

    // Member fields
    final BluetoothAdapter mAdapter;

    static final int MAX_CONNECTED_THREADS = 7;
    Semaphore mSemaphore = new Semaphore(MAX_CONNECTED_THREADS);
    private final Handler mUiConnectHandler;
    private static final myMessageHandler mMessageHandler = new myMessageHandler();
    private final HashMap<DeviceContact, ConnectedThread> mConnectedThreads;
    static ConcurrentHashMap<DeviceContact, ConnectThread> mConnectThreads = new ConcurrentHashMap<>();
    private final MediaPlayer mMediaPlayer;

    /**
         * Constructor. Prepares a new BluetoothChat session.
         */
    BluetoothService(Context context) {
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // TODO: check (mBluetoothAdapter == null) - Bluetooth not available
        mUiConnectHandler = new Handler();
        mConnectedThreads = new HashMap<>();
        mMediaPlayer = MediaPlayer.create(mContext, R.raw.light);

        start();
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    private synchronized void start() {
        Log.d(TAG, "start");

        // Start the thread to listen on a BluetoothServerSocket
        new MainAcceptThread().start();
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

        ConnectThread thread = new ConnectThread(device);
        mConnectThreads.put(contact, thread);
        thread.start();
    }

    private synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device) {
        Log.d(TAG, "connected");

        // Play sound
        PlaySound();

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

    private class MainAcceptThread extends Thread {
        public void run() {
            Log.d(TAG, "BEGIN MainAcceptThread" + this);
            setName("MainAcceptThread");

            BluetoothServerSocket server_socket;
            BluetoothSocket socket;

            try {
                server_socket = mAdapter.listenUsingRfcommWithServiceRecord("Main" + mAdapter.getName(), MAIN_ACCEPT_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket listen() failed", e);
                return;
            }

            while (true) {
                socket = null;

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
                            socket.close();
                            server_socket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Could not close unwanted socket", e);
                        }
                        continue;
                    }

                    // Normal situation - start the connected thread.
                    Log.d(TAG, "Accept thread established connection with: " +
                            socket.getRemoteDevice().getName());

                    new DedicatedAcceptThread(socket, UUID.randomUUID()).start();
                }
            }
        }

    }

    private class DedicatedAcceptThread extends Thread {
        final UUID mmUuid;
        BluetoothSocket mmInitSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final DeviceContact mmContact;

        DedicatedAcceptThread(BluetoothSocket socket, UUID uuid){
            mmInitSocket = socket;
            mmUuid = uuid;
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
            mmContact = new DeviceContact(socket.getRemoteDevice());
        }

        public void run() {
            Log.d(TAG, "BEGIN DedicatedAcceptThread" + mmUuid);
            setName("DedicatedAcceptThread");

            // Send dedicated UUID
            MessageBundle newMessage = new MessageBundle(
                    "", MessageTypes.UUID, MainActivity.myDeviceContact, mmContact, mmUuid);
            byte[] send = newMessage.toJson().getBytes();
            try {
                write(mmOutStream, send, myMessageHandler.MESSAGE_OUT);
                Log.d(TAG, "Sent dedicated uuid to device: " + mmContact.getDeviceId());
            } catch (IOException e){
                Log.e(TAG, "Can't send UUID message", e);
                return;
            }

            BluetoothServerSocket server_socket;
            BluetoothSocket socket;

            // Create a new listening server socket
            try {
                server_socket = mAdapter.listenUsingRfcommWithServiceRecord("Dedicated"+mmContact.getDeviceName(), mmUuid);

                Log.d(TAG, "Listening to the next connection...");
                socket = server_socket.accept();
            } catch (Exception e) {
                Log.e(TAG, "Dedicated socket creation failed", e);
                return;
            }

            // If a connection was accepted
            if (socket != null) {
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


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    class ConnectThread extends Thread {
        private BluetoothSocket mmInitSocket;
        private final BluetoothDevice mmDevice;
        private final DeviceContact mmContact;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            mmContact = new DeviceContact(mmDevice);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread - " + mmDevice.getName());
            setName("ConnectThread-" + mmDevice.getName());

            // if already connected, return.
            if (mConnectedThreads.containsKey(mmContact)) {
                Log.i(TAG, "Already connected to device: " + mmDevice.getName());
                mConnectThreads.remove(mmContact);
                return;
            }

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(TAG, "Acquiring connection lock");
                mSemaphore.acquire();
                Log.d(TAG, "Lock acquired, connecting...");
                mmInitSocket = mmDevice.createRfcommSocketToServiceRecord(MAIN_ACCEPT_UUID);
            } catch (InterruptedException e3) {
                Log.e(TAG, "Connect/Acquire interrupted for " + mmDevice.getName(), e3);
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
                finishConnect();
                return;
            }

            if (mmInitSocket == null){
                mConnectThreads.remove(mmContact);
                return;
            }

            // Make a connection to the BluetoothSocket
            try {
                mmInitSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect init thread to " + mmDevice.getName(), e);

                // Close the socket
                try {
                    mmInitSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }

                finishConnect();
                return;
            }

            getUuidAndConnect();
        }

        void getUuidAndConnect() {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmInitSocket.getInputStream();
                tmpOut = mmInitSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
                finishConnect();
                return;
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            boolean sendHS = sendHandshake();
            if (!sendHS){
                finishConnect();
                return;
            }

            byte[] buffer = new byte[1024];
            int bytes;

            try {
                // Read UUID from inputStream
                bytes = mmInStream.read(buffer);
                mMessageHandler.obtainMessage(
                        myMessageHandler.MESSAGE_IN, bytes, -1,
                        buffer).sendToTarget();

            } catch (Exception e) {
                Log.e(TAG, "disconnected", e);
            } finally {
                finishConnect();
            }
        }

        private boolean sendHandshake(){
            MessageBundle newMessage = new MessageBundle(
                    "", MessageTypes.HS, MainActivity.myDeviceContact, mmContact);
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = newMessage.toJson().getBytes();
            try {
                write(mmOutStream, send, myMessageHandler.MESSAGE_OUT);
                Log.d(TAG, "Sent HS to device: " + mmContact.getDeviceId());
            } catch (IOException e){
                Log.e(TAG, "Can't send HS message", e);
                return false;
            }
            return true;
        }


        void setUuid(UUID uuid){
            // Make a connection to the BluetoothSocket
            BluetoothSocket socket;

            try {
                socket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
                finishConnect();
                return;
            }

            try {
                socket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + mmDevice.getName(), e);

                // Close the socket
                try {
                    socket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }

                return;
            } finally {
                finishConnect();
            }

            // Start the connected thread
            connected(socket, mmDevice);
        }

        void finishConnect() {
            mConnectThreads.remove(mmContact);
            mSemaphore.release();
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

        void write(byte[] buffer, int messageType) throws IOException {
            BluetoothService.write(mmOutStream, buffer, messageType);
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
                    mMessageHandler.obtainMessage(
                            myMessageHandler.MESSAGE_OUT, bytes, -1,
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
    }

    static void write(OutputStream outStream, byte[] buffer, int messageType) throws IOException {
        outStream.write(buffer);
        // Share the sent message back to the UI Activity
        mMessageHandler.obtainMessage(
                messageType, -1, -1,
                buffer).sendToTarget();
    }

    private void PlaySound(){
        try {
            mMediaPlayer.start();
        } catch (Exception e) {
            // ignore
        }
    }

}
