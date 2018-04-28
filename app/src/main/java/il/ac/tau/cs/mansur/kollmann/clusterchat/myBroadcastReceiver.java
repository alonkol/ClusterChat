package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;

public class myBroadcastReceiver extends BroadcastReceiver {
    private final static String TAG = "Broadcast Reciever";
    private BluetoothService mBluetoothService;
    ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();

    public myBroadcastReceiver(BluetoothService bluetoothService){
        mBluetoothService = bluetoothService;
    }

    private UUID findUuid(Parcelable[] uuids){
        if (uuids == null) {
            return null;
        }
        for (Parcelable p : uuids) {
            UUID uuid = ((ParcelUuid) p).getUuid();
            if (uuid.toString().startsWith(MainActivity.UUID_PREFIX)) {
                return uuid;
            }
            // Handle Android bug swap bug
            if (uuid.toString().endsWith(MainActivity.UUID_PREFIX)) {
                return byteSwappedUuid(uuid);
            }
        }
        return null;
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

    void tryFetchNextDevice(){
        if (!mDeviceList.isEmpty()) {
            final BluetoothDevice device = mDeviceList.remove(0);
            if (!mBluetoothService.checkIfDeviceConnected(device)) {
                Log.d(TAG, "Fetching from device " + device.getAddress() + '/' +
                        device.getName());

                new Thread() {
                    public void run() {
                        setName("FetchingUuids-" + device.getAddress());
                        Log.d("FetchUuids", "Acquiring all locks...");
                        mBluetoothService.mSemaphore.acquireUninterruptibly(BluetoothService.MAX_CONNECTED_THREADS);
                        Log.d("FetchUuids", "All locks acquired.");
                        boolean result = device.fetchUuidsWithSdp();
                    }
                }.start();

            } else {
                tryFetchNextDevice();
            }
        }
    }

    void addNewDiscoveredDevice(BluetoothDevice device){
        Log.d(TAG, "Discovery found a device! " + device.getName() + '/' +
                device.getAddress());
        if (device.getName() != null){
            if (!mBluetoothService.checkIfDeviceConnected(device)){
                mDeviceList.add(device);
                Log.d(TAG, "Unknown device, adding to waiting list " +
                        device.getAddress() + '/' + device.getName());
            }
        }
    }

    void handleUUIDResult(BluetoothDevice deviceExtra, Parcelable[] uuidExtra){
        Log.d(TAG,"Handling UUIDS for device " + deviceExtra.getName() +
                '/' + deviceExtra.getAddress());
        if (uuidExtra != null) {
            UUID uuid = findUuid(uuidExtra);
            if (uuid == null){
                Log.d(TAG, "No matching uuid found for device " +
                        deviceExtra.getName());
            }
            else {
                Log.d(TAG, "Found matching uuid for device " +
                        deviceExtra.getName() + '/' + deviceExtra.getAddress());
                mBluetoothService.connect(deviceExtra, uuid);
            }
        } else {
            Log.d(TAG,"uuidExtra is still null");
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
                onDiscoveryEnd();
                break;
            case BluetoothDevice.ACTION_FOUND:
                // When discovery finds a device
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addNewDiscoveredDevice(device);
                break;
            case BluetoothDevice.ACTION_UUID:
                mBluetoothService.mSemaphore.release(BluetoothService.MAX_CONNECTED_THREADS);
                Log.d(TAG, "UUID scan finished, released all locks...");
                // This is when we can be assured that fetchUuidsWithSdp has completed.
                // So get the uuids and call fetchUuidsWithSdp on another device in list
                Log.d(TAG, "Done fetching ");
                BluetoothDevice deviceExtra = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                handleUUIDResult(deviceExtra, uuidExtra);
                tryFetchNextDevice();
                break;
            case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                // TODO: register ACTION_STATE_CHANGED.
                // TODO: bluetooth disabled during usage.
                break;
            default:
                break;
        }
    }

    public void onDiscoveryEnd() {
        mBluetoothService.mSemaphore.release(BluetoothService.MAX_CONNECTED_THREADS);
        Log.d(TAG, "Discovery finished starting to handle waiting list");
        tryFetchNextDevice();
    }
}
