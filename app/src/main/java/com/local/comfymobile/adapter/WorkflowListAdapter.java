package com.local.comfymobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.local.comfymobile.R;
import com.local.comfymobile.model.Entities.WorkflowSummary;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class WorkflowListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<WorkflowSummary> items = new ArrayList<>();

    public WorkflowListAdapter(Context context) { inflater = LayoutInflater.from(context); }
    public void replace(List<WorkflowSummary> workflows) { items.clear(); items.addAll(workflows); notifyDataSetChanged(); }
    @Override public int getCount() { return items.size(); }
    @Override public WorkflowSummary getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? inflater.inflate(R.layout.row_workflow, parent, false) : convertView;
        WorkflowSummary item = getItem(position);
        ((TextView) view.findViewById(R.id.workflowName)).setText(item.name);
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(item.modifiedAt * 1000));
        ((TextView) view.findViewById(R.id.workflowMeta)).setText(item.nodeCount + " 个节点 · " + date + "\n" + item.relativePath);
        return view;
    }
}
