package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ConversationsManager {
    private final String TAG = "ConversationsManager";
    private HashMap<String, ArrayList<String>> mConversations;
    private HashMap<String, AddedMessageFlag> mAddedMessagesFlags;

    public ConversationsManager(){
        mConversations = new HashMap<>();
        mAddedMessagesFlags = new HashMap<>();
    }

    public List<String> getMessagesForConversation(String deviceName, int index){
        List<String> allMessages =  safeGetMessages(deviceName);
        return allMessages.subList(index, allMessages.size());
    }

    public List<String> getAllMessagesForConversation(String deviceName){
        return safeGetMessages(deviceName);
    }

    public List<String> safeGetMessages(String deviceName){
        List<String> messages = mConversations.get(deviceName);
        if (messages == null){
            mConversations.put(deviceName, new ArrayList<String>());
            if (mAddedMessagesFlags.get(deviceName) == null) {
                mAddedMessagesFlags.put(deviceName, new AddedMessageFlag());
            }
        }
        return mConversations.get(deviceName);
    }

    public void addMessage(String deviceName, String message) {
        // This will init the requiered objects if needed
        safeGetMessages(deviceName);
        mConversations.get(deviceName).add(message);
        mAddedMessagesFlags.get(deviceName).addedMessage();
        Log.i(TAG, "Added message from device " + deviceName + "\nwith content: " + message);
    }

    public void addMessagesFromHistory(File contact){
        String contactName = contact.getName();
        String line;

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader =
                    new FileReader(contactName);

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                addMessage(contactName, line);
            }

            // Always close files.
            bufferedReader.close();
        }
        catch(FileNotFoundException ex) {
            Log.e(TAG,
                    "Unable to open file '" +
                            contactName + "'");
        }
        catch(IOException ex) {
            Log.e(TAG,
                    "Error reading file '"
                            + contactName + "'");
        }
    }

    public void initObserver(String deviceName, Observer o){
        // This will init the requiered objects if needed
        safeGetMessages(deviceName);
        mAddedMessagesFlags.get(deviceName).addObserver(o);
        Log.d(TAG, "Observer was init for device " + deviceName);
    }

    public void discardObserver(String deviceName){
        mAddedMessagesFlags.get(deviceName).deleteObservers();
        Log.d(TAG, "Observer was removed for device " + deviceName);
    }

    class AddedMessageFlag extends Observable{
        public void addedMessage(){
            setChanged();
            notifyObservers();
        }
    }
}
