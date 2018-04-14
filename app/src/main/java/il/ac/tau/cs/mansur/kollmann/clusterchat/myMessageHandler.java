package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class myMessageHandler extends Handler {
    private final String TAG = "MessageHandler";
    public static final int MESSAGE_OUT_HANDSHAKE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;

    @Override
    public void handleMessage(Message msg) {
        byte[] buffer = (byte[]) msg.obj;
        switch (msg.what) {
            case MESSAGE_WRITE:
                // construct a string from the buffer
                handleOutgoingTextMessage(buffer);
                break;
            case MESSAGE_READ:
                handleIncomingMessage(msg);
                break;
            case MESSAGE_OUT_HANDSHAKE:
                handleOutgoingHSMessage();
                break;
        }
    }

    private void handleOutgoingTextMessage(byte[] buffer){
        String writeMessage = new String(buffer);
        MessageBundle messageBundle = MessageBundle.fromJson(writeMessage);
        DeviceContact deviceContact = messageBundle.getReceiver();
        Log.i(TAG, "Handler caught outgoing message for device " +
                deviceContact.getDeviceId() + "\nwith content: " +
                messageBundle.getMessage());
        MainActivity.mConversationManager.addMessage(
                deviceContact, "Me:  " +
                        messageBundle.getMessage());
    }

    private void handleOutgoingHSMessage(){
        Log.i(TAG, "Handler caught outgoing HI message");
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
        Log.i(TAG, "Handler caught incoming HI message");
        if (MainActivity.myDeviceContact.getDeviceId().equals("00-00-00-00-00-00")) {
            Log.i(TAG, "Initializing myDeviceContact address to " +
                    messageBundle.getReceiver().getDeviceId());
            MainActivity.myDeviceContact = messageBundle.getReceiver();
        }
    }

}
