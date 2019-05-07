package net.eneiluj.moneybuster.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;


/**
 * Receiver for boot completed broadcast
 *
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    /**
     * Broadcast received on system boot completed.
     * Starts background logging service
     *
     * @param context Context
     * @param intent Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        /*boolean autoStart = prefs.getBoolean(context.getString(R.string.pref_key_autostart), false);
        if (autoStart && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent loggerIntent = new Intent(context, LoggerService.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                context.startService(loggerIntent);
            } else {
                context.startForegroundService(loggerIntent);
            }
        }*/
    }
}
