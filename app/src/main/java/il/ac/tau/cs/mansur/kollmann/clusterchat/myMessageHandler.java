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
        String[] splitMessage;
        //Message format is SENDER-RECIEVER-MESSAGE
        switch (msg.what) {
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                splitMessage = writeMessage.split("~", 3);
                Log.i(TAG, "Handler caught outgoing message for device " + splitMessage[1] +
                        "\nwith content: " + splitMessage[2]);
                MainActivity.mConversationManager.addMessage(splitMessage[1], "Me:  " + splitMessage[2]);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                splitMessage = readMessage.split("~", 3);
                Log.i(TAG, "Handler caught incoming message from device " + splitMessage[0] +
                        "\nwith content: " + splitMessage[2]);
                MainActivity.mConversationManager.addMessage(splitMessage[0], splitMessage[0]+ ":  " + splitMessage[2]);
                break;
        }
    }
}
