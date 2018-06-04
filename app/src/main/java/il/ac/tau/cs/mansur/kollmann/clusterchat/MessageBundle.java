package il.ac.tau.cs.mansur.kollmann.clusterchat;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Objects;

public class MessageBundle{
    private String message;
    private MessageTypes messageType;
    private DeviceContact sender;
    private DeviceContact receiver;
    private Integer ttl;
    private Integer messageID;
    private HashMap<String, String> metadata;

    public MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                  DeviceContact receiver, int messageID){
        this.message = message;
        this.sender = sender;
        this.receiver = receiver;
        this.messageType = messageType;
        this.ttl = 5;
        this.messageID = messageID;
        this.metadata =  new HashMap<>();
    }

    public MessageBundle(String message, MessageTypes messageType, DeviceContact sender,
                         DeviceContact receiver){
        this(message, messageType, sender, receiver, 0);
        this.messageID = MainActivity.getNewMessageID();
    }


    public void addMetadata(String key, String value){
        this.metadata.put(key, value);
    }

    public String getMetadata(String key){
        return this.metadata.get(key);
    }

    public Integer getMessageID(){
        return this.messageID;
    }


    public static MessageBundle routingMessageBundle(String routingData, MessageTypes messageType,
                                                     DeviceContact sender, DeviceContact receiver){
        MessageBundle routingBundle = new MessageBundle("", messageType, sender, receiver);
        routingBundle.addMetadata("Routing", routingData);
        return routingBundle;
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

    static MessageBundle fromJson(String messageJson) throws JsonSyntaxException{
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

    public void setTTL(Integer ttl){ this.ttl = ttl;}

    void decreaseTTL(){ this.ttl -= 1; }

    @Override
    public String toString() {
        return "MessageBundle{" +
                "message='" + (messageType == MessageTypes.TEXT ? message  :
                "")  +  '\'' +
                ", messageType=" + messageType +
                ", sender=" + sender.getShortStr() +
                ", receiver=" + receiver.getShortStr() +
                ", ttl=" + ttl +
                ", messageID=" + messageID +
                ", metadata=" + metadata +
                '}';
    }

    public PackageIdentifier getIdentifier(){
        return new PackageIdentifier(messageID, sender);
    }

    public static class PackageIdentifier{
        private Integer messageID;
        private DeviceContact deviceContact;

        PackageIdentifier(Integer messageID, DeviceContact deviceContact) {
            this.messageID = messageID;
            this.deviceContact = deviceContact;
        }

        @Override
        public String toString() {
            return "PackageIdentifier{" +
                    "messageID=" + messageID +
                    ", deviceContact=" + deviceContact +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PackageIdentifier that = (PackageIdentifier) o;
            return Objects.equals(messageID, that.messageID) &&
                    Objects.equals(deviceContact, that.deviceContact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageID, deviceContact);
        }
    }

}