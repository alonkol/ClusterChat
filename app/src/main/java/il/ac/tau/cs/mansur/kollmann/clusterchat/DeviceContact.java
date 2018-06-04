package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.bluetooth.BluetoothDevice;

public class DeviceContact {
    private String deviceId;
    private String deviceName;
    private int unreadMessages = 0;

    public DeviceContact(BluetoothDevice device) {
        this(device.getAddress(), device.getName());
    }

    DeviceContact(String deviceId){
        this(deviceId, null);
    }

    DeviceContact(String deviceId, String deviceName){
        this.deviceId = deviceId;
        this.deviceName = deviceName == null ? "Unknown" : deviceName;
    }

    void clearUnread() {
        unreadMessages = 0;
    }

    int IncrementAndGetUnread() {
        unreadMessages += 1;
        return unreadMessages;
    }

    public String toString(){
        return deviceName + "\n" + deviceId;
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

    @Override
    public int hashCode() {
        return deviceId.hashCode();
    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public String getDeviceId(){
        return this.deviceId;
    }

    public String getShortStr() {
        return this.deviceName + "/" + this.deviceId;
    }

    int getUnreadMessages() { return this.unreadMessages; }
}