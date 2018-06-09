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
    private final BluetoothService service;
    private BluetoothSocket mmInitSocket;
    private final BluetoothDevice mmDevice;
    private final DeviceContact mmContact;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private final UUID mainConnectionUUID;
    public UUID mmUuid;

    // Debugging
    private static final String TAGPREFIX = "ConnectThread-";
    private static String TAG;

    public ConnectThread(BluetoothService service, BluetoothDevice device, UUID uuid) {
        this.service = service;
        mmDevice = device;
        mmContact = new DeviceContact(mmDevice);
        mainConnectionUUID = uuid;
        TAG = TAGPREFIX + mmDevice.getName();
        setName(TAG);
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectThread - " + mmDevice.getName());

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
        } catch (InterruptedException e) {
            Log.e(TAG, "Lock Acquire interrupted for " + mmDevice.getName(), e);
            finishConnection(false);
            return;
        }

        try{
            mmInitSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(mainConnectionUUID);
            Log.d(TAG, "Created socket to main UUID: " + mmInitSocket + " for device: " + mmDevice.getName());
        } catch (Exception e) {
            Log.e(TAG, "Socket create() failed", e);
            finishConnection(false);
            return;
        }

        if (mmInitSocket == null){
            finishConnection(false);
            return;
        }

        // Make a connection to the BluetoothSocket
        try {
            mmInitSocket.connect();
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect init thread to " + mmDevice.getName(), e);
            finishConnection(true);
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
            Log.e(TAG, "Couldn't get input/output streams", e);
            finishConnection(true);
            return;
        }

        boolean sendHS = sendHandshake();
        if (!sendHS){
            finishConnection(true);
            return;
        }

        boolean readHS = readHandshake();
        if (!readHS){
            finishConnection(true);
            return;
        }
        // Should have new uuid, now attempt to connect to it
        closeInitSocket();
        connectWithDedicatedUUID();
    }

    private boolean readHandshake() {
        byte[] buffer;
        byte[] sizeBuffer = new byte[8];
        int bytes;
        int tmpBytes;
        int packetSize;
        try {
            // Read UUID from inputStream
            mmInStream.read(sizeBuffer, 0, 8);
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

    private void connectWithDedicatedUUID(){
        // Make a connection to the BluetoothSocket
        BluetoothSocket socket;
        try {
            socket = mmDevice.createInsecureRfcommSocketToServiceRecord(mmUuid);
            Log.d(TAG, "Opened " + socket.toString() + "to specific uuid " +mmDevice.getName());
        } catch (Exception e) {
            Log.e(TAG, "Socket create() failed", e);
            finishConnection(false);
            return;
        }

        try {
            socket.connect();
            Log.d(TAG, "Connected to " + mmDevice.getName() + "with socket: " + socket);
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to " + mmDevice.getName(), e);
            // Close the socket
            finishConnection(false);
            try {
                socket.close();
                Log.d(TAG, "CLOSING" + socket.toString() + mmDevice.getName());
            } catch (IOException e2) {
                Log.e(TAG, "unable to close() socket during connection failure", e2);
            }
            return;
        }
        finishConnection(false);
        // Start the connected thread
        service.connected(socket, mmDevice);
    }

    private void finishConnection(boolean shouldCloseInitSocket){
        service.mConnectThreads.remove(mmContact);
        service.mSemaphore.release();
        if (shouldCloseInitSocket)
            closeInitSocket();

    }

    private void closeInitSocket() {
        // Close the init socket
            try {
                Log.d(TAG, "Closing the main connection socket " + mmInitSocket);
                if (mmInStream!=null)
                    mmInStream.close();
                if (mmOutStream!=null)
                    mmOutStream.close();
                mmInitSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "unable to close() socket during connection failure", e);
            }
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        service.write(mmOutStream, buffer);
    }
}
