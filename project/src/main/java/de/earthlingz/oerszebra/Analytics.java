package de.earthlingz.oerszebra;

import android.os.Bundle;
import android.util.Log;
import com.shurik.droidzebra.GameState;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

public class Analytics {

    static final String ANALYTICS_SETTING = "analytics_setting";
    private static AtomicReference<DroidZebra> app = new AtomicReference<>();

    public static void setApp(DroidZebra zebra) {
        app.set(zebra);
    }

    public static void ask(DroidZebra app) {
        return;
    }


    public static void settingsChanged() {
    return;
    }

    public static void log(String id, String message) {
        Log.i(id, message);
    }

    public static void build() {

        if(app.get() == null) {
            return;
        }

        return;
    }


    public static void converse(String converse, @Nullable Bundle bundle) {
        Log.i("converse", converse);
        return;
    }

    public static void error(String msg, GameState state) {
        return;
    }
}
