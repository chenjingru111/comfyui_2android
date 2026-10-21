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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.local.comfymobile.R;
import com.local.comfymobile.model.Entities.WorkflowInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作流输入列表。
 *
 * 除了取值，还能按 ComfyUI 画布的分区整组启停（groupModes），
 * 以及单独启停某个节点，例如某一张参考图或某一段音频（nodeModes）。
 */
public final class WorkflowInputAdapter extends BaseAdapter {
    public interface MediaPicker { void choose(WorkflowInput input); }

    private final Context context;
    private final MediaPicker mediaPicker;
    private final List<WorkflowInput> all = new ArrayList<>();
    private final List<WorkflowInput> inputs = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Uri> media = new LinkedHashMap<>();
    /** 分区下标 -> 是否启用 */
    private final Map<String, Boolean> groupState = new LinkedHashMap<>();
    /** 节点 id -> 是否启用（对应 ComfyUI 里的旁路状态） */
    private final Map<String, Boolean> nodeState = new LinkedHashMap<>();
    private String query = "";
    private boolean showDisabled = false;
    /** 可切换的“流”：流的分区 id -> 这条流包含的全部分区 id（含嵌套小分区） */
    private final Map<Integer, List<Integer>> flowGroups = new LinkedHashMap<>();

    /** 告诉适配器有哪些“流”，以及每条流涵盖哪些分区（外层分区 + 它内部的小分区）。 */
    public void setFlowGroups(Map<Integer, List<Integer>> flows) {
        flowGroups.clear();
        flowGroups.putAll(flows);
    }

    /** 打开/关闭某条流（连同它内部的小分区）；打开时会把其它流关掉，保证只跑一条。 */
    public void toggleFlow(int groupId, boolean on) {
        List<Integer> target = flowGroups.get(groupId);
        if (target == null) return;
        if (on) {
            for (Map.Entry<Integer, List<Integer>> entry : flowGroups.entrySet()) {
                if (entry.getKey() == groupId) continue;
                for (int id : entry.getValue()) setGroupEnabled(id, false);
            }
        }
        for (int id : target) setGroupEnabled(id, on);
        applyFilter();
    }

    private void setGroupEnabled(int groupId, boolean on) {
        groupState.put(String.valueOf(groupId), on);
        for (WorkflowInput input : all) {
            if (input.groupId == groupId) nodeState.put(input.nodeId, on);
        }
    }

    public WorkflowInputAdapter(Context context, MediaPicker mediaPicker) {
        this.context = context;
        this.mediaPicker = mediaPicker;
    }

    public void replace(List<WorkflowInput> newInputs) {
        all.clear();
        all.addAll(newInputs);
        values.clear();
        media.clear();
        groupState.clear();
        nodeState.clear();

        Map<String, Boolean> groupAny = new LinkedHashMap<>();
        for (WorkflowInput input : all) {
            if (input.value != null) values.put(input.key, input.value);
            nodeState.put(input.nodeId, input.enabled);
            String key = String.valueOf(input.groupId);
            boolean any = Boolean.TRUE.equals(groupAny.get(key)) || input.enabled;
            groupAny.put(key, any);
        }
        groupState.putAll(groupAny);
        applyFilter();
    }

    public void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public void setShowDisabled(boolean show) {
        showDisabled = show;
        applyFilter();
    }

    private boolean isEnabled(WorkflowInput input) {
        Boolean group = groupState.get(String.valueOf(input.groupId));
        Boolean node = nodeState.get(input.nodeId);
        return (group == null || group) && (node == null || node);
    }

    private void applyFilter() {
        inputs.clear();
        for (WorkflowInput input : all) {
            boolean groupOn = !Boolean.FALSE.equals(groupState.get(String.valueOf(input.groupId)));
            // 画布上被旁路的节点默认不显示；用户把它的分区打开后才出现
            if (!showDisabled && !input.enabled && !groupOn) continue;
            if (query.isEmpty()) {
                inputs.add(input);
                continue;
            }
            String param = (input.label + " " + input.name).toLowerCase(Locale.ROOT);
            String node = (input.nodeTitle + " " + input.nodeType + " " + badgeOf(input.kind))
                    .toLowerCase(Locale.ROOT);
            if (param.contains(query) || node.contains(query)) inputs.add(input);
        }
        if (!query.isEmpty()) {
            inputs.sort((a, b) -> {
                boolean pa = (a.label + " " + a.name).toLowerCase(Locale.ROOT).contains(query);
                boolean pb = (b.label + " " + b.name).toLowerCase(Locale.ROOT).contains(query);
                return Boolean.compare(pb, pa);
            });
        }
        notifyDataSetChanged();
    }

    public Map<String, Object> values() { return new LinkedHashMap<>(values); }
    public Map<String, Uri> media() { return new LinkedHashMap<>(media); }

    /** 分区开关，键是分区下标（不含 -1 这种“无分区”）。 */
    public Map<String, Boolean> groupModes() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : groupState.entrySet()) {
            if ("-1".equals(entry.getKey())) continue;
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** 单节点开关，键是节点 id。 */
    public Map<String, Boolean> nodeModes() { return new LinkedHashMap<>(nodeState); }

    public int totalCount() { return all.size(); }

    public void setMedia(String key, Uri uri) {
        media.put(key, uri);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return inputs.size(); }
    @Override public WorkflowInput getItem(int position) { return inputs.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        WorkflowInput input = getItem(position);
        View row = LayoutInflater.from(context).inflate(R.layout.row_workflow_input, parent, false);

        View sectionRow = row.findViewById(R.id.sectionRow);
        String group = input.group == null ? "" : input.group;
        String previousGroup = position > 0 ? getItem(position - 1).group : "";
        if (!group.isEmpty() && !group.equals(previousGroup)) {
            sectionRow.setVisibility(View.VISIBLE);
            TextView title = row.findViewById(R.id.sectionHeader);
            boolean groupOn = !Boolean.FALSE.equals(groupState.get(String.valueOf(input.groupId)));
            title.setText(group + (groupOn ? "" : "　（整组已关闭）"));
            Switch groupSwitch = row.findViewById(R.id.groupSwitch);
            groupSwitch.setChecked(groupOn);
            if (input.groupId >= 0) {
                groupSwitch.setOnCheckedChangeListener((button, checked) -> {
                    String key = String.valueOf(input.groupId);
                    groupState.put(key, checked);
                    // 整组启停时，把组内每个节点也同步过去（否则单节点开关还停在旧状态）
                    for (WorkflowInput other : all) {
                        if (other.groupId == input.groupId) nodeState.put(other.nodeId, checked);
                    }
                    applyFilter();
                });
            } else {
                groupSwitch.setVisibility(View.GONE);
            }
        }

        boolean on = isEnabled(input);
        row.findViewById(R.id.inputCard).setAlpha(on ? 1f : 0.45f);

        TextView label = row.findViewById(R.id.inputLabel);
        label.setText(input.nodeTitle + " · " + input.label);
        TextView badge = row.findViewById(R.id.inputBadge);
        badge.setText(badgeOf(input.kind));
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

        if (!on) {
            for (int id : new int[]{R.id.inputText, R.id.inputTextarea, R.id.inputNumber,
                    R.id.inputBoolean, R.id.inputSelect, R.id.inputMedia}) {
                View control = row.findViewById(id);
                if (control != null) control.setEnabled(false);
            }
        }
        return row;
    }

    private static String badgeOf(String kind) {
        switch (kind) {
            case "IMAGE": return "图片";
            case "VIDEO": return "视频";
            case "AUDIO": return "音频";
            case "SELECT": return "下拉";
            case "NUMBER": return "数字";
            case "BOOLEAN": return "开关";
            case "TEXTAREA": return "长文本";
            case "FILE": return "文件";
            default: return "文本";
        }
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
        StringBuilder hint = new StringBuilder("输入数字");
        if (input.min != null && input.max != null) {
            hint = new StringBuilder("范围 " + trimNumber(input.min) + " ~ " + trimNumber(input.max));
            if (input.step != null) hint.append("，步长 ").append(trimNumber(input.step));
        }
        editor.setHint(hint);
    }

    private void addBoolean(View row, WorkflowInput input) {
        View container = row.findViewById(R.id.booleanRow);
        container.setVisibility(View.VISIBLE);
        Switch toggle = row.findViewById(R.id.inputBoolean);
        TextView state = row.findViewById(R.id.booleanState);
        Object value = values.get(input.key);
        boolean on = value instanceof Boolean && (Boolean) value;
        toggle.setChecked(on);
        state.setText(on ? "已开启" : "已关闭");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            values.put(input.key, checked);
            state.setText(checked ? "已开启" : "已关闭");
        });
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
        View container = row.findViewById(R.id.mediaRow);
        container.setVisibility(View.VISIBLE);
        TextView value = row.findViewById(R.id.mediaValue);
        Button button = row.findViewById(R.id.inputMedia);
        Switch useSwitch = row.findViewById(R.id.mediaSwitch);

        Uri picked = media.get(input.key);
        String current = picked != null ? picked.getLastPathSegment() : stringValue(input);
        boolean hasValue = current != null && !current.trim().isEmpty();
        value.setText(hasValue ? "当前：" + current : "未选择" + mediaLabel(input.kind));
        button.setText(hasValue ? "更换" : "选择");
        button.setOnClickListener(view -> mediaPicker.choose(input));

        boolean used = !Boolean.FALSE.equals(nodeState.get(input.nodeId));
        useSwitch.setChecked(used);
        useSwitch.setOnCheckedChangeListener((button1, checked) -> {
            nodeState.put(input.nodeId, checked);
            // 单独打开某个图片/音频时，把它所在分区也一起打开，否则整组关闭会把它压住
            if (checked && input.groupId >= 0) groupState.put(String.valueOf(input.groupId), true);
            applyFilter();
        });
    }

    private String stringValue(WorkflowInput input) {
        Object value = values.get(input.key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimNumber(Double value) {
        if (value == null) return "";
        if (value == Math.rint(value)) return String.valueOf(value.longValue());
        return String.valueOf(value);
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
