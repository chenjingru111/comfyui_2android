package com.local.codexmobile.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.local.codexmobile.R;
import com.local.codexmobile.model.Entities.Message;

import java.util.ArrayList;
import java.util.List;

public final class MessageAdapter extends BaseAdapter {
    private final LayoutInflater inflater; private final List<Message> items = new ArrayList<>();
    public MessageAdapter(Context context) { inflater = LayoutInflater.from(context); }
    public void replace(List<Message> value) { items.clear(); items.addAll(value); notifyDataSetChanged(); }
    @Override public int getCount() { return items.size(); }
    @Override public Message getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = inflater.inflate(R.layout.row_message, parent, false); Message item = getItem(position);
        LinearLayout root = (LinearLayout) row; TextView role = row.findViewById(R.id.messageRole); TextView text = row.findViewById(R.id.messageText);
        boolean user = "user".equals(item.role); root.setGravity(user ? Gravity.END : Gravity.START);
        role.setText(user ? "你" : "tool".equals(item.role) ? "工具" : "Codex"); text.setText(item.text);
        text.setBackgroundResource(user ? R.drawable.bg_user : R.drawable.bg_agent); return row;
    }
}
