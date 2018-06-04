package il.ac.tau.cs.mansur.kollmann.clusterchat.Connection;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import il.ac.tau.cs.mansur.kollmann.clusterchat.BluetoothService;
import il.ac.tau.cs.mansur.kollmann.clusterchat.DeviceContact;

public class DedicatedAcceptThread extends BluetoothThread {
    private BluetoothService service;
    final UUID mmUuid;
    private final DeviceContact mmContact;
    private BluetoothSocket mmOriginalSocket;

    // Debugging
    private static final String TAG = "DedicatedAcceptThread";

    DedicatedAcceptThread(BluetoothService service, DeviceContact deviceContact, UUID uuid,
                          BluetoothSocket originalSocket){
        mmUuid = uuid;
        mmContact = deviceContact;
        mmOriginalSocket = originalSocket;
        this.service = service;
    }

    public void run() {
        Log.d(TAG, "BEGIN DedicatedAcceptThread on UUID: " + mmUuid.toString());
        setName("DedicatedAcceptThread");

        BluetoothServerSocket server_socket;
        BluetoothSocket socket;

        // Create a new listening server socket
        try {
            server_socket = service.mAdapter.listenUsingRfcommWithServiceRecord(
                    "Dedicated" + mmContact.getDeviceName(), mmUuid);
            Log.d(TAG, "Created listening socket for specific: " + server_socket +
                    "for device: " + mmContact.getShortStr());
            Log.d(TAG, "Listening to UUID: " + mmUuid.toString());
            socket = server_socket.accept();
            Log.d(TAG, "Accepted socket: " + socket + "device: " + socket.getRemoteDevice().getName());

        } catch (Exception e) {
            Log.e(TAG, "Dedicated socket creation failed", e);
            return;
        }

        // If a connection was accepted
        if (socket != null) {
            Log.d(TAG, "Accept thread established connection with: " +
                    socket.getRemoteDevice().getName());

            service.connected(socket, socket.getRemoteDevice());
        }

        try {
            Log.d(TAG, "Connected, closing Server Socket");
            Log.d(TAG, "Closing socket: " + server_socket);
            server_socket.close();
            Log.d(TAG, "Closing original socket: " + mmOriginalSocket);
            mmOriginalSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close a server socket", e);
        }

    }

}
