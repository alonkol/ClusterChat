package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class RoutingTable {
    private static final String TAG="RoutingTable";
    private HashMap<DeviceContact, DeviceContact> mtable;
    private HashMap<DeviceContact, HashSet<DeviceContact>> revertedTable;

    public RoutingTable(){
        mtable = new HashMap<>();
        revertedTable = new HashMap<>();
        // TODO Lock???
    }

    public DeviceContact getLink(DeviceContact deviceContact){
        return mtable.get(deviceContact);
    }

    public HashSet<DeviceContact> getAllDevicesForLink(DeviceContact link){
        return revertedTable.get(link);
    }

    private void addDeviceToTable(DeviceContact deviceContact, DeviceContact linkDevice,
                                  boolean overrideIfExists, boolean checkConsistency){
        if (mtable.containsKey(deviceContact)){
            Log.i(TAG, "device "+ deviceContact.getDeviceName() + "already exists in table");
            if (overrideIfExists){
                Log.d(TAG, "Override exists flag is on, removing and adding new");
                removeDeviceFromTable(deviceContact);
            }else{
                return ;
            }
        }
        mtable.put(deviceContact, linkDevice);
        Log.d(TAG, String.format(
                "Added device name %s and link %s to tables",
                deviceContact.getDeviceName(), linkDevice.getDeviceName()));
        if (!revertedTable.containsKey(linkDevice)){
            revertedTable.put(linkDevice, new HashSet<DeviceContact>());
        }
        revertedTable.get(linkDevice).add(deviceContact);
        if (checkConsistency)
            checkConsistency();
    }

    public void addDeviceToTable(DeviceContact deviceContact, DeviceContact linkDevice,
                                 boolean overrideIfExists){
        addDeviceToTable(deviceContact, linkDevice,  overrideIfExists, true);
    }

    //TODO needs __hash__ and __equals__ in connectedThread???

    public void addDeviceListToTable(ArrayList<DeviceContact> deviceContacts,
                                     DeviceContact linkDevice, boolean overrideIfExists){
        for (DeviceContact deviceContact: deviceContacts){
            addDeviceToTable(deviceContact, linkDevice, overrideIfExists, false);
        }
        checkConsistency();
    }

    public void removeDeviceFromTable(DeviceContact deviceContact){
        DeviceContact link = mtable.get(deviceContact);
        mtable.remove(deviceContact);
        revertedTable.get(link).remove(deviceContact);
        if (revertedTable.get(link).size() == 0){
            revertedTable.remove(link);
            Log.d(TAG, String.format("Removed link %s due to removal of device %s",
                    link.getDeviceName(), deviceContact.getDeviceName()));
        }
        checkConsistency();
        Log.d(TAG, String.format("Removed device %s", deviceContact.getDeviceName()));
    }

    public void removeLinkFromTable(DeviceContact link){
        for (DeviceContact deviceContact: revertedTable.get(link)){
            Log.d(TAG, String.format(
                    "Removing device %s due to removal of link %s",
                    deviceContact.getDeviceName(), link.getDeviceName()));
            mtable.remove(deviceContact);
        }
        revertedTable.remove(link);
        checkConsistency();
        Log.d(TAG, String.format("Removed link %s and all of its devices", link.getDeviceName()));
    }

    public Set<DeviceContact> getAllConnectedDevices(){
        return mtable.keySet();
    }

    private void logTable(boolean showReversed){
        Log.d(TAG, "Routing Table");
        for (DeviceContact deviceContact: mtable.keySet()){
            Log.d(TAG,
                    String.format("Device Name: %s Link: %s",
                            deviceContact.getDeviceName(), mtable.get(deviceContact).getDeviceName()));
        }
        if (showReversed){
            Log.d(TAG, "Reversed Table");
            for (DeviceContact link: revertedTable.keySet()){
                Log.d(TAG,
                        String.format(
                                "Link: %s Devices %s", link.getDeviceName(), Arrays.toString(revertedTable.get(link).toArray())));
            }
        }
    }

    private void checkConsistency(){
        int count = 0;
        ArrayList<DeviceContact> allDevices = new ArrayList<>();
        for(DeviceContact link: revertedTable.keySet()){
                HashSet<DeviceContact> devicesList = revertedTable.get(link);
                allDevices.addAll(devicesList);
                for (DeviceContact deviceContact: devicesList){
                    count ++;
                    if (mtable.get(deviceContact) != link){
                        Log.e(TAG,
                                String.format("Mismatch in tables for link %s and device %s",
                                        link.getDeviceName(), deviceContact.getDeviceName()));
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
