package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FileSender extends Thread {
    private final String PREFIXTAG = "FileSender-";
    private final String TAG;
    private final String fileName;
    private final DeliveryMan deliveryMan;
    private final Uri uri;
    private final DeviceContact addressContact;
    private final ContentResolver contentResolver;
    private ArrayList<MessageBundle> messagesToSend;

    FileSender(DeliveryMan mDeliveryMan, Uri uri, String fileName, DeviceContact addressContact, ContentResolver contentResolver) {
        this.deliveryMan = mDeliveryMan;
        this.uri = uri;
        this.fileName = fileName;
        this.addressContact = addressContact;
        this.contentResolver = contentResolver;
        this.messagesToSend = new ArrayList<>();
        TAG = PREFIXTAG + this.addressContact.getDeviceName() + "/" + fileName;
        setName(TAG);
    }

    public void run() {
        Log.d(TAG, "Starting to work on " + fileName);
        prepareMessageBundles();
        Log.d(TAG, "Packages are ready, starting to send");
        sendMessages();
        Log.d(TAG, "All done, killing myself");
    }

    private void sendMessages() {
        MessageBundle mb;
        boolean result;
        Log.d(TAG, "Start sending " + Integer.toString(messagesToSend.size()) + " messages for file " + fileName);
        while (!messagesToSend.isEmpty()){
            mb = messagesToSend.remove(0);
            result = deliveryMan.sendMessage(mb, this.addressContact);
            if (!result){
                Log.e(TAG, "Couldn't send all messages for file " + fileName);
                return;
            }
            try {
                sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "Finished sending messages for file " + fileName);
    }

    private void prepareMessageBundles(){
        byte[] fileContent;
        try {
            fileContent = readBytesFromUri(uri, contentResolver);
        } catch (IOException e) {
            Log.e(TAG, "Failed at reading file data: " + uri.toString(), e);
            return;
        }
        int fileSize = fileContent.length;
        Log.d(TAG, "The file: " + fileName + "has size: " + Integer.toString(fileSize));

        List<byte[]> chunks = splitEqually(fileContent, DeliveryMan.MAX_BYTES_MESSAGE, fileSize);
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
            messagesToSend.add(mb);
        }

        // We would like to tell the sending device when the file that he has sent has arrived at his destination
        // so we keep it in a set, and upon ack received for it we will add a message to the chat window
        deliveryMan.addMessageToWaitingForAck(new MessageBundle.PackageIdentifier(messageID, addressContact));

    }

    private byte[] readBytesFromUri(Uri uri, ContentResolver contentResolver) throws IOException {
        InputStream iStream = contentResolver.openInputStream(uri);
        byte[] fileBytes = getBytes(iStream);
        iStream.close();
        return fileBytes;
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
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
