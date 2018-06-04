package il.ac.tau.cs.mansur.kollmann.clusterchat.Connection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

import il.ac.tau.cs.mansur.kollmann.clusterchat.BluetoothService;
import il.ac.tau.cs.mansur.kollmann.clusterchat.DeviceContact;
import il.ac.tau.cs.mansur.kollmann.clusterchat.MainActivity;
import il.ac.tau.cs.mansur.kollmann.clusterchat.MessageBundle;
import il.ac.tau.cs.mansur.kollmann.clusterchat.MessageTypes;
import il.ac.tau.cs.mansur.kollmann.clusterchat.myMessageHandler;

/**
 * This thread runs while attempting to make an outgoing connection
 * with a device. It runs straight through; the connection either
 * succeeds or fails.
 */
public class ConnectThread extends BluetoothThread {
    private BluetoothService service;
    private BluetoothSocket mmInitSocket;
    private final BluetoothDevice mmDevice;
    private final DeviceContact mmContact;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    public UUID mmUuid;

    // Debugging
    private static final String TAG = "ConnectThread";
    private static final String SOCKET_TAG = "Sockets";

    public ConnectThread(BluetoothService service, BluetoothDevice device) {
        this.service = service;
        mmDevice = device;
        mmContact = new DeviceContact(mmDevice);
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectThread - " + mmDevice.getName());
        setName("ConnectThread-" + mmDevice.getName());

        // if already connected, return.
        if (service.mConnectedThreads.containsKey(mmContact)) {
            Log.i(TAG, "Already connected to device: " + mmDevice.getName());
            service.mConnectThreads.remove(mmContact);
            return;
        }

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            Log.d(TAG, "Acquiring connection lock");
            service.mSemaphore.acquire();
            Log.d(TAG, "Lock acquired, connecting...");
            mmInitSocket = mmDevice.createRfcommSocketToServiceRecord(BluetoothService.MAIN_ACCEPT_UUID);
            Log.d(SOCKET_TAG, "Created socket to main UUID: " + mmInitSocket + " for device: " + mmDevice.getName());
        } catch (InterruptedException e3) {
            Log.e(TAG, "Connect/Acquire interrupted for " + mmDevice.getName(), e3);
        } catch (Exception e) {
            Log.e(SOCKET_TAG, "Socket create() failed", e);
            finishConnect();
            return;
        }

        if (mmInitSocket == null){
            service.mConnectThreads.remove(mmContact);
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

    private void getUuidAndConnect() {
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
            service.mMessageHandler.obtainMessage(
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
        service.connected(socket, mmDevice);
    }

    void finishConnect() {
        service.mConnectThreads.remove(mmContact);
        service.mSemaphore.release();

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
    public void write(byte[] buffer) throws IOException {
        service.write(mmOutStream, buffer);
    }
}
