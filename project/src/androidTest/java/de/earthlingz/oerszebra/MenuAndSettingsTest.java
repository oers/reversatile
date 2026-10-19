package de.earthlingz.oerszebra;

import android.content.Context;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;

import androidx.test.core.app.ActivityScenario;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;

public class MenuAndSettingsTest {

    // Undo, redo, back to the start and rotate are the buttons below the
    // board. The menu used to repeat them, which pushed everything else
    // into an overflow of eleven items.
    @Test
    public void menuDoesNotRepeatTheButtonsBelowTheBoard() {
        Context context = new ContextThemeWrapper(getInstrumentation().getTargetContext(),
                R.style.CustomActionBarTheme);
        List<Integer> ids = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> {
            PopupMenu popup = new PopupMenu(context, new View(context));
            popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());
            Menu menu = popup.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                ids.add(menu.getItem(i).getItemId());
            }
        });

        assertEquals(Arrays.asList(
                R.id.menu_new_game, R.id.menu_play_mode,
                R.id.menu_play_mode_choice, R.id.menu_switch_sides, R.id.menu_hint, R.id.menu_analyze_game,
                R.id.menu_enter_moves, R.id.menu_mail,
                R.id.menu_guess_move, R.id.menu_settings, R.id.menu_quit), ids);
    }

    // The keys are what the saved settings are stored under, so regrouping
    // must not lose or rename any of them.
    @Test
    public void settingsAreGroupedAndKeepTheirKeys() {
        Context context = getInstrumentation().getTargetContext();
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put(context.getString(R.string.settings_category_game), Arrays.asList(
                "settings_engine_function", "settings_engine_strength",
                "settings_engine_randomness", "settings_engine_auto_make_moves"));
        expected.put(context.getString(R.string.settings_category_openings), Arrays.asList(
                "settings_engine_use_book", "settings_engine_human_openings",
                "settings_engine_force_opening"));
        expected.put(context.getString(R.string.settings_category_training), Arrays.asList(
                "settings_engine_practice_mode", "settings_ui_display_pv",
                "settings_analysis_search_depth", "settings_analysis_drawer_side"));
        expected.put(context.getString(R.string.settings_category_display), Arrays.asList(
                "settings_ui_display_moves", "settings_ui_display_last_move",
                "settings_ui_display_enable_animations"));
        expected.put(context.getString(R.string.settings_category_other), Arrays.asList(
                "settings_sendmail", "analytics_setting"));

        Map<String, List<String>> actual = new LinkedHashMap<>();
        try (ActivityScenario<SettingsPreferences> scenario =
                     ActivityScenario.launch(SettingsPreferences.class)) {
            scenario.onActivity(activity -> {
                PreferenceScreen screen = activity.getPreferenceScreen();
                for (int i = 0; i < screen.getPreferenceCount(); i++) {
                    PreferenceCategory category = (PreferenceCategory) screen.getPreference(i);
                    List<String> keys = new ArrayList<>();
                    for (int j = 0; j < category.getPreferenceCount(); j++) {
                        keys.add(category.getPreference(j).getKey());
                    }
                    actual.put(String.valueOf(category.getTitle()), keys);
                }
            });
        }

        assertEquals(expected, actual);
    }
}
