package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

class BluetoothService {

    private static final UUID MAIN_ACCEPT_UUID = UUID.fromString("4fc2e17e-4ae5-11e8-842f-0ed5f89f718b");

    // Debugging
    private static final String TAG = "BluetoothService";
    private static final String SOCKET_TAG = "Sockets";
    // Member fields
    final BluetoothAdapter mAdapter;

    static final int MAX_CONNECTED_THREADS = 7;
    Semaphore mSemaphore = new Semaphore(MAX_CONNECTED_THREADS);
    private static myMessageHandler mMessageHandler;
    private final HashMap<DeviceContact, ConnectedThread> mConnectedThreads;
    static ConcurrentHashMap<DeviceContact, ConnectThread> mConnectThreads = new ConcurrentHashMap<>();

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
        new MainAcceptThread().start();
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

        ConnectThread thread = new ConnectThread(device);
        mConnectThreads.put(contact, thread);
        thread.start();
    }

    private synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device) {
        Log.d(TAG, "connected");

        // Start the thread to manage the connection and perform transmissions
        final DeviceContact contact = new DeviceContact(device);
        ConnectedThread thread = new ConnectedThread(socket, contact);
        mConnectedThreads.put(contact, thread);
        MainActivity.mRoutingTable.addDeviceToTable(contact, contact, 1, false);
        Log.d(SOCKET_TAG,"Connection with device " + device.getName() + "and socket " + socket);
        thread.start();
    }

    private class MainAcceptThread extends BluetoothThread {
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        private BluetoothServerSocket mainListeningSocket;

        public void run() {
            Log.d(TAG, "BEGIN MainAcceptThread" + this);
            setName("MainAcceptThread");

            BluetoothSocket socket;

            try {
                mainListeningSocket = mAdapter.listenUsingRfcommWithServiceRecord(
                        "Main" + mAdapter.getName(), MAIN_ACCEPT_UUID);
                Log.d(SOCKET_TAG, "Opened SUPER: " + mainListeningSocket.toString());

            } catch (Exception e) {
                Log.e(SOCKET_TAG, "Socket listen() failed", e);
                return;
            }

            while (true) {
                socket = null;

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    Log.d(TAG, "Listening to the next connection...");
                    socket = mainListeningSocket.accept();
                    Log.d(SOCKET_TAG, "Accepted socket: " + socket.toString() + " device: " + socket.getRemoteDevice().getName());
                } catch (IOException e) {
                    Log.e(SOCKET_TAG, "main accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    handleAcceptedSocket(socket);
                }
            }
        }

        private void handleAcceptedSocket(BluetoothSocket socket) {
            // if already connected, Terminate new socket.
            if (mConnectedThreads.containsKey(new DeviceContact(socket.getRemoteDevice()))) {
                Log.d(TAG, "Accept thread established connection with: "  +
                        socket.getRemoteDevice().getName() + " but this exists so leave it. ");
//                try {
//                    Log.d(TAG, "Accept thread established connection with: " +
//                            socket.getRemoteDevice().getName() + " but this exists so close. ");
//                    //Log.d(SOCKET_TAG, "closing socket " + socket.toString()  + " device: " + socket.getRemoteDevice().getName());
//                    //socket.close();
//                } catch (IOException e) {
//                    Log.e(SOCKET_TAG, "Could not close unwanted socket " + "device: " + socket.getRemoteDevice().getName(), e);
//                }
                return;
            }

            // Get input and output streams for the socket
            try{
                mmInStream = socket.getInputStream();
                mmOutStream = socket.getOutputStream();
            }catch (IOException e){
                Log.e(TAG, "Could not open streams", e);
                try {
                    Log.d(SOCKET_TAG, "closing socket " + socket.toString() + " device: " + socket.getRemoteDevice().getName());
                    socket.close();
                } catch (IOException e2) {
                    Log.e(SOCKET_TAG, "Could not close unwanted socket, device: " + socket.getRemoteDevice().getName(), e2);
                }
                return;
            }

            // Init parameters for connection
            UUID uuid = UUID.randomUUID();
            DeviceContact contact = new DeviceContact(socket.getRemoteDevice());

            boolean result;
            // Read Handshake from connecting device
            result = readHS();
            if (!result){
                try {
                    Log.d(SOCKET_TAG, "closing socket " + socket.toString() + "device: " + socket.getRemoteDevice().getName());
                    socket.close();
                } catch (IOException e) {
                    Log.e(SOCKET_TAG, "Could not close unwanted socket" + "device: " + socket.getRemoteDevice().getName(), e);
                }
            }

            // Send handshake with new uuid to connect
            result = sendHS(uuid, contact);
            if (!result){
                try {
                    Log.d(SOCKET_TAG, "closing socket " + socket.toString() + "device: " + socket.getRemoteDevice().getName());
                    socket.close();
                } catch (IOException e) {
                    Log.e(SOCKET_TAG, "Could not close unwanted socket" + "device: " + socket.getRemoteDevice().getName(), e);
                }
            }

            // Start a listening socket to listen on the new UUID
            DedicatedAcceptThread thread = new DedicatedAcceptThread(contact, uuid, socket);
            thread.start();
            // will close the original socket in dedicated thread
            try {
                mmInStream.close();
                mmOutStream.close();
            } catch (IOException e) {
                Log.e(SOCKET_TAG, "Could not close unwanted socket device: " + socket.getRemoteDevice().getName(), e);
            }
        }

        public boolean readHS(){
            byte[] buffer;
            byte[] sizeBuffer = new byte[4];
            int bytes;
            int tmpBytes;
            int packetSize;

            try {
                mmInStream.read(sizeBuffer, 0, 4);
                packetSize = ByteBuffer.wrap(sizeBuffer).getInt();
                bytes = 0;
                buffer = new byte[4096];
                while (bytes!=packetSize){
                    tmpBytes = mmInStream.read(buffer, bytes, packetSize-bytes);
                    bytes += tmpBytes;
                }
                mMessageHandler.obtainMessage(
                        myMessageHandler.MESSAGE_IN, bytes, -1,
                        buffer).sendToTarget();

            } catch (IOException e) {
                Log.e(TAG, "AcceptThread - failed to read handshake, disconnected", e);
                return false;
            }

            while(MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00")){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return true;
        }

        public boolean sendHS(UUID uuid, DeviceContact deviceContact){
            // Send HS
            boolean sendHS = sendHandshakeMessage(uuid, deviceContact);
            if (!sendHS) {
                Log.e(TAG, "Failed to send handshake");
                return false;
            }
            return true;
        }

        private boolean sendHandshakeMessage(UUID uuid, DeviceContact contact){
            MessageBundle newMessage = new MessageBundle(
                    "", MessageTypes.HS, MainActivity.myDeviceContact, contact);
            newMessage.setTTL(1);
            newMessage.addMetadata("UUID", uuid.toString());
            return MainActivity.mDeliveryMan.sendMessage(newMessage, contact, this);
        }

        @Override
        void write(byte[] buffer) throws IOException {
            BluetoothService.write(mmOutStream, buffer);
        }

    }

    class DedicatedAcceptThread extends BluetoothThread {
        final UUID mmUuid;
        private final DeviceContact mmContact;
        private BluetoothSocket mmOriginalSocket;

        DedicatedAcceptThread(DeviceContact deviceContact, UUID uuid, BluetoothSocket originalSocket){
            mmUuid = uuid;
            mmContact = deviceContact;
            mmOriginalSocket = originalSocket;
        }

        public void run() {
            Log.d(TAG, "BEGIN DedicatedAcceptThread on UUID: " + mmUuid.toString());
            setName("DedicatedAcceptThread");

            BluetoothServerSocket server_socket;
            BluetoothSocket socket;

            // Create a new listening server socket
            try {
                server_socket = mAdapter.listenUsingRfcommWithServiceRecord(
                        "Dedicated" + mmContact.getDeviceName(), mmUuid);
                Log.d(SOCKET_TAG, "Created listening socket for specific: " + server_socket +
                        "for device: " + mmContact.getShortStr());
                Log.d(TAG, "Listening to UUID: " + mmUuid.toString());
                socket = server_socket.accept();
                Log.d(SOCKET_TAG, "Accepted socket: " + socket + "device: " + socket.getRemoteDevice().getName());

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
                Log.d(SOCKET_TAG, "Closing socket: " + server_socket);
                server_socket.close();
                Log.d(SOCKET_TAG, "Closing original socket: " + mmOriginalSocket);
                mmOriginalSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close a server socket", e);
            }

        }

        @Override
        void write(byte[] buffer) throws IOException { }

    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    class ConnectThread extends BluetoothThread {
        private BluetoothSocket mmInitSocket;
        private final BluetoothDevice mmDevice;
        private final DeviceContact mmContact;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        UUID mmUuid;

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
                Log.d(SOCKET_TAG, "Created socket to main UUID: " + mmInitSocket + " for device: " + mmDevice.getName());
            } catch (InterruptedException e3) {
                Log.e(TAG, "Connect/Acquire interrupted for " + mmDevice.getName(), e3);
            } catch (Exception e) {
                Log.e(SOCKET_TAG, "Socket create() failed", e);
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
                Log.e(SOCKET_TAG, "Failed to connect init thread to " + mmDevice.getName(), e);
                finishConnect();
                return;
            }

            getUuidAndConnect();
        }

        void getUuidAndConnect() {
            // Get the BluetoothSocket input and output streams
            try {
                mmInStream = mmInitSocket.getInputStream();
                mmOutStream = mmInitSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
                finishConnect();
                return;
            }

            boolean sendHS = sendHandshake();
            if (!sendHS){
                finishConnect();
                return;
            }

            boolean readHS = readHandshake();
            if (!readHS){
                finishConnect();
                return;
            }
            // Should have new uuid, now attempt to connect to it
            setUuid();
            finishConnect();
        }

        private boolean readHandshake() {
            byte[] buffer;
            byte[] sizeBuffer = new byte[4];
            int bytes;
            int tmpBytes;
            int packetSize;
            try {
                // Read UUID from inputStream
                mmInStream.read(sizeBuffer, 0, 4);
                packetSize = ByteBuffer.wrap(sizeBuffer).getInt();
                bytes = 0;
                buffer = new byte[4096];
                while (bytes!=packetSize){
                    tmpBytes = mmInStream.read(buffer, bytes, packetSize-bytes);
                    bytes += tmpBytes;
                }
                mMessageHandler.obtainMessage(
                        myMessageHandler.MESSAGE_IN, bytes, -1,
                        buffer).sendToTarget();

                while (mmUuid == null) {
                    Thread.sleep(500);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to get UUID, disconnected", e);
                return false;
            }
            return true;

        }

        private boolean sendHandshake(){
            MessageBundle newMessage = new MessageBundle(
                    "", MessageTypes.HS, MainActivity.myDeviceContact, mmContact);
            newMessage.setTTL(1);
            return MainActivity.mDeliveryMan.sendMessage(newMessage, mmContact, this);
        }

        void setUuid(){
            // Make a connection to the BluetoothSocket
            BluetoothSocket socket;

            try {
                socket = mmDevice.createRfcommSocketToServiceRecord(mmUuid);
                Log.d(SOCKET_TAG, "Opened " + socket.toString() + "to specific uuid " +mmDevice.getName());
            } catch (Exception e) {
                Log.e(SOCKET_TAG, "Socket create() failed", e);
                finishConnect();
                return;
            }

            try {
                socket.connect();
                Log.d(SOCKET_TAG, "Connected to " + mmDevice.getName() + "with socket: " + socket);
            } catch (IOException e) {
                Log.e(SOCKET_TAG, "Failed to connect to " + mmDevice.getName(), e);

                // Close the socket
                try {
                    socket.close();
                    Log.d(SOCKET_TAG, "CLOSING" + socket.toString() + mmDevice.getName());
                } catch (IOException e2) {
                    Log.e(SOCKET_TAG, "unable to close() socket during connection failure", e2);
                }
                return;

            }
            // Start the connected thread
            connected(socket, mmDevice);
        }

        void finishConnect() {
            mConnectThreads.remove(mmContact);
            mSemaphore.release();

            // TODO - attempt maybe not close this!!
            // Close the init socket
//            try {
//                Log.d(SOCKET_TAG, "Closing socket" + mmInitSocket);
//                if (mmInStream!=null)
//                    mmInStream.close();
//                if (mmOutStream!=null)
//                    mmOutStream.close();
//                mmInitSocket.close();
//            } catch (IOException e2) {
//                Log.e(TAG, "unable to close() socket during connection failure", e2);
//            }
        }

        @Override
        void write(byte[] buffer) throws IOException {
            BluetoothService.write(mmOutStream, buffer);
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    public class ConnectedThread extends BluetoothThread {
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

        void write(byte[] buffer) throws IOException {
            BluetoothService.write(mmOutStream, buffer);
        }


        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            setName("ConnectedThread-" + mmSocket.getRemoteDevice().getName());
            MainActivity.mRoutingTable.shareRoutingInfo();
            byte[] buffer;
            byte[] sizeBuffer = new byte[4];
            int bytes;
            int tmpBytes;
            int packetSize;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    mmInStream.read(sizeBuffer, 0, 4);
                    packetSize = ByteBuffer.wrap(sizeBuffer).getInt();
                    bytes = 0;
                    buffer = new byte[packetSize];
                    while (bytes!=packetSize){
                        tmpBytes = mmInStream.read(buffer, bytes, packetSize-bytes);
                        bytes += tmpBytes;
                    }
                    mMessageHandler.obtainMessage(
                            myMessageHandler.MESSAGE_IN, bytes, -1,
                            buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read message, disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void connectionLost() {
            mConnectedThreads.remove(mmContact);
            MainActivity.mRoutingTable.removeLinkFromTable(mmContact);

            // close socket
            try {
                Log.d(SOCKET_TAG,"Closing socket " + mmSocket + "device: " + mmContact.getShortStr());
                mmOutStream.close();
                mmInStream.close();
                mmSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void write(OutputStream outStream, byte[] buffer) throws IOException {
        outStream.write(buffer);
        // Share the sent message back to the UI Activity
        mMessageHandler.obtainMessage(
                myMessageHandler.MESSAGE_OUT, -1, -1,
                buffer).sendToTarget();
    }

    abstract class BluetoothThread extends Thread {
        abstract void write(byte[] buffer) throws IOException;
    }

}
