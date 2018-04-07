package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothDevice;

public class DeviceContact {
    private String deviceId;
    private String deviceName;

    DeviceContact(BluetoothDevice device) {
        this(device.getAddress(), device.getName());
    }

    DeviceContact(String deviceId){
        this(deviceId, null);
    }

    DeviceContact(String deviceId, String deviceName){
        this.deviceId = deviceId;
        this.deviceName = deviceName == null ? "Unknown" : deviceName;
    }

    public String toString(){
        return deviceId + "\n" + deviceName;
    }

    @Override
    public boolean equals(Object o){
        if (o instanceof DeviceContact)
        {
            DeviceContact dc = (DeviceContact) o;
            return this.deviceId.equals(dc.deviceId);
        }
        return false;
    }
}