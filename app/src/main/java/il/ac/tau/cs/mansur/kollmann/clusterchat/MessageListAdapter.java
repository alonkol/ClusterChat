package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

class BaseMessage {
    final String mMessage;
    private final String mSender;
    final long createdAt;
    final boolean incoming;

    BaseMessage(String message, String sender) {
        mMessage = message;
        mSender = sender;
        incoming = true;
        createdAt = Calendar.getInstance().getTime().getTime();
    }

    BaseMessage(String message) {
        mMessage = message;
        mSender = "Me";
        incoming = false;
        createdAt = Calendar.getInstance().getTime().getTime();
    }
}

class MessageListAdapter extends RecyclerView.Adapter {
    private static final int VIEW_TYPE_MESSAGE_SENT = 1;
    private static final int VIEW_TYPE_MESSAGE_RECEIVED = 2;

    private final Context mContext;
    private final List<BaseMessage> mMessageList;

    MessageListAdapter(Context context, List<BaseMessage> messageList) {
        mContext = context;
        mMessageList = messageList;
        notifyDataSetChanged();
    }

    public void add(BaseMessage message) {
        mMessageList.add(message);
        notifyItemInserted(mMessageList.size() - 1);
    }

    @Override
    public int getItemCount() {
        return mMessageList.size();
    }

    // Determines the appropriate ViewType according to the sender of the message.
    @Override
    public int getItemViewType(int position) {
        BaseMessage message = mMessageList.get(position);

        if (message.incoming) {
            return VIEW_TYPE_MESSAGE_RECEIVED;
        } else {
            return VIEW_TYPE_MESSAGE_SENT;
        }
    }

    // Inflates the appropriate layout according to the ViewType.
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;

        if (viewType == VIEW_TYPE_MESSAGE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageHolder(view);
        } else if (viewType == VIEW_TYPE_MESSAGE_RECEIVED) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        }

        return null;
    }

    // Passes the message object to a ViewHolder so that the contents can be bound to UI.
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        BaseMessage message = mMessageList.get(position);

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_MESSAGE_SENT:
                ((SentMessageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_MESSAGE_RECEIVED:
                ((ReceivedMessageHolder) holder).bind(message);
        }
    }

    private class SentMessageHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;

        SentMessageHolder(View itemView) {
            super(itemView);

            messageText = itemView.findViewById(R.id.text_message_body);
            timeText = itemView.findViewById(R.id.text_message_time);
        }

        void bind(BaseMessage message) {
            messageText.setText(message.mMessage);

            // Format the stored timestamp into a readable String using method.
            timeText.setText(DateUtils.formatDateTime(mContext, message.createdAt,
                    DateUtils.FORMAT_SHOW_TIME));
        }
    }

    private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;

        ReceivedMessageHolder(View itemView) {
            super(itemView);

            messageText = itemView.findViewById(R.id.rec_text_message_body);
            timeText = itemView.findViewById(R.id.rec_text_message_time);
        }

        void bind(BaseMessage message) {
            messageText.setText(message.mMessage);

            // Format the stored timestamp into a readable String using method.
            timeText.setText(DateUtils.formatDateTime(mContext, message.createdAt,
                    DateUtils.FORMAT_SHOW_TIME));

        }
    }
}