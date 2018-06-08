package il.ac.tau.cs.mansur.kollmann.clusterchat;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.BluetoothThread;
import il.ac.tau.cs.mansur.kollmann.clusterchat.Connection.ConnectedThread;

public class DeliveryMan {

    private static final String TAG="DeliveryMan";
    public static final int MAX_BYTES_MESSAGE = 15 * 1024;
    private HashSet<MessageBundle.PackageIdentifier> messagesToAck = new HashSet<>();
    private HashSet<MessageBundle.PackageIdentifier> brodacastedMessages = new HashSet<>();

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
        if (brodacastedMessages.contains(packageIdentifier)){
            Log.d(TAG, "Already broadcasted this message so dropping");
            return false;
        }
        brodacastedMessages.add(messageBundle.getIdentifier());
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
            MainActivity.mConversationManager.addMessage(
                    messageBundle.getSender(), new BaseMessage("File arrived at its destination"));
            messagesToAck.remove(packageIdentifier);
        }
    }


    private byte[] addPackageSize(byte[] data){
        int len = data.length;
        byte[] size = ByteBuffer.allocate(4).putInt(len).array();
        byte[] result = new byte[4 + data.length];
        System.arraycopy(size, 0, result, 0, size.length);
        System.arraycopy(data, 0, result, size.length, data.length);
        return result;
    }


    public void addMessageToWaitingForAck(MessageBundle.PackageIdentifier packageIdentifier) {
        messagesToAck.add(packageIdentifier);
    }
}
