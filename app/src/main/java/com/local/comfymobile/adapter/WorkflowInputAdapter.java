package com.local.comfymobile.adapter;

import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.local.comfymobile.R;
import com.local.comfymobile.model.Entities.WorkflowInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowInputAdapter extends BaseAdapter {
    public interface MediaPicker { void choose(WorkflowInput input); }

    private final Context context;
    private final MediaPicker mediaPicker;
    private final List<WorkflowInput> inputs = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Uri> media = new LinkedHashMap<>();

    public WorkflowInputAdapter(Context context, MediaPicker mediaPicker) {
        this.context = context;
        this.mediaPicker = mediaPicker;
    }

    public void replace(List<WorkflowInput> newInputs) {
        inputs.clear();
        inputs.addAll(newInputs);
        values.clear();
        media.clear();
        for (WorkflowInput input : inputs) if (input.value != null) values.put(input.key, input.value);
        notifyDataSetChanged();
    }

    public Map<String, Object> values() { return new LinkedHashMap<>(values); }
    public Map<String, Uri> media() { return new LinkedHashMap<>(media); }
    public void setMedia(String key, Uri uri) { media.put(key, uri); notifyDataSetChanged(); }

    @Override public int getCount() { return inputs.size(); }
    @Override public WorkflowInput getItem(int position) { return inputs.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        WorkflowInput input = getItem(position);
        View row = LayoutInflater.from(context).inflate(R.layout.row_workflow_input, parent, false);
        TextView label = row.findViewById(R.id.inputLabel);
        label.setText(input.nodeTitle + " · " + input.label);
        TextView type = row.findViewById(R.id.inputType);
        type.setText(input.nodeType + " / " + input.name);

        switch (input.kind) {
            case "BOOLEAN": addBoolean(row, input); break;
            case "SELECT": addSelect(row, input); break;
            case "NUMBER": addNumber(row, input); break;
            case "IMAGE":
            case "VIDEO":
            case "AUDIO":
            case "FILE": addMedia(row, input); break;
            default: addText(row, input, "TEXTAREA".equals(input.kind));
        }
        return row;
    }

    private void addText(View row, WorkflowInput input, boolean multiline) {
        EditText editor = row.findViewById(multiline ? R.id.inputTextarea : R.id.inputText);
        editor.setVisibility(View.VISIBLE);
        editor.setText(stringValue(input));
        editor.addTextChangedListener(watcher(value -> values.put(input.key, value)));
    }

    private void addNumber(View row, WorkflowInput input) {
        EditText editor = row.findViewById(R.id.inputNumber);
        editor.setVisibility(View.VISIBLE);
        editor.setText(stringValue(input));
        editor.addTextChangedListener(watcher(value -> {
            try {
                if (input.value instanceof Integer || input.value instanceof Long) values.put(input.key, Long.parseLong(value));
                else values.put(input.key, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {}
        }));
        String range = input.min == null ? "" : "范围 " + input.min + " - " + input.max + (input.step == null ? "" : "，步长 " + input.step);
        editor.setHint(range);
    }

    private void addBoolean(View row, WorkflowInput input) {
        CheckBox box = row.findViewById(R.id.inputBoolean);
        box.setVisibility(View.VISIBLE);
        Object value = values.get(input.key);
        box.setChecked(value instanceof Boolean && (Boolean) value);
        box.setOnCheckedChangeListener((button, checked) -> values.put(input.key, checked));
    }

    private void addSelect(View row, WorkflowInput input) {
        Spinner spinner = row.findViewById(R.id.inputSelect);
        spinner.setVisibility(View.VISIBLE);
        List<String> labels = new ArrayList<>();
        for (Object choice : input.choices) labels.add(String.valueOf(choice));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.item_spinner, labels);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinner.setAdapter(adapter);
        int selected = labels.indexOf(stringValue(input));
        if (selected >= 0) spinner.setSelection(selected);
        if (!input.choices.isEmpty()) {
            spinner.setOnItemSelectedListener(new SimpleItemSelected(position -> values.put(input.key, input.choices.get(position))));
        }
    }

    private void addMedia(View row, WorkflowInput input) {
        Button button = row.findViewById(R.id.inputMedia);
        button.setVisibility(View.VISIBLE);
        Uri selected = media.get(input.key);
        button.setText(selected == null ? "选择" + mediaLabel(input.kind) : "已选择：" + selected.getLastPathSegment());
        button.setOnClickListener(view -> mediaPicker.choose(input));
    }

    private String stringValue(WorkflowInput input) {
        Object value = values.get(input.key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String mediaLabel(String kind) {
        if ("VIDEO".equals(kind)) return "视频";
        if ("AUDIO".equals(kind)) return "音频";
        if ("IMAGE".equals(kind)) return "图片";
        return "文件";
    }

    private TextWatcher watcher(ValueConsumer consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private interface ValueConsumer { void accept(String value); }
}
