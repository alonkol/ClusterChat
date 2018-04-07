package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class RoutingTable {
    private static final String TAG="RoutingTable";
    private static HashMap<String, BluetoothService.ConnectedThread> mtable;
    private static HashMap<BluetoothService.ConnectedThread, HashSet<String>> revertedTable;

    public RoutingTable(){
        mtable = new HashMap<>();
        revertedTable = new HashMap<>();
    }

    public BluetoothService.ConnectedThread getThread(String deviceName){
        return mtable.get(deviceName);
    }

    public HashSet<String> getAllDevicesForThread(BluetoothService.ConnectedThread t){
        return revertedTable.get(t);
    }

    public void addDeviceToTable(String deviceName, BluetoothService.ConnectedThread t){
        addDeviceToTable(deviceName, t, true);
    }

    //TODO needs __hash__ and __equals__ in connectedThread
    public void addDeviceToTable(String deviceName, BluetoothService.ConnectedThread t, boolean checkConsistency){
        mtable.put(deviceName, t);
        if (!revertedTable.containsKey(t)){
            revertedTable.put(t, new HashSet<String>());
        }
        revertedTable.get(t).add(deviceName);
        if (checkConsistency)
            checkConsistency();
    }

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
        }
        checkConsistency();

    }

    public void removeThreadFromTable(BluetoothService.ConnectedThread t){
        for (String deviceName: revertedTable.get(t)){
            mtable.remove(deviceName);
        }
        revertedTable.remove(t);
        checkConsistency();
    }

    public void logTable(){
        for (String deviceName: mtable.keySet()){
            Log.d(TAG,
                    String.format("Device Name: %s Thread %s", deviceName, mtable.get(deviceName)));
        }
    }

    public void checkConsistency(){
        int count = 0;
        for(BluetoothService.ConnectedThread t: revertedTable.keySet()){
                HashSet<String> devicesList = revertedTable.get(t);
                for (String deviceName: devicesList){
                    count ++;
                    if (mtable.get(deviceName) != t){
                        Log.e(TAG,
                                String.format("Mismatch in tables for thread %s and device %s", t, deviceName));
                        // TODO all hell break loose
                    }
                }
        }
        if (count!=mtable.size()){
            Log.e(TAG,
                    String.format("Mismatch number of devices between tables"));
        }
    }

}
