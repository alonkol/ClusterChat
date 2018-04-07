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
    private HashMap<String, Observable> mChangeFlags;

    public ConversationsManager(){
        mConversations = new HashMap<>();
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
            return new ArrayList<>();
        }
        return messages;
    }

    public void addMessage(String deviceName, String message) {
        List<String> deviceMessages = safeGetMessages(deviceName);
        if (deviceMessages.size() == 0){
            mConversations.put(deviceName, new ArrayList<String>());
            if (mChangeFlags.get(deviceName) == null) {
                mChangeFlags.put(deviceName, new Observable());
            }

        }
        mConversations.get(deviceName).add(message);
        mChangeFlags.get(deviceName).notifyObservers();
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
        if (mChangeFlags.get(deviceName) == null){
            mChangeFlags.put(deviceName, new Observable());
        }
        mChangeFlags.get(deviceName).addObserver(o);
    }

    public void discardObserver(String deviceName){
        mChangeFlags.get(deviceName).deleteObservers();
    }
}
