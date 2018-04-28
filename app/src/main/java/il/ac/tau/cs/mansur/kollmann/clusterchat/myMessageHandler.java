package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

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
        switch (messageBundle.getMessageType()){
            case TEXT:
                handleOutgoingTextMessage(messageBundle);
                break;
            case HS:
                handleOutgoingHSMessage();
                break;
            case UUID:
                handleOutgoingUuid();
                break;
        }
    }

    private void handleOutgoingTextMessage(MessageBundle messageBundle){
        DeviceContact deviceContact = messageBundle.getReceiver();
        Log.i(TAG, "Handler caught outgoing message for device " +
                deviceContact.getDeviceId() + "\nwith content: " +
                messageBundle.getMessage());
        MainActivity.mConversationManager.addMessage(
                deviceContact, "Me:  " +
                        messageBundle.getMessage());
    }

    private void handleOutgoingHSMessage(){
        Log.i(TAG, "Handler caught outgoing HS message");
    }

    private void handleOutgoingUuid(){
        Log.i(TAG, "Handler caught outgoing UUID message");
    }

    private void handleIncomingMessage(Message msg){
        byte[] buffer = (byte[]) msg.obj;
        String readMessage = new String(buffer, 0, msg.arg1);
        MessageBundle messageBundle = MessageBundle.fromJson(readMessage);
        switch (messageBundle.getMessageType()){
            case TEXT:
                handleIncomingTextMessage(messageBundle);
                break;
            case HS:
                handleIncomingHSMessage(messageBundle);
                break;
            case UUID:
                handleIncomingUuid(messageBundle);
                break;
        }
    }

    private void handleIncomingTextMessage(MessageBundle messageBundle){
        DeviceContact deviceContact = messageBundle.getSender();
        Log.i(TAG, "Handler caught incoming message from device " + deviceContact +
                "\nwith content: " + messageBundle.getMessage());
        MainActivity.mConversationManager.addMessage(
                deviceContact, deviceContact.getDeviceName() + ":  " +
                        messageBundle.getMessage());
    }

    private void handleIncomingHSMessage(MessageBundle messageBundle){
        Log.i(TAG, "Handler caught incoming HS message");
        if (MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00")) {
            Log.i(TAG, "Initializing myDeviceContact address to " +
                    messageBundle.getReceiver().getDeviceId());
            MainActivity.myDeviceContact = messageBundle.getReceiver();
        }
    }

    private void handleIncomingUuid(MessageBundle messageBundle){
        Log.i(TAG, "Handler caught incoming UUID message");
        DeviceContact device = messageBundle.getSender();
        BluetoothService.ConnectThread thread = BluetoothService.mConnectThreads.get(device);
        if (thread == null) {
            Log.e(TAG, "Incoming UUID - requesting thread not found.");
            return;
        }
        thread.setUuid(messageBundle.getUuid());
    }
}
