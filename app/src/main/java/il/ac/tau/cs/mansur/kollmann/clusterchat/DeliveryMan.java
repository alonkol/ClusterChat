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
import java.util.List;

public class DeliveryMan {

    private static final String TAG="DeliveryMan";
    public static final int MAX_BYTES_MESSAGE = 15 * 1024;

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

    public boolean sendMessage(MessageBundle messageBundle, DeviceContact addressContact){
        DeviceContact linkDevice = MainActivity.mRoutingTable.getLink(addressContact);
        BluetoothService.ConnectedThread t =
                MainActivity.mBluetoothService.getConnectedThread(linkDevice);
        if (t==null){
            Log.e(TAG, "No matching thread found for device contact: " + addressContact.getShortStr() +
                    "\nDisposing of message" + messageBundle);
            return false;
        }
        return sendMessage(messageBundle, addressContact, t);
    }

    public boolean broadcastMessage(MessageBundle messageBundle){
        Log.d(TAG, "Broadcasting message: " + messageBundle + " to all neighbouring devices");
        MessageBundle.PackageIdentifier packageIdentifier = messageBundle.getIdentifier();
        if (MainActivity.brodacastedMessages.contains(packageIdentifier)){
            Log.d(TAG, "Already broadcasted this message so dropping");
            return false;
        }
        MainActivity.brodacastedMessages.add(messageBundle.getIdentifier());
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

    public void sendFile(Uri uri, String fileName, DeviceContact addressContact, ContentResolver contentResolver){
        byte[] fileContent;
        try {
            fileContent = readBytesFromUri(uri, contentResolver);
        } catch (IOException e) {
            Log.e(TAG, "Failed at reading file data: " + uri.toString(), e);
            return;
        }
        int fileSize = fileContent.length;
        Log.d(TAG, "Sending file: " + fileName + "with size: " + Integer.toString(fileSize));

        List<byte[]> chunks = splitEqually(fileContent, MAX_BYTES_MESSAGE, fileSize);
        int messageID = MainActivity.getNewMessageID();
        for (int i=0; i<chunks.size(); i++){
            MessageBundle mb = new MessageBundle(
                    Base64.encodeToString(chunks.get(i), Base64.DEFAULT), MessageTypes.FILE,
                    MainActivity.myDeviceContact,
                    addressContact, messageID);
            mb.addMetadata("totalPackages", Integer.toString(chunks.size()));
            mb.addMetadata("totalFileSize", Integer.toString(fileSize));
            mb.addMetadata("packageIndex", Integer.toString(i));
            mb.addMetadata("fileName", fileName);
            sendMessage(mb, addressContact);
        }
        // We would like to tell the sending device when the file that he has sent has arrived at his destination
        // so we keep it in a set, and upon ack received for it we will add a message to the chat window
        MainActivity.messagesToAck.add(new MessageBundle.PackageIdentifier(messageID, addressContact));
    }

    private byte[] readBytesFromUri(Uri uri, ContentResolver contentResolver) throws IOException {
        InputStream iStream = contentResolver.openInputStream(uri);
        byte[] fileBytes = getBytes(iStream);
        iStream.close();
        return fileBytes;
    }

    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }


    private byte[] addPackageSize(byte[] data){
        int len = data.length;
        byte[] size = ByteBuffer.allocate(4).putInt(len).array();
        byte[] result = new byte[4 + data.length];
        System.arraycopy(size, 0, result, 0, size.length);
        System.arraycopy(data, 0, result, size.length, data.length);
        return result;
    }

    private List<byte[]> splitEqually(byte[] testBytes, int pieceSize, int totalSize) {
        int current = 0;
        byte[] chunk;
        List <byte[]> chunks = new ArrayList<>();
        while (current<totalSize){
            chunk = new byte[pieceSize];
            for (int i=current; i < current + pieceSize && i<totalSize; i++){
                chunk[i % pieceSize] = testBytes[i];
            }
            chunks.add(chunk);
            current+=pieceSize;
        }
        return chunks;
    }
}
