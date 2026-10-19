package com.local.comfymobile.adapter;

import android.view.View;
import android.widget.AdapterView;

public final class SimpleItemSelected implements AdapterView.OnItemSelectedListener {
    public interface Selection { void selected(int position); }
    private final Selection selection;
    public SimpleItemSelected(Selection selection) { this.selection = selection; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selection.selected(position); }
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}
