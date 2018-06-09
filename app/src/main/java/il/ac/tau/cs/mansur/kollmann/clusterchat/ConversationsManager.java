package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

class ConversationsManager {
    private final String TAG = "ConversationsManager";
    private final HashMap<DeviceContact, ArrayList<BaseMessage>> mConversations;
    private final HashMap<DeviceContact, AddedMessageFlag> mAddedMessagesFlags;

    ConversationsManager(){
        mConversations = new HashMap<>();
        mAddedMessagesFlags = new HashMap<>();
    }

    List<BaseMessage> getMessagesForConversation(DeviceContact deviceContact, int index){
        List<BaseMessage> allMessages = safeGetMessages(deviceContact);
        return allMessages.subList(index, allMessages.size());
    }

    private void initDeviceObjects(DeviceContact deviceContact) {
        if (! mConversations.containsKey(deviceContact)){
            mConversations.put(deviceContact, new ArrayList<BaseMessage>());
            if (mAddedMessagesFlags.get(deviceContact) == null) {
                mAddedMessagesFlags.put(deviceContact, new AddedMessageFlag());
            }
        }
    }

    private List<BaseMessage> safeGetMessages(DeviceContact deviceContact){
        initDeviceObjects(deviceContact);
        return mConversations.get(deviceContact);
    }

    private void addMessages(DeviceContact deviceContact, List<BaseMessage> messages) {
        for (BaseMessage message: messages) {
            addMessage(deviceContact, message);
        }
    }

    void addMessage(DeviceContact deviceContact, BaseMessage message) {
        initDeviceObjects(deviceContact);
        mConversations.get(deviceContact).add(message);
        mAddedMessagesFlags.get(deviceContact).addedMessage();
        Log.i(TAG, "Added message with device " + deviceContact.getDeviceName() +
                " with content: " + message.mMessage);
    }

    void addMessagesFromHistory(File contact){
        String contactID = contact.getName();
        try {
            Gson gson = new Gson();
            BufferedReader reader = new BufferedReader(new FileReader(contact));
            Type type = new TypeToken<List<BaseMessage>>(){}.getType();
            List<BaseMessage> data = gson.fromJson(reader, type);
            addMessages(new DeviceContact(contactID), data);
        }
        catch(Exception e) {
            Log.e(TAG,"Failed to read history from file.", e);
        }
    }

    void writeMessagesToFiles(File convDirectory){
        for (DeviceContact dc: mConversations.keySet()){
            List<BaseMessage> conversation = mConversations.get(dc);
            String deviceAddress = dc.getDeviceId();

            try {
                File file = new File(convDirectory, deviceAddress);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
                Gson gson = new Gson();
                String json = gson.toJson(conversation);
                writer.append(json);
                writer.close();
                fileOutputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save conversation history to file.", e);
            }
        }
    }

    void initObserver(DeviceContact deviceContact, Observer o){
        initDeviceObjects(deviceContact);
        mAddedMessagesFlags.get(deviceContact).addObserver(o);
        Log.d(TAG, "Observer was init for device " + deviceContact.getDeviceName());
    }

    void discardObserver(DeviceContact deviceContact){
        mAddedMessagesFlags.get(deviceContact).deleteObservers();
        Log.d(TAG, "Observer was removed for device " + deviceContact.getDeviceName());
    }

    class AddedMessageFlag extends Observable{
        void addedMessage(){
            setChanged();
            notifyObservers();
        }
    }
}
