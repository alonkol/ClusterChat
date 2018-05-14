package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class myBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = "Broadcast Reciever";
    private BluetoothService mBluetoothService;

    public myBroadcastReceiver(BluetoothService bluetoothService){
        mBluetoothService = bluetoothService;
    }

    void tryConnect(BluetoothDevice device){
        Log.d(TAG, "Discovery found a device! " + device.getName() + "/" +
                device.getAddress());

        if (device.getName() == null) {
            return;
        }
        if (MainActivity.LEVEL_ROUTING_FLAG){
            // In order to debug complex networks, the devices will be named A1, B1, B2, C3 etc.
            // A device will only initiate connections to devices from the next level
            // (the level is the leading letter in the device name)
            // meaning a device called B1 will initiate connections only to devices whose names start with C
            char firstLetter = MainActivity.myDeviceContact.getDeviceName().charAt(0);
            firstLetter += 1;
            if (!device.getName().startsWith(Character.toString(firstLetter))){
                return;
            }
        }

        if (!mBluetoothService.checkIfDeviceConnected(device)){
            mBluetoothService.connect(device);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        
        switch (action) {
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                mBluetoothService.mSemaphore.release(BluetoothService.MAX_CONNECTED_THREADS);
                Log.d(TAG, "Discovery finished starting to handle waiting list");
                break;
            case BluetoothDevice.ACTION_FOUND:
                // When discovery finds a device
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                tryConnect(device);
                break;
            case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                // TODO: register ACTION_STATE_CHANGED.
                // TODO: bluetooth disabled during usage.
                break;
            default:
                break;
        }
    }
}
