package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
    private MainActivity mMainActivity;
    private ArrayList<BluetoothDevice> mWaitingList;

    public myBroadcastReceiver(BluetoothService bluetoothService, MainActivity mainActivity){
        mBluetoothService = bluetoothService;
        mMainActivity = mainActivity;
        mWaitingList = new ArrayList<>();
    }

    void addDeviceToWaitingList(BluetoothDevice device){
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
            if ((!device.getName().startsWith(Character.toString((char) (firstLetter - 1)))) &&
                    (!device.getName().startsWith(Character.toString((char) (firstLetter + 1))))){
                Log.d(TAG, "device " + device.getName()+ " is not in matching levels (" +
                        (Character.toString(firstLetter)) + ") so not connecting");
                return;
            }
        }

        if (mWaitingList.contains(device)){
            return;
        }

        if (mBluetoothService.checkIfDeviceConnected(device)){
            return;
        }

        //TODO try this approach - this will eliminate duplicate connections
        if (!MainActivity.myDeviceContact.getDeviceId().equals(MainActivity.DEFAULT_DEVICE_ID)){
            if (MainActivity.myDeviceContact.getDeviceId().compareTo(device.getAddress()) < 0){
                return;
            }
        } else {
            if (MainActivity.myDeviceContact.getDeviceName().compareTo(device.getName()) < 0) {
                return;
            }
        }

        mWaitingList.add(device);
    }

    void handleUUIDResult(BluetoothDevice deviceExtra, Parcelable[] uuidExtra){
        Log.d(TAG,"Handling UUIDS for device - " + deviceExtra.getName() +
                '/' + deviceExtra.getAddress());
        if (uuidExtra != null) {
            boolean foundUuid = findUuid(uuidExtra);
            if (!foundUuid){
                Log.d(TAG, "No matching uuid found for device " +
                        deviceExtra.getName());
            }
            else {
                Log.d(TAG, "Found matching uuid for device " +
                        deviceExtra.getName() + '/' + deviceExtra.getAddress());
                mBluetoothService.connect(deviceExtra);
            }
        } else {
            Log.d(TAG,"uuidExtra is still null");
        }
    }

    private void handleWaitingList(){
        tryFetchNextDevice();
    }

    void tryFetchNextDevice(){
        if (!mWaitingList.isEmpty()) {
            BluetoothDevice device = mWaitingList.remove(0);
            if (mBluetoothService.checkIfDeviceConnected(device)){
                tryFetchNextDevice();
            }else{
                startFetchUUIDS(device);
            }
        }else{
            // Release lock for connections to happen
            mBluetoothService.mSemaphore.release(BluetoothService.MAX_CONNECTED_THREADS);
        }
    }

    void startFetchUUIDS(BluetoothDevice device){
        Log.d(TAG, "Fetching from device " + device.getAddress() + '/' +
                device.getName());
        boolean result = device.fetchUuidsWithSdp();
    }

    private boolean findUuid(Parcelable[] uuids){
        if (uuids == null) {
            return false;
        }
        for (Parcelable p : uuids) {
            UUID uuid = ((ParcelUuid) p).getUuid();
            if (uuid.equals(BluetoothService.MAIN_ACCEPT_UUID)) {
                return true;
            }
            // Handle Android bug swap bug
            if (byteSwappedUuid(uuid).equals(BluetoothService.MAIN_ACCEPT_UUID)) {
                return true;
            }
        }
        return false;
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


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        
        switch (action) {
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                Log.d(TAG, "Discovery finished starting to handle waiting list");
                handleWaitingList();
                break;
            case BluetoothDevice.ACTION_FOUND:
                // When discovery finds a device
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addDeviceToWaitingList(device);
                break;
            case BluetoothDevice.ACTION_UUID:
                // This is when we can be assured that fetchUuidsWithSdp has completed.
                // So get the uuids and call fetchUuidsWithSdp on another device in list
                BluetoothDevice deviceExtra = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                handleUUIDResult(deviceExtra, uuidExtra);
                tryFetchNextDevice();
                break;
            case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                mMainActivity.startActivityForResult(enableIntent, MainActivity.REQUEST_ENABLE_BT);
                break;
            default:
                break;
        }
    }
}
