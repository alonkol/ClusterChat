package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

class ConversationsManager {
    private final String TAG = "ConversationsManager";
    private HashMap<DeviceContact, ArrayList<String>> mConversations;
    private HashMap<DeviceContact, AddedMessageFlag> mAddedMessagesFlags;

    ConversationsManager(){
        mConversations = new HashMap<>();
        mAddedMessagesFlags = new HashMap<>();
    }

    List<String> getMessagesForConversation(DeviceContact deviceContact, int index){
        List<String> allMessages =  safeGetMessages(deviceContact);
        return allMessages.subList(index, allMessages.size());
    }

    private void initDeviceObjects(DeviceContact deviceContact) {
        if (! mConversations.containsKey(deviceContact)){
            mConversations.put(deviceContact, new ArrayList<String>());
            if (mAddedMessagesFlags.get(deviceContact) == null) {
                mAddedMessagesFlags.put(deviceContact, new AddedMessageFlag());
            }
        }
    }

    private List<String> safeGetMessages(DeviceContact deviceContact){
        initDeviceObjects(deviceContact);
        return mConversations.get(deviceContact);
    }

    void addMessage(DeviceContact deviceContact, String message) {
        initDeviceObjects(deviceContact);
        mConversations.get(deviceContact).add(message);
        mAddedMessagesFlags.get(deviceContact).addedMessage();
        Log.i(TAG, "Added message with device " + deviceContact.getDeviceName() +
                "\nwith content: " + message);
    }

    void addMessagesFromHistory(File contact){
        String contactID = contact.getName();
        String line;

        try {
            FileReader fileReader = new FileReader(contact);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                addMessage(new DeviceContact(contactID), line);
            }

            bufferedReader.close();
            fileReader.close();
        }
        catch(Exception e) {
            Log.e(TAG,"Failed to read history from file.", e);
        }
    }

    void writeMessagesToFiles(File convDirectory){
        for (DeviceContact dc: mConversations.keySet()){
            List<String> conversation = mConversations.get(dc);
            String deviceAddress = dc.getDeviceId();

            try {
                File file = new File(convDirectory, deviceAddress);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
                String conversation_string = TextUtils.join("\n", conversation);
                writer.append(conversation_string);
                writer.close();
                fileOutputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save convesration history to file.", e);
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
