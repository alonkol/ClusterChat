package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class RoutingTable<T> {
    private static final String TAG="RoutingTable";
    private HashMap<DeviceContact, T> mtable;
    private HashMap<T, HashSet<DeviceContact>> revertedTable;

    public RoutingTable(){
        mtable = new HashMap<>();
        revertedTable = new HashMap<>();
        // TODO Lock???
    }

    public T getThread(DeviceContact deviceContact){
        return mtable.get(deviceContact);
    }

    public HashSet<DeviceContact> getAllDevicesForThread(T t){
        return revertedTable.get(t);
    }

    private void addDeviceToTable(DeviceContact deviceContact, T t,
                                  boolean overrideIfExists, boolean checkConsistency){
        if (mtable.containsKey(deviceContact)){
            Log.i(TAG, "device "+ deviceContact + "already exists in table");
            if (overrideIfExists){
                Log.d(TAG, "Override exists flag is on, removing and adding new");
                removeDeviceFromTable(deviceContact);
            }else{
                return ;
            }
        }
        mtable.put(deviceContact, t);
        Log.d(TAG, String.format(
                "Added device name %s and thread %s to tables", deviceContact, t));
        if (!revertedTable.containsKey(t)){
            revertedTable.put(t, new HashSet<DeviceContact>());
        }
        revertedTable.get(t).add(deviceContact);
        if (checkConsistency)
            checkConsistency();
    }

    public void addDeviceToTable(DeviceContact deviceContact, T t,
                                 boolean overrideIfExists){
        addDeviceToTable(deviceContact, t,  overrideIfExists, true);
    }

    //TODO needs __hash__ and __equals__ in connectedThread???

    public void addDeviceListToTable(ArrayList<DeviceContact> deviceContacts,
                                     T t, boolean overrideIfExists){
        for (DeviceContact deviceContact: deviceContacts){
            addDeviceToTable(deviceContact, t, overrideIfExists, false);
        }
        checkConsistency();
    }

    public void removeDeviceFromTable(DeviceContact deviceContact){
        T t = mtable.get(deviceContact);
        mtable.remove(deviceContact);
        revertedTable.get(t).remove(deviceContact);
        if (revertedTable.get(t).size() == 0){
            revertedTable.remove(t);
            Log.d(TAG, String.format("Removed thread %s due to removal of device %s", t, deviceContact));
        }
        checkConsistency();
        Log.d(TAG, String.format("Removed device %s", deviceContact));
    }

    public void removeThreadFromTable(T t){
        for (DeviceContact deviceContact: revertedTable.get(t)){
            Log.d(TAG, String.format("Removing device %s due to removal of thread %s", deviceContact, t));
            mtable.remove(deviceContact);
        }
        revertedTable.remove(t);
        checkConsistency();
        Log.d(TAG, String.format("Removed thread %s and all of its devices", t));
    }

    public Set<DeviceContact> getAllConnectedDevices(){
        return mtable.keySet();
    }

    private void logTable(boolean showReversed){
        Log.d(TAG, "Routing Table");
        for (DeviceContact deviceContact: mtable.keySet()){
            Log.d(TAG,
                    String.format("Device Name: %s Thread %s", deviceContact, mtable.get(deviceContact)));
        }
        if (showReversed){
            Log.d(TAG, "Reversed Table");
            for (T t: revertedTable.keySet()){
                Log.d(TAG,
                        String.format(
                                "Thread: %s Devices %s", t, Arrays.toString(revertedTable.get(t).toArray())));
            }
        }
    }

    private void checkConsistency(){
        int count = 0;
        ArrayList<DeviceContact> allDevices = new ArrayList<>();
        for(T t: revertedTable.keySet()){
                HashSet<DeviceContact> devicesList = revertedTable.get(t);
                allDevices.addAll(devicesList);
                for (DeviceContact deviceContact: devicesList){
                    count ++;
                    if (mtable.get(deviceContact) != t){
                        Log.e(TAG,
                                String.format("Mismatch in tables for thread %s and device %s", t, deviceContact));
                        logTable(true);
                        // TODO all hell break loose
                        return;
                    }
                }
        }
        if (count!=mtable.size()){
            Log.e(TAG,"Mismatch number of devices between tables\n" +
                    "All devices: " + Arrays.toString(allDevices.toArray()) +
                    "\nIn table: " + Arrays.toString(mtable.keySet().toArray()));
            logTable(true);
            // TODO all hell break loose
            return;
        }
        Log.d(TAG, "Consistency check passed");
        logTable(false);
    }

}
