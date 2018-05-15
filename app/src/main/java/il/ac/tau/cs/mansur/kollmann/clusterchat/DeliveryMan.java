package il.ac.tau.cs.mansur.kollmann.clusterchat;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DeliveryMan {

    private static final String TAG="DeliveryMan";
    private static final int MAX_BYTES_MESSAGE = 5;

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact,
                               BluetoothService.BluetoothThread thread){
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

    private byte[] addPackageSize(byte[] data){
        int len = data.length;
        byte[] size = ByteBuffer.allocate(4).putInt(len).array();
        byte[] result = new byte[4 + data.length];
        System.arraycopy(size, 0, result, 0, size.length);
        System.arraycopy(data, 0, result, size.length, data.length);
        return result;
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


    public void sendRoutingData(DeviceContact deviceContact, String data, boolean reply) {
        MessageTypes type = reply ? MessageTypes.ROUTINGREPLY : MessageTypes.ROUTING;
        MessageBundle routingBundle = new MessageBundle(data, type,
                MainActivity.myDeviceContact, deviceContact);
        sendMessage(routingBundle, deviceContact);
    }

    public void replyRoutingData(DeviceContact deviceContact) {
        String data = MainActivity.mRoutingTable.createRoutingData(deviceContact);
        sendRoutingData(deviceContact, data, true);
    }

    public void sendFile(String filePath, DeviceContact addressContact){
        String test = "test1test2test3test4test";
        byte[] testBytes = test.getBytes();
        List<byte[]> chunks = splitEqually(testBytes, MAX_BYTES_MESSAGE, testBytes.length);
        int messageID = MainActivity.getNewMessageID();
        for (int i=0; i<chunks.size(); i++){
            MessageBundle mb = new MessageBundle(
                    new String(chunks.get(i)), MessageTypes.FILE, MainActivity.myDeviceContact,
                    addressContact, messageID);
            mb.addMetadata("totalPackages", Integer.toString(chunks.size()));
            mb.addMetadata("totalFileSize", Integer.toString(testBytes.length));
            mb.addMetadata("packageIndex", Integer.toString(i));
            sendMessage(mb, addressContact);
        }
    }

    private List<byte[]> splitEqually(byte[] testBytes, int pieceSize, int totalSize) {
        int current = 0;
        byte[] chunk;
        List <byte[]> chunks = new ArrayList<>();
        while (current<totalSize){
            chunk = new byte[pieceSize];
            for (int i=current; i<current+pieceSize && i<totalSize; i++){
                chunk[i % pieceSize] = testBytes[i];
            }
            chunks.add(chunk);
            current+=pieceSize;
        }
        return chunks;
    }
}
