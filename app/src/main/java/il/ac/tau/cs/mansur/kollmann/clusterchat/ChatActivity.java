package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_window);
    }
}
