package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConversationsManager {
    private final String TAG = "ConversationsManager";
    private HashMap<String, ArrayList<String>> mConversations;

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
        }
        mConversations.get(deviceName).add(message);
        Log.i(TAG, "Added message from device " + deviceName + "\nwith content: " + message);
    }
}
