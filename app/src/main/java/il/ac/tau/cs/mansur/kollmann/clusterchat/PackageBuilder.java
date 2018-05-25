package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Objects;

public class PackageBuilder extends Thread{
    public final String TAG = "PackageBuilder";
    private HashMap<PackageIdentifier, ArrayList<MessageBundle>> constructionPackages;
    private MainActivity mainActivity;

    public PackageBuilder(MainActivity mainActivity){
        constructionPackages = new HashMap<>();
        this.mainActivity = mainActivity;
    }

    public void start() {
        MessageBundle mb;
        while (true){
            mb = MainActivity.packageQueue.getPackage();
            while (mb != null){
                processPackage(mb);
                mb = MainActivity.packageQueue.getPackage();

            }

            try {
                sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void processPackage(MessageBundle mb){
        Log.d(TAG, "Processing package " + mb);
        PackageIdentifier packageIdentifier = new PackageIdentifier(mb.getMessageID(),
                mb.getSender());
        if (!constructionPackages.containsKey(packageIdentifier)){
            constructionPackages.put(packageIdentifier, new ArrayList<MessageBundle>());
        }
        constructionPackages.get(packageIdentifier).add(mb);
        try {
            checkIfPackageComplete(packageIdentifier);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkIfPackageComplete(PackageIdentifier packageIdentifier) throws IOException {
        ArrayList<MessageBundle> packages = constructionPackages.get(packageIdentifier);
        MessageBundle mb = packages.get(0);
        int totalPackages = Integer.parseInt(mb.getMetadata("totalPackages"));
        if (packages.size() != totalPackages) {
            return;
        }
        MessageBundle[] packagesArr = new MessageBundle[totalPackages];
        for (int i=0; i<totalPackages; i++){
            mb = packages.get(i);
            packagesArr[Integer.parseInt(mb.getMetadata("packageIndex"))] = mb;
        }
        Log.d(TAG, "Completed package for " + packageIdentifier);
        constructionPackages.remove(packageIdentifier);

        byte[] fileBytes = new byte[totalPackages * DeliveryMan.MAX_BYTES_MESSAGE];
        for (int i=0; i<totalPackages; i++){
            byte[] b = Base64.decode(packagesArr[i].getMessage(), Base64.DEFAULT);
            System.arraycopy(b, 0, fileBytes, i * DeliveryMan.MAX_BYTES_MESSAGE, b.length);
        }
        Log.d(TAG, "Package is constructed and passed");
        handleCompleteFile(mb.getSender(), fileBytes,
                Integer.parseInt(mb.getMetadata("totalFileSize")),
                mb.getMetadata("fileName"), mb);

    }

    private void handleCompleteFile(DeviceContact senderContact,
                                    byte[] fileBytes, int fileSize, String fileName,
                                    MessageBundle mb){
        try {
            mainActivity.writeFileToDevice(fileBytes, fileSize, fileName);
        } catch (IOException e) {
            Log.e(TAG, "Cannot save file to device", e);
        }

        // Add to chat
        MainActivity.mConversationManager.addMessage(
                senderContact, new BaseMessage("File received", senderContact.getDeviceId()));
        // Send Ack message
        MainActivity.mDeliveryMan.sendMessage(
                MessageBundle.AckBundle(mb), senderContact);
    }


    private class PackageIdentifier{
        private Integer messageID;
        private DeviceContact deviceContact;

        private PackageIdentifier(Integer messageID, DeviceContact deviceContact) {
            this.messageID = messageID;
            this.deviceContact = deviceContact;
        }

        @Override
        public String toString() {
            return "PackageIdentifier{" +
                    "messageID=" + messageID +
                    ", deviceContact=" + deviceContact +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PackageIdentifier that = (PackageIdentifier) o;
            return Objects.equals(messageID, that.messageID) &&
                    Objects.equals(deviceContact, that.deviceContact);
        }

        @Override
        public int hashCode() {

            return Objects.hash(messageID, deviceContact);
        }
    }
}
