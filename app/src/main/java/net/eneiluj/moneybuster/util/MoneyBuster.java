package net.eneiluj.moneybuster.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import net.eneiluj.moneybuster.R;


public class MoneyBuster extends Application {
    private static final String MODE_NIGHT = "modeNight";

    @Override
    public void onCreate() {
        setAppTheme(getAppTheme(getApplicationContext()));
        super.onCreate();
    }

    public static void setAppTheme(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int getAppTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String strValue = prefs.getString(context.getString(R.string.pref_key_night_mode), String.valueOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        return Integer.parseInt(strValue);
    }

    public static boolean isDarkTheme(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                return false;
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
        }
        return false;
    }
}
