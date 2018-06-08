package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int READ_REQUEST_CODE = 42;
    private Integer mCurrentIndex;

    // Layout Views
    private RecyclerView mMessageRecycler;
    private EditText mOutEditText;
    private Button mSendButton;
    private Button mSendFileButton;
    public MessageListAdapter mMessagesAdapter;
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
        getSupportActionBar().setTitle(mDeviceContact.getDeviceName());

        mMessageRecycler = (RecyclerView) findViewById(R.id.reyclerview_message_list);
        mMessagesAdapter = new MessageListAdapter(this, new ArrayList<BaseMessage>());
        mMessageRecycler.setAdapter(mMessagesAdapter);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mMessageRecycler.setLayoutManager(layoutManager);

        mOutEditText = (EditText) findViewById(R.id.edittext_chatbox);
        mSendButton = (Button) findViewById(R.id.button_chatbox_send);
        mSendFileButton = (Button) findViewById(R.id.button_send_file);
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
        mSendFileButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Filter to show only images, using the image MIME data type.
                // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
                // To search for all documents available via installed storage providers,
                // it would be "*/*".
                intent.setType("*/*");

                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        MainActivity.mConversationManager.initObserver(mDeviceContact, mObserver);
        addMessagesToUI();
    }

    private void addMessagesToUI(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
                MainActivity.mConnectedDevicesArrayAdapter.clearUnread(mDeviceContact);
            }
        });
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
            MainActivity.mDeliveryMan.sendMessage(newMessage, mDeviceContact);
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

    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                String fileName = getFileName(uri);
                MainActivity.mConversationManager.addMessage(mDeviceContact,
                        new BaseMessage("File " + fileName + " is on its way to target...\n" +
                                "You will be notified when the file has been saved into " + mDeviceContact.getDeviceName() + " device"));
                new FileSender(MainActivity.mDeliveryMan, uri, fileName, mDeviceContact, getContentResolver()).start();
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }



}
