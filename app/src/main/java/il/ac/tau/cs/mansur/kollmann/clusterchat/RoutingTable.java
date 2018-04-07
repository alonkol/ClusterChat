package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class RoutingTable {
    private static final String TAG="RoutingTable";
    private static HashMap<String, BluetoothService.ConnectedThread> mtable;
    private static HashMap<BluetoothService.ConnectedThread, HashSet<String>> revertedTable;

    public RoutingTable(){
        mtable = new HashMap<>();
        revertedTable = new HashMap<>();
        // TODO Lock???
    }

    public BluetoothService.ConnectedThread getThread(String deviceName){
        return mtable.get(deviceName);
    }

    public HashSet<String> getAllDevicesForThread(BluetoothService.ConnectedThread t){
        return revertedTable.get(t);
    }

    private void addDeviceToTable(String deviceName, BluetoothService.ConnectedThread t, boolean checkConsistency){
        mtable.put(deviceName, t);
        Log.d(TAG, String.format("Added device name %s and thread %s to tables", deviceName, t));
        if (!revertedTable.containsKey(t)){
            revertedTable.put(t, new HashSet<String>());
        }
        revertedTable.get(t).add(deviceName);
        if (checkConsistency)
            checkConsistency();
    }

    public void addDeviceToTable(String deviceName, BluetoothService.ConnectedThread t){
        addDeviceToTable(deviceName, t, true);
    }

    //TODO needs __hash__ and __equals__ in connectedThread

    public void addDeviceListToTable(ArrayList<String> deviceNames, BluetoothService.ConnectedThread t){
        for (String deviceName: deviceNames){
            addDeviceToTable(deviceName, t, false);
        }
        checkConsistency();
    }

    public void removeDeviceFromTable(String deviceName){
        BluetoothService.ConnectedThread t = mtable.get(deviceName);
        mtable.remove(deviceName);
        revertedTable.get(t).remove(deviceName);
        if (revertedTable.get(t).size() == 0){
            revertedTable.remove(t);
            Log.d(TAG, String.format("Removed thread %s due to removal of device %s", t, deviceName));
        }
        checkConsistency();
        Log.d(TAG, String.format("Removed device %s", deviceName));
    }

    public void removeThreadFromTable(BluetoothService.ConnectedThread t){
        for (String deviceName: revertedTable.get(t)){
            Log.d(TAG, String.format("Removing device %s due to removal of thread %s", deviceName, t));
            mtable.remove(deviceName);
        }
        revertedTable.remove(t);
        checkConsistency();
        Log.d(TAG, String.format("Removed thread %s and all of its devices", t));
    }

    public void logTable(boolean showReversed){
        for (String deviceName: mtable.keySet()){
            Log.d(TAG,
                    String.format("Device Name: %s Thread %s", deviceName, mtable.get(deviceName)));
        }
        if (showReversed){
            for (BluetoothService.ConnectedThread t: revertedTable.keySet()){
                Log.d(TAG,
                        String.format(
                                "Thread: %s Devices %s", t, Arrays.toString(revertedTable.get(t).toArray())));
            }
        }
    }

    private void checkConsistency(){
        int count = 0;
        ArrayList<String> allDevices = new ArrayList<>();
        for(BluetoothService.ConnectedThread t: revertedTable.keySet()){
                HashSet<String> devicesList = revertedTable.get(t);
                allDevices.addAll(devicesList);
                for (String deviceName: devicesList){
                    count ++;
                    if (mtable.get(deviceName) != t){
                        Log.e(TAG,
                                String.format("Mismatch in tables for thread %s and device %s", t, deviceName));
                        logTable(true);
                        // TODO all hell break loose
                    }
                }
        }
        if (count!=mtable.size()){
            Log.e(TAG,"Mismatch number of devices between tables\n" +
                    "All devices: " + Arrays.toString(allDevices.toArray()) +
                    "\nIn table: " + Arrays.toString(mtable.keySet().toArray()));
            logTable(true);
            // TODO all hell break loose
        }
        Log.d(TAG, "Consistency check done");
        logTable(false);
    }

}
