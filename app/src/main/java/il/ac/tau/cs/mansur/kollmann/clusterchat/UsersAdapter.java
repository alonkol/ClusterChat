package il.ac.tau.cs.mansur.kollmann.clusterchat;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.ArrayList;

public class UsersAdapter extends ArrayAdapter<DeviceContact> {
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
        TextView contact_name = (TextView) convertView.findViewById(R.id.contact_name);
        TextView contact_id = (TextView) convertView.findViewById(R.id.contact_id);
        TextView new_messages = (TextView) convertView.findViewById(R.id.new_messages);

        // Populate the data into the template view using the data object
        contact_name.setText(user.getDeviceName());
        contact_id.setText(user.getDeviceId());

        new_messages.setText(String.valueOf(user.getUnreadMessages()));
        if (user.getUnreadMessages() > 0) {
            new_messages.setVisibility(View.VISIBLE);
        }

        convertView.setTag(user);

        // Return the completed view to render on screen
        return convertView;
    }

    void newMessage(DeviceContact sender) {
        DeviceContact stored_contact = getItem(getPosition(sender));
        stored_contact.IncrementAndGetUnread();
        remove(stored_contact);
        add(stored_contact);
        notifyDataSetChanged();
    }

    void clearUnread(DeviceContact contact) {
        contact.clearUnread();
        remove(contact);
        add(contact);
        notifyDataSetChanged();
    }

}