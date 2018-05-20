package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;

import java.util.HashMap;
import java.util.Objects;

public class PackageBuilder extends Thread{
    public final String TAG = "PackageBuilder";
    private HashMap<PackageIdentifier, ArrayList<MessageBundle>> constructionPackages;

    public PackageBuilder(){
        constructionPackages = new HashMap<>();
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
        checkIfPackageComplete(packageIdentifier);
    }

    private void checkIfPackageComplete(PackageIdentifier packageIdentifier) {
        ArrayList<MessageBundle> packages = constructionPackages.get(packageIdentifier);
        MessageBundle mb = packages.get(0);
        int totalPackages = Integer.parseInt(mb.getMetadata("totalPackages"));
        if (packages.size() != totalPackages) {
            return;
        }
        Log.d(TAG, "Completed package for " + packageIdentifier);
        MessageBundle[] packagesArr = new MessageBundle[totalPackages];
        for (int i=0; i<totalPackages; i++){
            mb = packages.get(i);
            packagesArr[Integer.parseInt(mb.getMetadata("packageIndex"))] = mb;
        }
        StringBuilder fullMessage = new StringBuilder();
        for (int i=0; i<totalPackages; i++){
            fullMessage.append(packagesArr[i].getMessage());
        }
        fullMessage.delete(Integer.parseInt(mb.getMetadata("totalFileSize")),
                fullMessage.length());

        Log.d(TAG, "Package is constructed and passed");
        MessageBundle constructedBundle = MessageBundle.ConstructedBundle(
                fullMessage.toString(), mb);
        handleConstructedMessage(constructedBundle);
    }

    private void handleConstructedMessage(MessageBundle constructedBundle) {
        // for testing we treat this message as text and deal with it regularly
        // TODO we should treat this as files (save to directory, show in UI)
        DeviceContact senderContact = constructedBundle.getSender();
        // Add to chat
        MainActivity.mConversationManager.addMessage(
                senderContact, new BaseMessage(constructedBundle.getMessage(), senderContact.getDeviceId()));
        // Send Ack message
        MainActivity.mDeliveryMan.sendMessage(
                MessageBundle.AckBundle(constructedBundle), senderContact);
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
