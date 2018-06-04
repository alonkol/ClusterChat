package il.ac.tau.cs.mansur.kollmann.clusterchat.Connection;

import android.bluetooth.BluetoothServerSocket;
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

public class MainAcceptThread extends BluetoothThread {
    private BluetoothService service;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    // Debugging
    private static final String TAG = "MainAcceptThread";

    public MainAcceptThread(BluetoothService service){
        this.service = service;
    }

    public void run() {
        Log.d(TAG, "BEGIN MainAcceptThread" + this);
        setName("MainAcceptThread");

        BluetoothSocket socket;
        BluetoothServerSocket mainListeningSocket;

        try {
            mainListeningSocket = service.mAdapter.listenUsingRfcommWithServiceRecord(
                    "Main" + service.mAdapter.getName(), BluetoothService.MAIN_ACCEPT_UUID);
            Log.d(TAG, "Opened SUPER: " + mainListeningSocket.toString());

        } catch (Exception e) {
            Log.e(TAG, "Socket listen() failed", e);
            return;
        }

        while (true) {
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(TAG, "Listening to the next connection...");
                socket = mainListeningSocket.accept();
                Log.d(TAG, "Accepted socket: " + socket.toString() + " device: " + socket.getRemoteDevice().getName());
            } catch (IOException e) {
                Log.e(TAG, "main accept() failed", e);
                break;
            }

            // A connection was accepted
            handleAcceptedSocket(socket);

        }
    }

    private void handleAcceptedSocket(BluetoothSocket socket) {
        // if already connected, Terminate new socket.
        if (service.mConnectedThreads.containsKey(new DeviceContact(socket.getRemoteDevice()))) {
            Log.d(TAG, "Accept thread established connection with: "  +
                    socket.getRemoteDevice().getName() + " but this exists so leave it. ");
                try {
                    Log.d(TAG, "Accept thread established connection with: " +
                            socket.getRemoteDevice().getName() + " but this exists so close. ");
                    Log.d(TAG, "closing socket " + socket.toString()  + " device: " + socket.getRemoteDevice().getName());
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close unwanted socket " + "device: " + socket.getRemoteDevice().getName(), e);
                }
            return;
        }

        // Get input and output streams for the socket
        try{
            mmInStream = socket.getInputStream();
            mmOutStream = socket.getOutputStream();
        }catch (IOException e){
            Log.e(TAG, "Could not open streams", e);
            try {
                Log.d(TAG, "closing socket " + socket.toString() + " device: " + socket.getRemoteDevice().getName());
                socket.close();
            } catch (IOException e2) {
                Log.e(TAG, "Could not close unwanted socket, device: " + socket.getRemoteDevice().getName(), e2);
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
                Log.d(TAG, "closing socket " + socket.toString() + "device: " + socket.getRemoteDevice().getName());
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close unwanted socket" + "device: " + socket.getRemoteDevice().getName(), e);
            }
        }

        // Send handshake with new uuid to connect
        result = sendHS(uuid, contact);
        if (!result){
            try {
                Log.d(TAG, "closing socket " + socket.toString() + "device: " + socket.getRemoteDevice().getName());
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close unwanted socket" + "device: " + socket.getRemoteDevice().getName(), e);
            }
        }

        // Start a listening socket to listen on the new UUID
        DedicatedAcceptThread thread = new DedicatedAcceptThread(service, contact, uuid, socket);
        thread.start();
        // will close the original socket in dedicated thread
        try {
            mmInStream.close();
            mmOutStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close unwanted socket device: " + socket.getRemoteDevice().getName(), e);
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
            service.mMessageHandler.obtainMessage(
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
    public void write(byte[] buffer) throws IOException {
        service.write(mmOutStream, buffer);
    }

}
