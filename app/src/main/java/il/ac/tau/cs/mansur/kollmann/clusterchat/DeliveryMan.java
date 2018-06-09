package il.ac.tau.cs.mansur.kollmann.clusterchat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;

import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.BluetoothThread;
import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.ConnectedThread;

public class DeliveryMan {

    private static final String TAG="DeliveryMan";
    public static final int MAX_BYTES_MESSAGE = 20 * 1024;
    private final HashSet<MessageBundle.PackageIdentifier> messagesToAck = new HashSet<>();
    private final HashSet<MessageBundle.PackageIdentifier> broadcastedMessages = new HashSet<>();

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact,
                               BluetoothThread thread){
        Log.d(TAG, "Sending message: "+ messageBundle+ " to " + addressContact.getShortStr());
        byte[] send = messageBundle.toJson().getBytes();
        try {
            thread.write(addPackageSize(send));
        } catch (IOException e){
            Log.e(TAG, "Can't send message", e);
            return false;
        }
        return true;

    }

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact){
        DeviceContact linkDevice = MainActivity.mRoutingTable.getLink(addressContact);
        if (linkDevice==null){
            return false;
        }
        ConnectedThread t =
                MainActivity.mBluetoothService.getConnectedThread(linkDevice);
        if (t==null){
            Log.e(TAG, "No matching thread found for device contact: " + addressContact.getShortStr() +
                    "\nDisposing of message" + messageBundle);
            MainActivity.mRoutingTable.removeLinkFromTable(linkDevice);
            return false;
        }
        return sendMessage(messageBundle, addressContact, t);
    }


    public void prepareAndBroadcastMessage(String message) {
        Log.d(TAG, "got message to broadcast: " + message);
        message = "Group Message: " + message;
        MessageBundle messageBundle = new MessageBundle(
                message, MessageTypes.BROADCAST, MainActivity.myDeviceContact, MainActivity.myDeviceContact);
        broadcastMessage(messageBundle);
        for (DeviceContact dc: MainActivity.mRoutingTable.getAllConnectedDevices()){
            MainActivity.mConversationManager.addMessage(
                    dc, new BaseMessage(message));
        }
    }

    public boolean broadcastMessage(MessageBundle messageBundle){
        Log.d(TAG, "Broadcasting message: " + messageBundle + " to all neighbouring devices");
        MessageBundle.PackageIdentifier packageIdentifier = messageBundle.getIdentifier();
        if (broadcastedMessages.contains(packageIdentifier)){
            Log.d(TAG, "Already broadcasted this message so dropping");
            return false;
        }
        broadcastedMessages.add(messageBundle.getIdentifier());
        for (DeviceContact dc: MainActivity.mRoutingTable.getAllNeighboursConnectedDevices()){
            sendMessage(messageBundle, dc);
        }
        return true;
    }


    public void sendRoutingData(DeviceContact deviceContact, String data, boolean reply) {
        MessageTypes type = reply ? MessageTypes.ROUTINGREPLY : MessageTypes.ROUTING;
        MessageBundle routingBundle = MessageBundle.routingMessageBundle(data, type,
                MainActivity.myDeviceContact, deviceContact);

        sendMessage(routingBundle, deviceContact);
    }

    public void replyRoutingData(DeviceContact deviceContact) {
        String data = MainActivity.mRoutingTable.createRoutingData(deviceContact);
        sendRoutingData(deviceContact, data, true);
    }

    public void checkAckMessage(MessageBundle messageBundle) {
        // If the message acks a file sent, then add message to the chat to let the user know
        // that the file has been received
        MessageBundle.PackageIdentifier packageIdentifier = new MessageBundle.PackageIdentifier(
                Integer.parseInt(messageBundle.getMetadata("AckID")), messageBundle.getSender());
        if (messagesToAck.contains(packageIdentifier)){
            String message = "File arrived at its destination";
            MainActivity.mConversationManager.addMessage(
                    messageBundle.getSender(), new BaseMessage(message));

            messagesToAck.remove(packageIdentifier);
        }
    }


    private byte[] addPackageSize(byte[] data){
        int len = data.length;
        byte[] size = ByteBuffer.allocate(8).putInt(len).array();
        byte[] result = new byte[8 + data.length];
        System.arraycopy(size, 0, result, 0, size.length);
        System.arraycopy(data, 0, result, size.length, data.length);
        return result;
    }


    public void addMessageToWaitingForAck(MessageBundle.PackageIdentifier packageIdentifier) {
        messagesToAck.add(packageIdentifier);
    }
}
