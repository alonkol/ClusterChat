package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RoutingTable {
    private static final String TAG="RoutingTable";
    private static final Integer INFINITY_HOP_COUNT = 15;
    private final Handler mUiConnectHandler;
    private HashMap<DeviceContact, DeviceContact> mtable;
    private HashMap<DeviceContact, HashSet<DeviceContact>> revertedTable;
    private HashMap<DeviceContact, Integer> hopCounts;

    public RoutingTable(){
        mtable = new HashMap<>();
        revertedTable = new HashMap<>();
        mUiConnectHandler = new Handler();
        hopCounts = new HashMap<>();
    }

    public DeviceContact getLink(DeviceContact deviceContact){
        return mtable.get(deviceContact);
    }

    public HashSet<DeviceContact> getAllDevicesForLink(DeviceContact dc){
        HashSet<DeviceContact> devices = revertedTable.get(dc);
        if (devices==null){
            return new HashSet<>();
        }
        return devices;
    }

    private void addDeviceToTable(DeviceContact deviceContact, DeviceContact linkDevice,
                                  Integer hopCount,
                                  boolean overrideIfExists, boolean finalize){
        if (hopCount >= INFINITY_HOP_COUNT){
            Log.d(TAG, "Device " + deviceContact + " will not be added since hop count is inf");
            return;
        }
        if (mtable.containsKey(deviceContact)){
            Log.i(TAG, "device "+ deviceContact.getShortStr() + " already exists in table");
            if (overrideIfExists){
                Log.d(TAG, "Override exists flag is on, removing and adding new");
                removeDeviceFromTable(deviceContact, false);
            }else{
                return;
            }
        }
        mtable.put(deviceContact, linkDevice);
        hopCounts.put(deviceContact, hopCount);
        Log.d(TAG, String.format(
                "Added device name %s and link %s to tables",
                deviceContact.getDeviceName(), linkDevice.getDeviceName()));
        if (!revertedTable.containsKey(linkDevice)){
            revertedTable.put(linkDevice, new HashSet<DeviceContact>());
        }
        revertedTable.get(linkDevice).add(deviceContact);
        if (finalize) {
            checkConsistency();
            shareRoutingInfo();
        }
    }

    public void addDeviceToTable(DeviceContact deviceContact, DeviceContact linkDevice,
                                 Integer hopCount, boolean finalize){
        addDeviceToTable(deviceContact, linkDevice,  hopCount, finalize, true);
    }

    public void removeDeviceFromTable(DeviceContact deviceContact){
        removeDeviceFromTable(deviceContact, true);
    }

    public void removeDeviceFromTable(DeviceContact deviceContact, boolean finalize){
        DeviceContact link = mtable.get(deviceContact);
        mtable.remove(deviceContact);
        hopCounts.remove(deviceContact);
        revertedTable.get(link).remove(deviceContact);
        removeDeviceFromUI(deviceContact);
        if (finalize) {
            checkConsistency();
            shareRoutingInfo();
        }
        Log.d(TAG, String.format("Removed device %s", deviceContact.getDeviceName()));
    }

    public void removeLinkFromTable(DeviceContact linkDevice){
        try{
            for (DeviceContact deviceContact: new HashSet<>(revertedTable.get(linkDevice))){
                Log.d(TAG, String.format(
                        "Removing device %s due to removal of link %s",
                        deviceContact.getShortStr(), linkDevice.getShortStr()));
                removeDeviceFromTable(deviceContact, false);
            }
            revertedTable.remove(linkDevice);
            checkConsistency();
            shareRoutingInfo();
            Log.d(TAG, String.format("Removed link %s and all of its devices",
                    linkDevice.getShortStr()));
        } catch (NullPointerException e){
            //TODO examine
            Log.e(TAG, "Failed to find devices for link " + linkDevice.getDeviceId());
        }

    }

    public Set<DeviceContact> getAllConnectedDevices(){
        return mtable.keySet();
    }

    private void logTable(boolean showReversed){
        Log.d(TAG, "Routing Table");
        for (DeviceContact deviceContact: mtable.keySet()){
            Log.d(TAG,
                    String.format("Device Name: %s Link: %s Hops: %s",
                            deviceContact.getDeviceName(), mtable.get(deviceContact).getDeviceName(),
                            Integer.toString(hopCounts.get(deviceContact))));
        }
        if (showReversed){
            Log.d(TAG, "Reversed Table");
            for (DeviceContact link: revertedTable.keySet()){
                Log.d(TAG,
                        String.format(
                                "Link: %s Devices %s", link.getDeviceName(),
                                Arrays.toString(revertedTable.get(link).toArray())));
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
                    if (!mtable.get(deviceContact).equals(link)){
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

    public ArrayList<DeviceContact> getAllNeighboursConnectedDevices() {
        ArrayList<DeviceContact> neighbours = new ArrayList<>();
        for (DeviceContact l: revertedTable.keySet()){
            if (hopCounts.get(l) == 1)
                neighbours.add(l);
        }
        return neighbours;
    }

    public String createRoutingData(DeviceContact recieverContact){
        StringBuilder routingData = new StringBuilder();
        String deviceInfo;
        for (Map.Entry<DeviceContact, HashSet<DeviceContact>> entry: revertedTable.entrySet()){
            DeviceContact l = entry.getKey();
            if (l == recieverContact){
                continue;
            }
            HashSet<DeviceContact> devices = entry.getValue();
            for (DeviceContact dc: devices){
                deviceInfo = String.format("%s#~#%s#~#%s",
                        dc.getDeviceId(), dc.getDeviceName(), Integer.toString(hopCounts.get(dc)));
                routingData.append(deviceInfo).append("\n");
            }
        }
        return routingData.toString();
    }

    public void mergeRoutingData(String routingData, DeviceContact senderContact){
        // prepare a list to make sure all devices related to this link are dealt
        HashSet<DeviceContact> currentLinkedDevices =
                new HashSet<>(getAllDevicesForLink(senderContact));
        currentLinkedDevices.remove(senderContact);

        Integer senderHopCount = hopCounts.get(senderContact);
        if (!routingData.equals("")) {
            for (String line : routingData.split("\n")) {
                String info[] = line.split("#~#");
                DeviceContact dc = new DeviceContact(info[0], info[1]);
                // mark device as dealt
                currentLinkedDevices.remove(dc);
                Integer hopCount = Integer.parseInt(info[2]);
                Integer currentHopCount = hopCounts.get(dc);
                Integer newCount = senderHopCount + hopCount;
                if (currentHopCount == null) {
                    // If not known of device yet add it to table and UI
                    addDeviceToTable(dc, senderContact, newCount, true);
                    addDeviceToUI(dc);
                } else if (newCount < currentHopCount) {
                    // If better reach update link and hop count
                    addDeviceToTable(dc, senderContact, newCount, true);
                }
            }
        }
        // If any device related to current link was not dealt meaning it must have been
        // disconnected so we remove it
        for (DeviceContact dc: currentLinkedDevices){
            removeDeviceFromTable(dc);
        }
    }

    public void removeDeviceFromUI(final DeviceContact deviceContact){
        // Update UI
        mUiConnectHandler.post(new Runnable() {
            public void run() {
                MainActivity.removeDeviceFromUi(deviceContact);
            }
        });
    }

    public void addDeviceToUI(final DeviceContact deviceContact){
        // Update UI
        mUiConnectHandler.post(new Runnable() {
            public void run() {
                MainActivity.addDeviceToUi(deviceContact);
            }
        });
    }

    public void shareRoutingInfo(){
        ArrayList<DeviceContact> neighbours =
                MainActivity.mRoutingTable.getAllNeighboursConnectedDevices();
        for (DeviceContact dc: neighbours){
            String data = MainActivity.mRoutingTable.createRoutingData(dc);
            MessageBundle routingBundle = new MessageBundle(data, MessageTypes.ROUTING,
                    MainActivity.myDeviceContact, dc);
            MainActivity.mDeliveryMan.sendMessage(routingBundle, dc);
        }
    }

}
