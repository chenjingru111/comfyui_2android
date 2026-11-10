package com.local.codexmobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.local.codexmobile.R;
import com.local.codexmobile.model.Entities.ThreadSummary;

import java.util.ArrayList;
import java.util.List;

public final class ThreadAdapter extends BaseAdapter {
    private final LayoutInflater inflater; private final List<ThreadSummary> items = new ArrayList<>();
    public ThreadAdapter(Context context) { inflater = LayoutInflater.from(context); }
    public void replace(List<ThreadSummary> value) { items.clear(); items.addAll(value); notifyDataSetChanged(); }
    @Override public int getCount() { return items.size(); }
    @Override public ThreadSummary getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.row_thread, parent, false) : convertView;
        ThreadSummary item = getItem(position);
        ((TextView) row.findViewById(R.id.threadName)).setText(item.name.isEmpty() ? "未命名对话" : item.name);
        ((TextView) row.findViewById(R.id.threadPreview)).setText(item.preview);
        ((TextView) row.findViewById(R.id.threadMeta)).setText(item.cwd + (item.status.isEmpty() ? "" : " · " + item.status));
        return row;
    }
}
