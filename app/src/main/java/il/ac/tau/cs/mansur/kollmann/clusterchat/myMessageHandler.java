package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.UUID;

public class myMessageHandler extends Handler {
    private final String TAG = "MessageHandler";
    static final int MESSAGE_IN = 0;
    static final int MESSAGE_OUT = 1;


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
        MessageBundle messageBundle = MessageBundle.fromJson(readMessage);
        Log.d(TAG, "Handler caught incoming message: " + messageBundle);
        // Decrease package TTL and check if still valid
        messageBundle.decreaseTTL();
        if (messageBundle.getTTL() == -1){
            Log.d(TAG, "Dropping " + messageBundle + "since TTL is less than zero");
            return;
        }
        DeviceContact receiverContact = messageBundle.getReceiver();
        if (receiverContact != MainActivity.myDeviceContact) {
            Log.i(TAG, "Current device isn't the address for current message, passing along");
            MainActivity.mDeliveryMan.sendMessage(messageBundle, receiverContact);
        }
        switch (messageBundle.getMessageType()){
            case TEXT:
                handleIncomingTextMessage(messageBundle);
                break;
            case HS:
                handleIncomingHSMessage(messageBundle);
                break;
            case ACK:
                handleIncomingACKMessage(messageBundle);
                break;
            case ROUTING:
                handleIncomingRoutingMessage(messageBundle);
                break;
        }
    }

    private void handleIncomingTextMessage(MessageBundle messageBundle){
        DeviceContact senderContact = messageBundle.getSender();
        // Add to chat
        MainActivity.mConversationManager.addMessage(
                senderContact, senderContact.getDeviceName() + ":  " +
                        messageBundle.getMessage());
        // Send Ack message
        MainActivity.mDeliveryMan.sendMessage(
                MessageBundle.AckBundle(messageBundle), senderContact);
    }

    private void handleIncomingHSMessage(MessageBundle messageBundle){
        if (MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00")) {
            Log.i(TAG, "Initializing myDeviceContact address to " +
                    messageBundle.getReceiver().getDeviceId());
            MainActivity.myDeviceContact = messageBundle.getReceiver();
        }
        if (messageBundle.getUuid()!=null) {
            DeviceContact device = messageBundle.getSender();
            BluetoothService.ConnectThread thread = BluetoothService.mConnectThreads.get(device);
            if (thread == null) {
                Log.e(TAG, "Incoming UUID - requesting thread not found.");
                return;
            }
            thread.mmUuid = messageBundle.getUuid();
        }
    }

    private void handleIncomingRoutingMessage(MessageBundle messageBundle) {
        DeviceContact senderContact = messageBundle.getSender();
        Log.i(TAG, "Merging routing data received from " + senderContact.getShortStr());
        MainActivity.mRoutingTable.mergeRoutingData(messageBundle.getMessage(), senderContact);
    }

    private void handleIncomingACKMessage(MessageBundle messageBundle) {
        // TODO do something with this
    }
}
