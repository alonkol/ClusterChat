package il.ac.tau.cs.mansur.kollmann.clusterchat;
import com.google.gson.Gson;

import java.util.UUID;

class MessageBundle{
    private String message;
    private MessageTypes messageType;
    private DeviceContact sender;
    private DeviceContact receiver;
    private UUID uuid;

    MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                         DeviceContact receiver, UUID uuid){
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.messageType = messageType;
        this.uuid = uuid;
    }

    MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                  DeviceContact receiver){
        this(message, messageType, sender, receiver, null);
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

    @Override
    public String toString() {
        return "MessageBundle{" +
                "message='" + message + '\'' +
                ", messageType=" + messageType +
                '}';
    }

    UUID getUuid() {
        return uuid;
    }
}