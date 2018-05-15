package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

import java.util.UUID;

public class myMessageHandler extends Handler {
    private final String TAG = "MessageHandler";
    static final int MESSAGE_IN = 0;
    static final int MESSAGE_OUT = 1;
    private final MediaPlayer mMediaPlayerOnNewMessage;

    myMessageHandler(Context context) {
        mMediaPlayerOnNewMessage = MediaPlayer.create(context, R.raw.open_ended);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_IN:
                handleIncomingMessage(msg);
                break;
            case MESSAGE_OUT:
                handleOutgoingMessage(msg);
                break;
        }
    }

    private void handleOutgoingMessage(Message msg){
        byte[] buffer = (byte[]) msg.obj;
        String readMessage = new String(buffer);
        MessageBundle messageBundle = MessageBundle.fromJson(readMessage);
        Log.d(TAG, "Handler caught outgoing bundle " + messageBundle);
    }


    private void handleIncomingMessage(Message msg){
        byte[] buffer = (byte[]) msg.obj;
        String readMessage = new String(buffer, 0, msg.arg1);

        try {
            MessageBundle messageBundle = MessageBundle.fromJson(readMessage);
            Log.d(TAG, "Handler caught incoming message: " + messageBundle);
            // Decrease package TTL and check if still valid
            messageBundle.decreaseTTL();
            if (messageBundle.getTTL() == -1){
                Log.d(TAG, "Dropping " + messageBundle + "since TTL is less than zero");
                return;
            }
            DeviceContact receiverContact = messageBundle.getReceiver();
            if (!MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00") &&
                    !receiverContact.getDeviceId().equals(MainActivity.myDeviceContact.getDeviceId())){
                Log.i(TAG, "Current device isn't the address for current message, passing along");
                MainActivity.mDeliveryMan.sendMessage(messageBundle, receiverContact);
            }
            switch (messageBundle.getMessageType()){
                case TEXT:
                    handleIncomingTextMessage(messageBundle);
                    break;
                case FILE:
                    handleIncomingFileMessage(messageBundle);
                    break;
                case HS:
                    handleIncomingHSMessage(messageBundle);
                    break;
                case ACK:
                    handleIncomingACKMessage(messageBundle);
                    break;
                case ROUTING:
                    handleIncomingRoutingMessage(messageBundle, false);
                    break;
                case ROUTINGREPLY:
                    handleIncomingRoutingMessage(messageBundle, true);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize incoming message", e);
        }
    }

    private void handleIncomingFileMessage(MessageBundle messageBundle) {
        MainActivity.packageQueue.addPackage(messageBundle);
    }

    private void handleIncomingTextMessage(MessageBundle messageBundle){
        DeviceContact senderContact = messageBundle.getSender();
        // Add to chat
        MainActivity.mConversationManager.addMessage(senderContact,
                new BaseMessage(messageBundle.getMessage(), senderContact.getDeviceId()));
        // Send Ack message
        MainActivity.mDeliveryMan.sendMessage(
                MessageBundle.AckBundle(messageBundle), senderContact);

        // Update unread messages count
        MainActivity.mConnectedDevicesArrayAdapter.newMessage(senderContact);


        // Play sound
        try {
            mMediaPlayerOnNewMessage.start();
        } catch (Exception e) {
            // ignore
        }
    }

    private void handleIncomingHSMessage(MessageBundle messageBundle){
        if (MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00")) {
            Log.i(TAG, "Initializing myDeviceContact address to " +
                    messageBundle.getReceiver().getDeviceId());
            MainActivity.myDeviceContact = messageBundle.getReceiver();
        }
        String uuid = messageBundle.getMetadata("UUID");
        if (uuid != null) {
            DeviceContact device = messageBundle.getSender();
            BluetoothService.ConnectThread thread = BluetoothService.mConnectThreads.get(device);
            if (thread == null) {
                Log.e(TAG, "Incoming UUID - requesting thread not found.");
                return;
            }
            thread.mmUuid = UUID.fromString(uuid);
        }
    }

    private void handleIncomingRoutingMessage(MessageBundle messageBundle, boolean reply) {
        DeviceContact senderContact = messageBundle.getSender();
        Log.i(TAG, "Merging routing data received from " + senderContact.getShortStr());
        boolean changeHappened =
                MainActivity.mRoutingTable.mergeRoutingData(messageBundle.getMessage(), senderContact);
        // If change happened we are already sharing the table to the sender,
        // but if not we will share our routing info with the sender
        if (!changeHappened && !reply){
            MainActivity.mDeliveryMan.replyRoutingData(senderContact);
        }
    }

    private void handleIncomingACKMessage(MessageBundle messageBundle) {
        Log.i(TAG, "Message " + messageBundle.getMetadata("AckID") +
                " has been acked");

        // TODO: Single tick on ACK?
    }
}
