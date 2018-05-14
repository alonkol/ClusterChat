package il.ac.tau.cs.mansur.kollmann.clusterchat;
import com.google.gson.Gson;

import java.util.HashMap;

class MessageBundle{
    private String message;
    private MessageTypes messageType;
    private DeviceContact sender;
    private DeviceContact receiver;
    private Integer ttl;
    private Integer messageID;
    private HashMap<String, String> metadata;

    MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                         DeviceContact receiver){
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.messageType = messageType;
        this.ttl = 5;
        this.messageID = MainActivity.getMessageID();
        this.metadata =  new HashMap<>();
    }

    public void addMetadata(String key, String value){
        this.metadata.put(key, value);
    }

    public String getMetadata(String key){
        return this.metadata.get(key);
    }


    public static MessageBundle AckBundle(MessageBundle messageBundle){
        MessageBundle ackBundle = new MessageBundle("", MessageTypes.ACK,
                MainActivity.myDeviceContact, messageBundle.getSender());
        ackBundle.addMetadata("AckID", Integer.toString(messageBundle.messageID));
        return ackBundle;
    }

    String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    static MessageBundle fromJson(String messageJson){
        Gson gson = new Gson();
        MessageBundle mb =  gson.fromJson(messageJson, MessageBundle.class);
        if (mb.metadata == null)
            mb.metadata = new HashMap<>();
        return mb;
    }

    String getMessage() {
        return message;
    }

    DeviceContact getSender() {
        return sender;
    }

    DeviceContact getReceiver() {
        return receiver;
    }

    MessageTypes getMessageType() {
        return messageType;
    }

    Integer getTTL() { return ttl; }

    void setTTL(Integer ttl){ this.ttl = ttl;}

    void decreaseTTL(){ this.ttl -= 1; }

    @Override
    public String toString() {
        return "MessageBundle{" +
                "message='" + message + '\'' +
                ", messageType=" + messageType +
                ", sender=" + sender +
                ", receiver=" + receiver +
                ", ttl=" + ttl +
                ", messageID=" + messageID +
                ", metadata=" + metadata +
                '}';
    }
}