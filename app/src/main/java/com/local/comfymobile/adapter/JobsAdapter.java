package com.local.comfymobile.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.local.comfymobile.R;
import com.local.comfymobile.model.Entities.JobInfo;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class JobsAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<JobInfo> items = new ArrayList<>();

    public JobsAdapter(Context context) { inflater = LayoutInflater.from(context); }
    public void replace(List<JobInfo> jobs) { items.clear(); items.addAll(jobs); notifyDataSetChanged(); }
    @Override public int getCount() { return items.size(); }
    @Override public JobInfo getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? inflater.inflate(R.layout.row_job, parent, false) : convertView;
        JobInfo job = getItem(position);
        ((TextView) view.findViewById(R.id.jobName)).setText(job.workflowName);
        TextView status = view.findViewById(R.id.jobStatus);
        status.setText(job.status + (job.error.isEmpty() ? "" : " · " + job.error));
        status.setTextColor("COMPLETED".equals(job.status) ? Color.rgb(21, 128, 61) : "FAILED".equals(job.status) ? Color.rgb(185, 28, 28) : Color.rgb(37, 99, 235));
        String date = DateFormat.getDateTimeInstance().format(new Date(job.createdAt * 1000));
        ((TextView) view.findViewById(R.id.jobMeta)).setText(date + " · " + job.artifacts.size() + " 个产物");
        return view;
    }
}
