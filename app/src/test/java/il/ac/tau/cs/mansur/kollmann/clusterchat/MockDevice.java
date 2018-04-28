package il.ac.tau.cs.mansur.kollmann.clusterchat;

public class MockDevice {
    private static final String TAG = "Mock Device";
    private RoutingTable<Link> routingTable;
    DeviceContact deviceContact;
    private myMessageHandler messageHandler;

    public MockDevice(String name, String id){
        this.deviceContact = new DeviceContact(id, name);
        this.routingTable = new RoutingTable();
        this.messageHandler = new myMessageHandler();
    }

    public void initiateConnection(MockDevice newDevice){
        this.routingTable.addDeviceToTable(newDevice.deviceContact, newDevice.deviceContact,
                true);
        newDevice.establishConnection(this);
        this.sendMessage(newDevice, "HI", MessageTypes.HS);
    }

    public void establishConnection(MockDevice connectingDevice){
        this.routingTable.addDeviceToTable(connectingDevice.deviceContact,
                connectingDevice.deviceContact, true);
        this.sendMessage(connectingDevice, "HI", MessageTypes.HS);

    }

    public void sendMessage(MockDevice recieverDevice, String msg, MessageTypes type){
        System.out.println(String.format("%s sending message %s (type: %s) to %s",
                this.deviceContact.getDeviceName(), msg, type,
                recieverDevice.deviceContact.getDeviceName()));
        DeviceContact dc = routingTable.getLink(recieverDevice.deviceContact);
        MockDevice device = l.getDevice(recieverDevice.deviceContact);
        MessageBundle mb = new MessageBundle(msg, type, this.deviceContact, recieverDevice.deviceContact);

        device.receiveMessage(this, l, mb);
    }

    public void receiveMessage(MockDevice d, Link l, MessageBundle mb){
        System.out.println(String.format("%s received message %s (type: %s) from %s",
                this.deviceContact.getDeviceName(), mb.getMessage(), mb.getMessageType()
                ,d.deviceContact.getDeviceName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MockDevice that = (MockDevice) o;

        return deviceContact.equals(that.deviceContact);
    }

    @Override
    public int hashCode() {
        return deviceContact.hashCode();
    }
}
