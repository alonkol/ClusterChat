package il.ac.tau.cs.mansur.kollmann.clusterchat;
import com.google.gson.Gson;

import java.util.UUID;

class MessageBundle{
    private String message;
    private MessageTypes messageType;
    private DeviceContact sender;
    private DeviceContact receiver;
    private UUID uuid;
    private Integer ttl;
    private Integer messageID;

    MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                         DeviceContact receiver, UUID uuid){
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.messageType = messageType;
        this.uuid = uuid;
        this.ttl = 5;
        this.messageID = MainActivity.getMessageID();
    }

    MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                  DeviceContact receiver){
        this(message, messageType, sender, receiver, null);
    }


    public static MessageBundle AckBundle(MessageBundle messageBundle){
        return new MessageBundle(Integer.toString(messageBundle.messageID), MessageTypes.ACK,
                MainActivity.myDeviceContact, messageBundle.getSender());
    }

    String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    static MessageBundle fromJson(String messageJson){
        Gson gson = new Gson();
        return gson.fromJson(messageJson, MessageBundle.class);
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
                ", sender=" + sender.getShortStr() +
                ", receiver=" + receiver.getShortStr() +
                ", ttl=" + ttl +
                ", uuid=" + (uuid==null ? "null" : uuid.toString()) +
                ", messageID =" + Integer.toString(messageID) +
                '}';
    }

    UUID getUuid() {
        return uuid;
    }

}