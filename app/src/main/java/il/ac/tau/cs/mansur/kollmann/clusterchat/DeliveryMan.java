package il.ac.tau.cs.mansur.kollmann.clusterchat;
import android.util.Log;
import java.io.IOException;

public class DeliveryMan {

    private static final String TAG="DeliveryMan";

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact,
                               BluetoothService.BluetoothThread thread){
        Log.d(TAG, "Sending message: "+ messageBundle+ " to " + addressContact.getShortStr());
        byte[] send = messageBundle.toJson().getBytes();
        try {
            thread.write(send);
        } catch (IOException e){
            Log.e(TAG, "Can't send message", e);
            return false;
        }
        return true;

    }

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact){
        DeviceContact linkDevice = MainActivity.mRoutingTable.getLink(addressContact);
        BluetoothService.ConnectedThread t =
                MainActivity.mBluetoothService.getConnectedThread(linkDevice);
        if (t==null){
            Log.e(TAG, "No matching thread found for device contact: " + addressContact +
                    "\nDisposing of message" + messageBundle);
            return false;
        }
        return sendMessage(messageBundle, addressContact, t);
    }

    public boolean broadcastMessage(MessageBundle messageBundle){
        Log.d(TAG, "Broadcasting message: " + messageBundle + " to all neighbouring devices");
        for (DeviceContact dc: MainActivity.mRoutingTable.getAllNeighboursConnectedDevices()){
            sendMessage(messageBundle, dc);
        }
        return true;
    }


    public void sendRoutingData(DeviceContact deviceContact, String data) {
        MessageBundle routingBundle = new MessageBundle(data, MessageTypes.ROUTING,
                MainActivity.myDeviceContact, deviceContact);
        sendMessage(routingBundle, deviceContact);
    }

    public void replyRoutingData(DeviceContact deviceContact) {
        String data = MainActivity.mRoutingTable.createRoutingData(deviceContact);
        sendRoutingData(deviceContact, data);
    }
}
