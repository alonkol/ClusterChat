package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.ArrayList;

 class UsersAdapter extends ArrayAdapter<DeviceContact> {
    UsersAdapter(Context context) {
        super(context, 0, new ArrayList<DeviceContact>());
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        DeviceContact user = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_user, parent, false);
        }
        // Lookup view for data population
        TextView contact_name = convertView.findViewById(R.id.contact_name);
        TextView contact_id = convertView.findViewById(R.id.contact_id);
        TextView new_messages = convertView.findViewById(R.id.new_messages);

        // Populate the data into the template view using the data object
        if (user==null){
            return convertView;
        }
        contact_name.setText(user.getDeviceName());
        contact_id.setText(user.getDeviceId());

        new_messages.setText(String.valueOf(user.getUnreadMessages()));
        if (user.getUnreadMessages() > 0) {
            new_messages.setVisibility(View.VISIBLE);
        } else {
            new_messages.setVisibility(View.INVISIBLE);
        }

        convertView.setTag(user);
        // Return the completed view to render on screen
        return convertView;
    }

    void newMessage(DeviceContact sender) {
        int pos = getPosition(sender);
        if (pos == -1)
            return;
        DeviceContact stored_contact = getItem(getPosition(sender));
        if (stored_contact==null)
            return;
        stored_contact.IncrementAndGetUnread();
        remove(stored_contact);
        insert(stored_contact, 0);
        notifyDataSetChanged();
    }

    void clearUnread(DeviceContact contact) {
        contact.clearUnread();
        int position = getPosition(contact);
        if (position == -1)
            return;
        remove(contact);
        insert(contact, position);
        notifyDataSetChanged();
    }

}