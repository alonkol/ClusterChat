package il.ac.tau.cs.mansur.kollmann.clusterchat.Connection;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import il.ac.tau.cs.mansur.kollmann.clusterchat.BluetoothService;
import il.ac.tau.cs.mansur.kollmann.clusterchat.DeviceContact;

 class DedicatedAcceptThread extends BluetoothThread {
    private final BluetoothService service;
    private final UUID mmUuid;
    private final DeviceContact mmContact;

    // Debugging
    private static final String TAGPREFIX = "DedicatedAcceptThread-";
    private static String TAG;

    DedicatedAcceptThread(BluetoothService service, DeviceContact deviceContact, UUID uuid){
        mmUuid = uuid;
        mmContact = deviceContact;
        this.service = service;
        TAG = TAGPREFIX + mmContact.getDeviceName();
        setName(TAG);

    }

    public void run() {
        Log.d(TAG, "BEGIN DedicatedAcceptThread with device: " + mmContact.getShortStr() + " on UUID: " + mmUuid.toString());

        BluetoothServerSocket server_socket;
        BluetoothSocket socket;

        try {
            server_socket = service.mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    "Dedicated" + mmContact.getDeviceName(), mmUuid);
            Log.d(TAG, "Server socket initiated " + server_socket);
        } catch (IOException e) {
            Log.e(TAG, "Dedicated socket creation failed", e);
            return;
        }

        // Create a new listening server socket
        try {
            socket = server_socket.accept(100000);
        } catch (Exception e) {
            Log.e(TAG, "Dedicated socket accept timeout or failed", e);
            closeServerSocket(server_socket);
            return;
        }
        // If a connection was accepted
        Log.d(TAG, "Dedicated accept thread established connection with: " +
                socket.getRemoteDevice().getName() + "socket:" + socket);

        service.connected(socket, socket.getRemoteDevice());
        closeServerSocket(server_socket);

    }

    private void closeServerSocket(BluetoothServerSocket server_socket) {
        try {
            Log.d(TAG, "Connected, closing Server Socket");
            server_socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close a server socket", e);
        }
    }

}
