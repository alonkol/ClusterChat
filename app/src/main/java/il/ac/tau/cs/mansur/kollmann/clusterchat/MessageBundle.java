package il.ac.tau.cs.mansur.kollmann.clusterchat;
import com.google.gson.Gson;

public class MessageBundle{
    private static final String TAG="Message";
    private String message;
    private MessageTypes messageType;
    private DeviceContact sender;
    private DeviceContact receiver;

    public MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                         DeviceContact receiver){
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.messageType = messageType;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static MessageBundle fromJson(String messageJson){
        Gson gson = new Gson();
        return gson.fromJson(messageJson, MessageBundle.class);
    }


    public String getMessage() {
        return message;
    }

    public DeviceContact getSender() {
        return sender;
    }

    public DeviceContact getReceiver() {
        return receiver;
    }

    public MessageTypes getMessageType() {
        return messageType;
    }
}