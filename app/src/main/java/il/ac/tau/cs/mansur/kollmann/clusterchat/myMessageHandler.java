package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class myMessageHandler extends Handler {
    private final String TAG = "MessageHandler";
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    @Override
    public void handleMessage(Message msg) {
        byte[] buffer = (byte[]) msg.obj;
        String deviceId;
        DeviceContact deviceContact;
        switch (msg.what) {
            case MESSAGE_WRITE:
                // construct a string from the buffer
                String writeMessage = new String(buffer);
                deviceId = writeMessage.substring(0, 17);
                writeMessage = writeMessage.substring(18,writeMessage.length());
                deviceContact = MainActivity.findDeviceContact(deviceId);
                Log.i(TAG, "Handler caught outgoing message for device " + deviceContact.getDeviceId() +
                        "\nwith content: " + writeMessage);
                MainActivity.mConversationManager.addMessage(
                        deviceContact, "Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(buffer, 0, msg.arg1);
                deviceId = readMessage.substring(0, 17);
                readMessage = readMessage.substring(18,readMessage.length());
                deviceContact = MainActivity.findDeviceContact(deviceId);
                Log.i(TAG, "Handler caught incoming message from device " + deviceContact +
                        "\nwith content: " + readMessage);
                MainActivity.mConversationManager.addMessage(
                        deviceContact, deviceContact.getDeviceName() + ":  " + readMessage);
                break;
        }
    }

}
