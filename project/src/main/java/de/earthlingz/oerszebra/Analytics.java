package de.earthlingz.oerszebra;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import com.shurik.droidzebra.GameState;

import javax.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.MODE_PRIVATE;
import static de.earthlingz.oerszebra.GlobalSettingsLoader.SHARED_PREFS_NAME;

import org.matomo.sdk.Matomo;
import org.matomo.sdk.Tracker;
import org.matomo.sdk.TrackerBuilder;
import org.matomo.sdk.extra.TrackHelper;

public class Analytics {

    static final String ANALYTICS_SETTING = "analytics_setting";
    static final String FIRST_RUN = "isFirstRun";
    private static final AtomicReference<DroidZebra> app = new AtomicReference<>();
    private static Tracker tracker = null;
    private static boolean sessionStarted = false;
    // The open consent dialog, so onStart() running again while it's still
    // up (e.g. back from the home screen) doesn't stack a second one.
    private static WeakReference<AlertDialog> consentDialog = new WeakReference<>(null);

    public static void setApp(DroidZebra zebra) {
        app.set(zebra);
    }

    public static void ask(DroidZebra app) {
        final SharedPreferences settings =
                app.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        AlertDialog open = consentDialog.get();
        if (open != null && open.isShowing()) {
            return;
        }
        if (settings.getBoolean(FIRST_RUN, true)) {
            // Not cancelable, and FIRST_RUN is only cleared once the user
            // answers (see initSettings): a dismissed dialog used to count
            // as asked, leaving consent to whatever default applied later.
            AlertDialog shown = new AlertDialog.Builder(app)
                    .setTitle(R.string.ask_analytics)
                    .setMessage(R.string.ask_analytics_help)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ask_analytics_accept, (dialog, which) -> {
                        Analytics.initSettings(app, true);
                        askPlayMode(app);
                    })
                    .setNeutralButton(R.string.ask_analytics_deny, (dialog, which) -> {
                        Analytics.initSettings(app, false);
                        askPlayMode(app);
                    }).show();
            consentDialog = new WeakReference<>(shown);
        }
    }

    // Second onboarding step, right after the analytics consent: lets a new
    // user pick a sensible starting point instead of the raw default
    // settings. Just pre-fills the existing engine-function/practice-mode
    // preferences - no new persisted concept, everything stays changeable
    // in Settings afterwards.
    private static void askPlayMode(DroidZebra app) {
        CharSequence[] labels = {
                app.getString(R.string.onboarding_mode_analyze),
                app.getString(R.string.onboarding_mode_vs_human),
                app.getString(R.string.onboarding_mode_vs_computer)
        };
        new AlertDialog.Builder(app)
                .setTitle(R.string.onboarding_mode_title)
                .setCancelable(false)
                .setItems(labels, (dialog, which) -> applyPlayModeChoice(app, which))
                .show();
    }

    private static void applyPlayModeChoice(DroidZebra app, int which) {
        final SharedPreferences settings =
                app.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        switch (which) {
            case 0: // analyze games - human vs human, evals on so the board is informative right away
                editor.putString(GlobalSettingsLoader.SETTINGS_KEY_FUNCTION,
                        String.valueOf(GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN));
                editor.putBoolean(GlobalSettingsLoader.SETTINGS_KEY_PRACTICE_MODE, true);
                break;
            case 1: // play locally vs. another human
                editor.putString(GlobalSettingsLoader.SETTINGS_KEY_FUNCTION,
                        String.valueOf(GameSettingsConstants.FUNCTION_HUMAN_VS_HUMAN));
                editor.putBoolean(GlobalSettingsLoader.SETTINGS_KEY_PRACTICE_MODE, false);
                break;
            case 2: // play vs. the computer
            default:
                editor.putString(GlobalSettingsLoader.SETTINGS_KEY_FUNCTION,
                        String.valueOf(GameSettingsConstants.FUNCTION_ZEBRA_BLACK));
                editor.putBoolean(GlobalSettingsLoader.SETTINGS_KEY_PRACTICE_MODE, false);
                break;
        }
        editor.apply();
    }

    private static void initSettings(DroidZebra app, boolean consent) {
        final SharedPreferences settings =
                app.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        settings.edit()
                .putBoolean(ANALYTICS_SETTING, consent)
                .putBoolean(FIRST_RUN, false)
                .apply();

        handleConsent(app, consent);
    }

    public static void settingsChanged() {
        if (app.get() == null) {
            return;
        }
        handleConsent(app.get(), isConsent());
    }

    public static void log(String id, String message) {

        if(app.get() == null) {
            return;
        }

        TrackHelper.track().event(id, "E/Message: " + message).with(tracker);
    }

    public static void build() {

        if(app.get() == null) {
            return;
        }

        boolean consent = isConsent();

        handleConsent(app.get(), consent);
    }

    private static boolean isConsent() {
        if(app.get() == null) {
            return false;
        }
        DroidZebra droidZebra = app.get();
        final SharedPreferences settings =
                droidZebra.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        return settings.getBoolean(ANALYTICS_SETTING, false);
    }

    // Applies consent every time it's called, not just the first time: the
    // tracker exists from startup on, and returning early once it did meant
    // turning analytics off in Settings had no effect until a restart.
    private static void handleConsent(Context app, boolean consent) {
        Tracker current = getTracker(app);
        current.setOptOut(!consent);

        if (!consent || sessionStarted) {
            return;
        }
        sessionStarted = true;

        current.startNewSession();
        current.setUserId(UUID.randomUUID().toString());

        TrackHelper.track().uncaughtExceptions().with(current);
        TrackHelper.track().download().with(current);
    }

    private synchronized static Tracker getTracker(Context app) {
        if(tracker == null) {
            tracker = TrackerBuilder.createDefault("https://matomo.reversatile.online/matomo.php", 2).build(Matomo.getInstance(app));
        }
        return tracker;
    }

    public static void converse(String converse, @Nullable Bundle bundle) {
        if(!isConsent()) {
            Log.i("converse", converse);
            return;
        }
        if(app.get() != null) {
            Tracker fb = getTracker(app.get());
            TrackHelper.track().screen( converse).with(fb);

        }

    }

    public static void error(String msg, GameState state) {
        String message = msg + (state!=null? " -" + state.getMoveSequenceAsString():"");
        if(!isConsent()) {
            Log.e("alert", message);
            return;
        }

        if(app.get() == null) {
            return;
        }
        TrackHelper.track().event("error", "E/Message: " + message).with(getTracker(app.get()));
    }
}
