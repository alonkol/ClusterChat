package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.media.MediaPlayer;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

class PackageBuilder extends Thread{
    private final static String TAG = "PackageBuilder";
    private final MediaPlayer mMediaPlayerOnNewMessage;
    private final HashMap<MessageBundle.PackageIdentifier, ArrayList<MessageBundle>> constructionPackages;
    private final MainActivity mainActivity;
    private final HashMap<MessageBundle.PackageIdentifier, Long> packageTimes;

    PackageBuilder(MainActivity mainActivity){
        this.mainActivity = mainActivity;
        constructionPackages = new HashMap<>();
        mMediaPlayerOnNewMessage = MediaPlayer.create(mainActivity, R.raw.open_ended);
        packageTimes = new HashMap<>();
        setName("PackageBuilder");
    }

    public void start() {
        MessageBundle mb;
        // This will die when the main thread is destroyed
        while (true){
            mb = MainActivity.packageQueue.getPackage();
            while (mb != null){
                processPackage(mb);
                mb = MainActivity.packageQueue.getPackage();
            }
            cleanUpLostPackages();
            try {
                sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

     private void cleanUpLostPackages() {
        long now = Calendar.getInstance().getTime().getTime();
        ArrayList<MessageBundle.PackageIdentifier> identifiersToRemove = new ArrayList<>();
        for (Map.Entry<MessageBundle.PackageIdentifier, Long> entry: packageTimes.entrySet()){
            Long packageLastTime = entry.getValue();
            long diffInSeconds = (now - packageLastTime) / 1000;
            if (diffInSeconds > 100){
                identifiersToRemove.add(entry.getKey());
            }
        }
        for (MessageBundle.PackageIdentifier pi: identifiersToRemove){
            Log.d(TAG, "Removing " + pi + " since its been long since last package arrived");
            packageTimes.remove(pi);
            constructionPackages.remove(pi);
        }

     }

     private void processPackage(MessageBundle mb){
        Log.d(TAG, "Processing package " + mb);
        MessageBundle.PackageIdentifier packageIdentifier = mb.getIdentifier();
        if (!constructionPackages.containsKey(packageIdentifier)){
            constructionPackages.put(packageIdentifier, new ArrayList<MessageBundle>());
        }
        constructionPackages.get(packageIdentifier).add(mb);
        packageTimes.put(packageIdentifier, Calendar.getInstance().getTime().getTime());
        checkIfPackageComplete(packageIdentifier);

    }

    private void checkIfPackageComplete(MessageBundle.PackageIdentifier packageIdentifier){
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
        packageTimes.remove(packageIdentifier);

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

    private void handleCompleteFile(final DeviceContact senderContact,
                                    byte[] fileBytes, int fileSize, String fileName,
                                    MessageBundle mb){
        try {
            mainActivity.writeFileToDevice(fileBytes, fileSize, fileName);
        } catch (IOException e) {
            Log.e(TAG, "Cannot save file to device", e);
        }

        // Add to chat
        String message = "File received from " + senderContact.getDeviceName() +
                "\ngoto ClusterChat/"+ fileName + " to find it";

        MainActivity.mConversationManager.addMessage(
                senderContact, new BaseMessage(message, senderContact.getDeviceId()));
        // Send Ack message
        MainActivity.mDeliveryMan.sendMessage(
                MessageBundle.AckBundle(mb), senderContact);

        // Update unread messages count
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.mConnectedDevicesArrayAdapter.newMessage(senderContact);
            }
        });
        // Play sound
        try {
            mMediaPlayerOnNewMessage.start();
        } catch (Exception e) {
            // ignore
        }
    }


}
