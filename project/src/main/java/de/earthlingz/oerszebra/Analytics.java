package de.earthlingz.oerszebra;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import com.shurik.droidzebra.GameState;

import javax.annotation.Nullable;

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
    private static final AtomicReference<DroidZebra> app = new AtomicReference<>();
    private static Tracker tracker = null;

    public static void setApp(DroidZebra zebra) {
        app.set(zebra);
    }

    public static void ask(DroidZebra app) {
        final SharedPreferences settings =
                app.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        if (settings.getBoolean("isFirstRun", true)) {
            settings.edit().putBoolean("isFirstRun", false).apply();
            new AlertDialog.Builder(app)
                    .setTitle(R.string.ask_analytics)
                    .setMessage(R.string.ask_analytics_help)
                    .setPositiveButton(R.string.ask_analytics_accept, (dialog, which) -> Analytics.initSettings(app, true))
                    .setNeutralButton(R.string.ask_analytics_deny, (dialog, which) -> Analytics.initSettings(app, false)).show();
        }
    }

    private static void initSettings(DroidZebra app, boolean consent) {
        final SharedPreferences settings =
                app.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        settings.edit().putBoolean(ANALYTICS_SETTING, consent).apply();

        handleConsent(app, consent);
    }

    public static void settingsChanged() {
        final SharedPreferences settings =
                app.get().getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        boolean consent = settings.getBoolean(ANALYTICS_SETTING, true);

        handleConsent(app.get(), consent);
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

    private static void handleConsent(Context app, boolean consent) {
        if(tracker != null) { //already initialised
            return;
        }

        getTracker(app);

        tracker.setOptOut(!consent);

        if (!consent) {
            return;
        }

        tracker.startNewSession();
        tracker.setUserId(UUID.randomUUID().toString());

        TrackHelper.track().uncaughtExceptions().with(tracker);
        TrackHelper.track().download().with(tracker);
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
        TrackHelper.track().event("error", "E/Message: " + message);
    }
}
