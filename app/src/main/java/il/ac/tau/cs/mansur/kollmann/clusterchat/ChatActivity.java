package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private BluetoothService.ConnectedThread mThread;
    private Integer mCurrentIndex;

    // Layout Views
    private RecyclerView mMessageRecycler;
    private EditText mOutEditText;
    private Button mSendButton;
    public static MessageListAdapter mMessagesAdapter;
    private StringBuffer mOutStringBuffer;
    private Observer mObserver;
    private DeviceContact mDeviceContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_window);
        Intent intent = getIntent();
        String deviceAddress = intent.getStringExtra("clusterchat.deviceAddress");
        mDeviceContact = MainActivity.findDeviceContact(deviceAddress);
        if (mDeviceContact==null){
            Log.e(TAG, "Device was not found");
            finish();
        }
        Log.d(TAG, String.format("Init chatService for device %s", mDeviceContact.getShortStr()));
        DeviceContact link = MainActivity.mRoutingTable.getLink(mDeviceContact);
        mThread = MainActivity.mBluetoothService.getConnectedThread(link);
        if (mThread == null){
            Log.e(TAG, "Connection thread not found");
            finish();
        }
        getSupportActionBar().setTitle(mDeviceContact.getDeviceName());

        mMessageRecycler = (RecyclerView) findViewById(R.id.reyclerview_message_list);
        mMessagesAdapter = new MessageListAdapter(this, new ArrayList<BaseMessage>());
        mMessageRecycler.setAdapter(mMessagesAdapter);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mMessageRecycler.setLayoutManager(layoutManager);

        // TODO: remove
        // mMessageRecycler.setHasFixedSize(true);

        mOutEditText = (EditText) findViewById(R.id.edittext_chatbox);
        mSendButton = (Button) findViewById(R.id.button_chatbox_send);
        mCurrentIndex = 0;
        mObserver = new Observer() {
            @Override
            public void update(Observable observable, Object o) {
                Log.d(TAG, "Invoked update method of observer");
                addMessagesToUI();
            }
        };
        setupChat();
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String message = mOutEditText.getText().toString();
                sendMessage(message);
            }
        });
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        MainActivity.mConversationManager.initObserver(mDeviceContact, mObserver);
        addMessagesToUI();
    }

    private void addMessagesToUI(){
        Log.i(TAG, "Adding messages to UI starting with index " + mCurrentIndex);
        // Get messages from ConversationManager
        List<BaseMessage> conversations = MainActivity.mConversationManager.getMessagesForConversation(
                mDeviceContact, mCurrentIndex);
        for(BaseMessage message: conversations){
            mMessagesAdapter.add(message);
        }

        mMessagesAdapter.notifyItemRangeInserted(mCurrentIndex, conversations.size());
        mMessageRecycler.scrollToPosition(mMessagesAdapter.getItemCount() - 1);
        mCurrentIndex += conversations.size();
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that there's actually something to send
        if (message.length() > 0) {
            MessageBundle newMessage = new MessageBundle(
                    message, MessageTypes.TEXT, MainActivity.myDeviceContact, mDeviceContact);
            MainActivity.mDeliveryMan.sendMessage(newMessage, mDeviceContact, mThread);
            MainActivity.mConversationManager.addMessage(mDeviceContact, new BaseMessage(message));
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
            addMessagesToUI();
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.mConversationManager.discardObserver(mDeviceContact);
    }

}
