package com.local.comfymobile.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.local.comfymobile.R;
import com.local.comfymobile.model.Entities.OutputEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** ComfyUI output 目录的浏览列表。 */
public final class OutputFilesAdapter extends BaseAdapter {
    private final Context context;
    private final List<OutputEntry> entries = new ArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public OutputFilesAdapter(Context context) {
        this.context = context;
    }

    public void replace(List<OutputEntry> items) {
        entries.clear();
        entries.addAll(items);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return entries.size(); }
    @Override public OutputEntry getItem(int position) { return entries.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        OutputEntry entry = getItem(position);
        View row = LayoutInflater.from(context).inflate(R.layout.row_output, parent, false);
        ((TextView) row.findViewById(R.id.outputName)).setText(entry.name);
        ((TextView) row.findViewById(R.id.outputKind)).setText(kindOf(entry));
        ((TextView) row.findViewById(R.id.outputMeta)).setText(metaOf(entry));
        return row;
    }

    private static String kindOf(OutputEntry entry) {
        if (entry.dir) return "文件夹";
        String mime = entry.mime == null ? "" : entry.mime;
        if (mime.startsWith("image/")) return "图片";
        if (mime.startsWith("video/")) return "视频";
        if (mime.startsWith("audio/")) return "音频";
        return "文件";
    }

    private String metaOf(OutputEntry entry) {
        String time = format.format(new Date(entry.modifiedAt * 1000L));
        if (entry.dir) return "打开查看 · " + time;
        return sizeOf(entry.size) + " · " + time;
    }

    private static String sizeOf(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024);
        return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024 / 1024);
    }
}
