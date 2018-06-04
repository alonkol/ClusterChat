package il.ac.tau.cs.mansur.kollmann.clusterchat.Connection;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import il.ac.tau.cs.mansur.kollmann.clusterchat.BluetoothService;
import il.ac.tau.cs.mansur.kollmann.clusterchat.DeviceContact;
import il.ac.tau.cs.mansur.kollmann.clusterchat.MainActivity;
import il.ac.tau.cs.mansur.kollmann.clusterchat.myMessageHandler;

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
public class ConnectedThread extends BluetoothThread {
    private BluetoothService service;
    private final BluetoothSocket mmSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private final DeviceContact mmContact;

    // Debugging
    private static final String TAGPREFIX = "ConnectedThread-";
    private static String TAG;

    public ConnectedThread(BluetoothService service, BluetoothSocket socket, DeviceContact contact) {
        this.service = service;
        mmSocket = socket;
        mmContact = contact;

        TAG = TAGPREFIX + contact.getDeviceName();
        setName(TAG);
    }

    public boolean initStreams(){
        // Get the BluetoothSocket input and output streams
        try {
            mmInStream = mmSocket.getInputStream();
            mmOutStream = mmSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "streams not created", e);
            try{
                mmSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return false;
        }
        return true;
    }

    public void write(byte[] buffer) throws IOException {
        service.write(mmOutStream, buffer);
    }


    public void run() {
        Log.i(TAG, "BEGIN mConnectedThread");
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
                service.mMessageHandler.obtainMessage(
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
        // close socket
        try {
            Log.d(TAG,"Closing socket " + mmSocket + "device: " + mmContact.getShortStr());
            mmOutStream.close();
            mmInStream.close();
            mmSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        service.mConnectedThreads.remove(mmContact);
        MainActivity.mRoutingTable.removeLinkFromTable(mmContact);
    }
}
