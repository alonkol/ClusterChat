package il.ac.tau.cs.mansur.kollmann.clusterchat;

import java.util.ArrayList;

public class MockDevice {
    private final ArrayList<String> messages;
    private final RoutingTable routingTable;
    final DeviceContact deviceContact;
    private final MockNetwork network;

    public MockDevice(String id, MockNetwork network){
        this.deviceContact = new DeviceContact(id, id);
        this.routingTable = new RoutingTable();
        this.network = network;
        this.messages = new ArrayList<>();
    }

    public void connect(MockDevice b) {
        this.routingTable.addDeviceToTable(b.deviceContact, b.deviceContact, 1, true);
    }

    public void disconnect(MockDevice b) {
        this.routingTable.removeDeviceFromTable(b.deviceContact);
    }

    public ArrayList<String> showRouting() {
        System.out.println("Routing table for device " + deviceContact.getDeviceName());
        ArrayList<String> routingData = new ArrayList<String>();
        for (DeviceContact d: routingTable.getAllConnectedDevices()){
            routingData.add(d.getDeviceName());
            System.out.println(d.getDeviceName());
        }
        return routingData;
    }

    public void sendMessage(MockDevice reciever, MessageBundle msg){
        DeviceContact nextHop = routingTable.getLink(reciever.deviceContact);
        if (nextHop==null){
            System.out.println("No link was found from device " + this + "to " + reciever);
            return;
        }
        MockDevice nextHopDevice = network.getDevice(nextHop.getDeviceId());
        System.out.println(String.format("%s is sending messege %s to %s", this, msg, reciever));
        nextHopDevice.recieveMessage(this, msg);
    }

    private void recieveMessage(MockDevice sender, MessageBundle msg){
        System.out.println(String.format("%s recieved messege %s from %s", this, msg, sender));
        messages.add(msg.getMessage());
        handleIncomingMessage(msg);
    }

    private void handleIncomingMessage(MessageBundle msg) {
        System.out.println(this + "is handling incoming message");
    }

    @Override
    public int hashCode() {
        return deviceContact.hashCode();
    }

    @Override
    public String toString() {
        return "MockDevice{" +
                "deviceContact=" + deviceContact.getDeviceId() +
                '}';
    }

    public void broadcast(MessageTypes type, String msg) {
        for (DeviceContact dc: routingTable.getAllNeighboursConnectedDevices()){
            network.sendMessage(this, network.getDevice(dc.getDeviceId()), type, msg);
        }
    }

    public ArrayList<String> getMessages(){
        return this.messages;
    }
}
