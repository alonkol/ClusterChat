package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

public class ConversationsManager {
    private final String TAG = "ConversationsManager";
    private HashMap<DeviceContact, ArrayList<String>> mConversations;
    private HashMap<DeviceContact, AddedMessageFlag> mAddedMessagesFlags;

    public ConversationsManager(){
        mConversations = new HashMap<>();
        mAddedMessagesFlags = new HashMap<>();
    }

    public List<String> getMessagesForConversation(DeviceContact deviceContact, int index){
        List<String> allMessages =  safeGetMessages(deviceContact);
        return allMessages.subList(index, allMessages.size());
    }

    public List<String> getAllMessagesForConversation(DeviceContact deviceContact){
        return safeGetMessages(deviceContact);
    }

    private void initDeviceObjects(DeviceContact deviceContact) {
        if (! mConversations.containsKey(deviceContact)){
            mConversations.put(deviceContact, new ArrayList<String>());
            if (mAddedMessagesFlags.get(deviceContact) == null) {
                mAddedMessagesFlags.put(deviceContact, new AddedMessageFlag());
            }
        }
    }

    public List<String> safeGetMessages(DeviceContact deviceContact){
        initDeviceObjects(deviceContact);
        return mConversations.get(deviceContact);
    }

    public void addMessage(DeviceContact deviceContact, String message) {
        initDeviceObjects(deviceContact);
        mConversations.get(deviceContact).add(message);
        mAddedMessagesFlags.get(deviceContact).addedMessage();
        Log.i(TAG, "Added message from device " + deviceContact.getDeviceName() + "\nwith content: " + message);
    }

    public void addMessagesFromHistory(File contact){
        String contactID = contact.getName();
        String line;

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader =
                    new FileReader(contactID);

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                addMessage(new DeviceContact(contactID), line);
            }

            // Always close files.
            bufferedReader.close();
        }
        catch(FileNotFoundException ex) {
            Log.e(TAG,
                    "Unable to open file '" +
                            contactID + "'");
        }
        catch(IOException ex) {
            Log.e(TAG,
                    "Error reading file '"
                            + contactID + "'");
        }
    }

    public void writeMessagesToFiles(File convDirectory){
            for (DeviceContact dc: mConversations.keySet()){
                String deviceAddress = dc.getDeviceId();

            }
    }

    public void initObserver(DeviceContact deviceContact, Observer o){
        initDeviceObjects(deviceContact);
        mAddedMessagesFlags.get(deviceContact).addObserver(o);
        Log.d(TAG, "Observer was init for device " + deviceContact.getDeviceName());
    }

    public void discardObserver(DeviceContact deviceContact){
        mAddedMessagesFlags.get(deviceContact).deleteObservers();
        Log.d(TAG, "Observer was removed for device " + deviceContact.getDeviceName());
    }

    class AddedMessageFlag extends Observable{
        public void addedMessage(){
            setChanged();
            notifyObservers();
        }
    }
}
