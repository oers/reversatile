/* Copyright (C) 2010 by Alex Kompel  */
/* This file is part of DroidZebra.

    DroidZebra is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DroidZebra is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with DroidZebra.  If not, see <http://www.gnu.org/licenses/>
*/

package de.earthlingz.oerszebra;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import static de.earthlingz.oerszebra.GlobalSettingsLoader.SHARED_PREFS_NAME;


public class SettingsPreferences extends PreferenceActivity
	implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String[] DEPTH_KEYS = {
            GlobalSettingsLoader.SETTINGS_KEY_STRENGTH,
            GlobalSettingsLoader.SETTINGS_KEY_ANALYSIS_DEPTH,
    };

    // The summary from settings.xml, shown while a fixed level is picked.
    private final Map<String, CharSequence> defaultSummaries = new HashMap<>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(SHARED_PREFS_NAME);
	    addPreferencesFromResource(R.xml.settings);
        // Drawn edge to edge (from Android 15 on): let the list scroll under
        // the system bars, but end clear of them, so the first and last
        // settings can be reached.
        getListView().setClipToPadding(false);
        SystemBars.keepClear(getListView());
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        for (String key : DEPTH_KEYS) {
            setUpDepthPreference(key);
        }
    }

    /**
     * Picking "Custom…" in a strength list asks for the depths first, and
     * only selects it once they are confirmed - cancelling keeps the level
     * that was picked before. Picking it again edits the saved values.
     */
    private void setUpDepthPreference(String key) {
        ListPreference preference = (ListPreference) findPreference(key);
        if (preference == null) {
            return;
        }
        defaultSummaries.put(key, preference.getSummary());
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (SearchDepth.CUSTOM.equals(newValue)) {
                showCustomDepthDialog((ListPreference) pref);
                return false;
            }
            return true;
        });
        updateDepthSummary(preference);
    }

    private void showCustomDepthDialog(ListPreference preference) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        SearchDepth current = SearchDepth.readCustom(prefs, preference.getKey());

        NumberPicker midgame = newPicker(SearchDepth.MIN_MIDGAME, SearchDepth.MAX_MIDGAME, current.midgame);
        NumberPicker exact = newPicker(SearchDepth.MIN_EXACT, SearchDepth.MAX_EXACT, current.exact);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        layout.setPadding(padding, dp(8), padding, 0);
        layout.addView(newLabel(R.string.settings_strength_custom_midgame));
        layout.addView(midgame);
        layout.addView(newLabel(R.string.settings_strength_custom_endgame));
        layout.addView(exact);
        layout.addView(newLabel(R.string.settings_strength_custom_hint));

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_strength_custom_dialog_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    midgame.clearFocus(); // commits a value typed into the picker
                    exact.clearFocus();
                    prefs.edit()
                            .putString(SearchDepth.customKey(preference.getKey()),
                                    SearchDepth.format(midgame.getValue(), exact.getValue()))
                            .apply();
                    preference.setValue(SearchDepth.CUSTOM);
                    updateDepthSummary(preference);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateDepthSummary(ListPreference preference) {
        if (SearchDepth.CUSTOM.equals(preference.getValue())) {
            SearchDepth custom = SearchDepth.readCustom(
                    getPreferenceManager().getSharedPreferences(), preference.getKey());
            preference.setSummary(getString(R.string.settings_strength_custom_summary, custom.midgame, custom.exact));
        } else {
            preference.setSummary(defaultSummaries.get(preference.getKey()));
        }
    }

    private NumberPicker newPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(false);
        return picker;
    }

    private TextView newLabel(int text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
        for (String depthKey : DEPTH_KEYS) {
            if (depthKey.equals(key)) {
                ListPreference preference = (ListPreference) findPreference(depthKey);
                if (preference != null) {
                    updateDepthSummary(preference);
                }
            }
        }
	}
}
