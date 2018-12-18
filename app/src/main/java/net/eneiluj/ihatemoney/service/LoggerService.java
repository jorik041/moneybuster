/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.eneiluj.ihatemoney.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.activity.LogjobsListViewActivity;
import net.eneiluj.ihatemoney.android.fragment.PreferencesFragment;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.persistence.PhoneTrackSQLiteOpenHelper;

import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */

public class LoggerService extends Service {

    public static float battery = -1.0f;

    private static final String TAG = LoggerService.class.getSimpleName();
    public static final String BROADCAST_LOCATION_STARTED = "net.eneiluj.ihatemoney.broadcast.location_started";
    public static final String BROADCAST_LOCATION_STOPPED = "net.eneiluj.ihatemoney.broadcast.location_stopped";
    public static final String BROADCAST_LOCATION_UPDATED = "net.eneiluj.ihatemoney.broadcast.location_updated";
    public static final String BROADCAST_LOCATION_PERMISSION_DENIED = "net.eneiluj.ihatemoney.broadcast.location_permission_denied";
    public static final String BROADCAST_LOCATION_NETWORK_DISABLED = "net.eneiluj.ihatemoney.broadcast.network_disabled";
    public static final String BROADCAST_LOCATION_GPS_DISABLED = "net.eneiluj.ihatemoney.broadcast.gps_disabled";
    public static final String BROADCAST_LOCATION_NETWORK_ENABLED = "net.eneiluj.ihatemoney.broadcast.network_enabled";
    public static final String BROADCAST_LOCATION_GPS_ENABLED = "net.eneiluj.ihatemoney.broadcast.gps_enabled";
    public static final String BROADCAST_LOCATION_DISABLED = "net.eneiluj.ihatemoney.broadcast.location_disabled";
    public static final String BROADCAST_EXTRA_PARAM = "net.eneiluj.ihatemoney.broadcast.extra_param";
    public static final String BROADCAST_ERROR_MESSAGE = "net.eneiluj.ihatemoney.broadcast.error_message";
    public static final String UPDATE_NOTIFICATION = "net.eneiluj.ihatemoney.UPDATE_NOTIFICATION";
    private boolean liveSync = false;
    private Intent syncIntent;

    private static volatile boolean isRunning = false;
    private static volatile boolean firstRun = false;
    private LoggerThread thread;
    private Looper looper;
    private LocationManager locManager;
    private Map<String, mLocationListener> locListeners;
    private Map<String, DBLogjob> logjobs;
    private PhoneTrackSQLiteOpenHelper db;

    private Map<String, Location> lastLocations;
    private static volatile Map<String, Long> lastUpdateRealtime;

    private final int NOTIFICATION_ID = 1526756640;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private boolean useGps = true;
    private boolean useNet = true;
    public static boolean DEBUG = true;

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "[onCreate]");
        }
        firstRun = true;

        db = PhoneTrackSQLiteOpenHelper.getInstance(getApplicationContext());

        syncIntent = new Intent(getApplicationContext(), WebTrackService.class);
        // start websync service if needed
        if (db.getLocationCount() > 0) {
            startService(syncIntent);
        }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        lastLocations = new HashMap<>();
        lastUpdateRealtime = new HashMap<>();
        locListeners = new HashMap<>();
        logjobs = new HashMap<>();

        List<DBLogjob> ljs = db.getLogjobs();
        for (DBLogjob ljob : ljs) {
            if (ljob.isEnabled()) {
                mLocationListener ll = new mLocationListener(ljob);
                locListeners.put(String.valueOf(ljob.getId()), ll);
                logjobs.put(String.valueOf(ljob.getId()), ljob);
                lastLocations.put(String.valueOf(ljob.getId()), null);
                lastUpdateRealtime.put(String.valueOf(ljob.getId()), Long.valueOf(0));
            }
        }

        // read user preferences
        updatePreferences(null);

        boolean hasLocationUpdates = false;
        for (DBLogjob lj : ljs) {
            if (lj.isEnabled()) {
                hasLocationUpdates = requestLocationUpdates(String.valueOf(lj.getId()));
            }
        }

        if (hasLocationUpdates) {
            final Notification notification = showNotification(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, notification);
            updateNotificationContent();

            isRunning = true;

            sendBroadcast(BROADCAST_LOCATION_STARTED);

            thread = new LoggerThread();
            thread.start();
            looper = thread.getLooper();

            battery = getBatteryLevelOnce();
            // register for battery level
            this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        else {
            if (DEBUG) { Log.d(TAG, "[onCreate : stop because no loc upd]"); }
            stopSelf();
        }
    }

    /**
     * Start main thread, request location updates, start synchronization.
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return Always returns START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            final boolean logjobsUpdated = (intent != null) && intent.getBooleanExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, false);
            final boolean providersUpdated = (intent != null) && intent.getBooleanExtra(PreferencesFragment.UPDATED_PROVIDERS, false);
            final boolean updateNotif = (intent != null) && intent.getBooleanExtra(UPDATE_NOTIFICATION, false);
            if (logjobsUpdated) {
                // this to avoid doing two loc upd when service is down and then a logjob is enabled
                // in this scenario, we run onCreate which already does it all, no need to handle logjo updated
                if (firstRun) {
                    if (DEBUG) {
                        Log.d(TAG, "[onStartCommand : upd logjob but firstrun so nothing]");
                    }
                } else {
                    String ljId = String.valueOf(intent.getLongExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, 0));
                    if (DEBUG) {
                        Log.d(TAG, "[onStartCommand : upd logjob]");
                    }
                    handleLogjobsUpdated(ljId);
                }
            } else if (providersUpdated) {
                if (DEBUG) {
                    Log.d(TAG, "[onStartCommand : upd providers]");
                }
                String providersValue = intent.getStringExtra(PreferencesFragment.UPDATED_PROVIDERS_VALUE);
                updatePreferences(providersValue);
                boolean hasLocationUpdates = false;
                for (String ljId : logjobs.keySet()) {
                    hasLocationUpdates = requestLocationUpdates(ljId);
                }
                if (!hasLocationUpdates) {
                    stopSelf();
                }
            } else if (updateNotif && isRunning) {
                updateNotificationContent();
            } else {
                // start without parameter
                if (DEBUG) {
                    Log.d(TAG, "[onStartCommand : start without parameter]");
                }
            }
            // anyway, first run is over
            firstRun = false;
        }

        return START_STICKY;
    }

    /**
     * When user updated a logjob, restart location updates, stop service on failure
     */
    private void handleLogjobsUpdated(String ljId) {
        boolean wasAlreadyThere = logjobs.containsKey(ljId);
        updateLogjob(ljId);
        // if it was not deleted or disabled
        if (logjobs.containsKey(ljId)) {
            if (isRunning) {
                // it was modified
                if (wasAlreadyThere) {
                    if (!restartUpdates(ljId)) {
                        // no valid providers after logjob update
                        stopSelf();
                    }
                }
                // it was created
                else {
                    if (!requestLocationUpdates(ljId)) {
                        stopSelf();
                    }
                }
            }
        }
        // it was deleted or disabled
        else {
            if (logjobs.isEmpty()) {
                stopSelf();
            }
        }
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    private boolean canAccessLocation() {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Check if given provider exists on device
     * @param provider Provider
     * @return True if exists, false otherwise
     */
    private boolean providerExists(String provider) {
        return locManager.getAllProviders().contains(provider);
    }

    /**
     * Reread preferences
     */
    private void updatePreferences(String value) {
        String providersPref;
        if (value == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            providersPref = prefs.getString(getString(R.string.pref_key_providers), "1");
        }
        else {
            providersPref = value;
        }
        useGps = (!providersPref.equals("2") && providerExists(LocationManager.GPS_PROVIDER));
        useNet = (!providersPref.equals("1") && providerExists(LocationManager.NETWORK_PROVIDER));
        if (DEBUG) { Log.d(TAG, "[update prefs "+providersPref+", gps : "+useGps+", net : "+useNet+"]"); }
        //liveSync = prefs.getBoolean("prefLiveSync", false);
    }

    /**
     * update internal values
     *
     * logjob might have been added, modified or deleted
     *
     */
    private void updateLogjob(String ljId) {
        DBLogjob lj = db.getLogjob(Long.valueOf(ljId));
        if (lj != null && lj.isEnabled()) {
            // new or modified : update logjob
            logjobs.put(ljId, lj);

            // this is a new logjob
            if (!locListeners.containsKey(ljId)) {
                mLocationListener ll = new mLocationListener(lj);
                locListeners.put(ljId, ll);
                lastLocations.put(ljId, null);
                lastUpdateRealtime.put(ljId, Long.valueOf(0));
            }
        }
        // it has been deleted or disabled
        else {
            if (locListeners.containsKey(ljId)) {
                locManager.removeUpdates(locListeners.get(ljId));
                locListeners.remove(ljId);
                lastLocations.remove(ljId);
                lastUpdateRealtime.remove(ljId);
                logjobs.remove(ljId);
            }
        }
    }

    /**
     * Restart request for location updates
     *
     * @return True if succeeded, false otherwise (eg. disabled all providers)
     */
    private boolean restartUpdates(String jobId) {
        if (DEBUG) { Log.d(TAG, "[job "+jobId+"location updates restart]"); }

        locManager.removeUpdates(locListeners.get(jobId));

        return requestLocationUpdates(jobId);
    }

    /**
     * Request location updates
     * @return True if succeeded from at least one provider
     */
    @SuppressWarnings({"MissingPermission"})
    private boolean requestLocationUpdates(String ljId) {
        // TODO here we start a location request for each activated logjob
        DBLogjob lj = logjobs.get(ljId);
        int minTimeMillis = lj.getMinTime() * 1000;
        int minDistance = lj.getMinDistance();
        mLocationListener locListener = locListeners.get(ljId);
        boolean hasLocationUpdates = false;
        if (canAccessLocation()) {
            if (useNet) {
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMillis, minDistance, locListener, looper);
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using net provider]"); }
                }
            }
            if (useGps) {
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMillis, minDistance, locListener, looper);
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+"[Using gps provider]"); }
                }
            }
            if (!hasLocationUpdates) {
                // no location provider available
                sendBroadcast(BROADCAST_LOCATION_DISABLED);
                if (DEBUG) { Log.d(TAG, "job "+ljId+"[No available location updates]"); }
            }
        } else {
            // can't access location
            sendBroadcast(BROADCAST_LOCATION_PERMISSION_DENIED);
            if (DEBUG) { Log.d(TAG, "job "+ljId+"[Location permission denied]"); }
        }

        return hasLocationUpdates;
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {
        if (DEBUG) { Log.d(TAG, "[onDestroy]"); }

        if (canAccessLocation()) {
            //noinspection MissingPermission
            for (Map.Entry<String, mLocationListener> entry : locListeners.entrySet()) {
                locManager.removeUpdates(entry.getValue());
            }
        }

        isRunning = false;

        mNotificationManager.cancel(NOTIFICATION_ID);


        if (thread != null) {
            thread.interrupt();
            unregisterReceiver(mBatInfoReceiver);
            sendBroadcast(BROADCAST_LOCATION_STOPPED);
        }
        thread = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Check if logger service is running.
     *
     * @return True if running, false otherwise
     */
    public static boolean isRunning() {
        return isRunning;
    }

    /**
     * Return realtime of last update in milliseconds
     *
     * @return Time or zero if not set
     */
    public static long lastUpdateRealtime(String ljId) {
        return lastUpdateRealtime.get(ljId);
    }

    /**
     * Reset realtime of last update
     */
    public static void resetUpdateRealtime(String ljId) {
        lastUpdateRealtime.put(ljId, Long.valueOf(0));
    }

    /**
     * Main service thread class handling location updates.
     */
    private class LoggerThread extends HandlerThread {
        LoggerThread() {
            super("LoggerThread");
        }
        private final String TAG = LoggerThread.class.getSimpleName();

        @Override
        public void interrupt() {
            if (DEBUG) { Log.d(TAG, "[interrupt]"); }
        }

        @Override
        public void finalize() throws Throwable {
            if (DEBUG) { Log.d(TAG, "[finalize]"); }
            super.finalize();
        }

        @Override
        public void run() {
            if (DEBUG) { Log.d(TAG, "[run]"); }
            super.run();
        }
    }

    /**
     * Show notification
     *
     * @param mId Notification Id
     */
    private Notification showNotification(int mId) {
        if (DEBUG) { Log.d(TAG, "[showNotification " + mId + "]"); }

        final String channelId = String.valueOf(mId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId);
        }
        String nbLocations = String.valueOf(db.getLocationCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
                        //.setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        //.setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        mNotificationBuilder = mBuilder;

        Intent resultIntent = new Intent(this, LogjobsListViewActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(LogjobsListViewActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        mNotificationManager.notify(mId, mNotification);
        return mNotification;
    }

    private void updateNotificationContent() {
        String nbLocations = String.valueOf(db.getLocationCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        mNotificationBuilder.setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
        mNotificationManager.notify(this.NOTIFICATION_ID, mNotificationBuilder.build());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId) {
        NotificationChannel chan = new NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(chan);
    }

    /**
     * Send broadcast message
     * @param broadcast Broadcast message
     */
    private void sendBroadcast(String broadcast) {
        Intent intent = new Intent(broadcast);
        sendBroadcast(intent);
    }

    /**
     * Send broadcast message
     * @param broadcast Broadcast message
     */
    private void sendBroadcast(String broadcast, String ljId) {
        Intent intent = new Intent(broadcast);
        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
        sendBroadcast(intent);
    }

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if(level == -1 || scale == -1) {
                battery = 0.0f;
            }
            battery = ((float)level / (float)scale) * 100.0f;
            if (LoggerService.DEBUG) { Log.d(TAG, "[BATT changed " + battery + "]"); }
        }
    };

    private float getBatteryLevelOnce() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 0.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }

    /**
     * Location listener class
     */
    private class mLocationListener implements LocationListener {

        private DBLogjob logjob;
        private String logjobId;
        private long minTimeTolerance;
        private long maxTimeMillis;

        public mLocationListener(DBLogjob logjob) {
            this.logjob = logjob;
            this.logjobId = String.valueOf(logjob.getId());
            // max time tolerance is half min time, but not more that 5 min
            int minTimeMillis = logjob.getMinTime() * 1000;
            minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
            maxTimeMillis = minTimeMillis + minTimeTolerance;
        }

        @Override
        public void onLocationChanged(Location loc) {

            if (DEBUG) { Log.d(TAG, "[location changed: " + logjobId + "/"+ logjob.getTitle() +" : bat : "+ battery+", " + loc + "]"); }

            if (!skipLocation(logjob, loc)) {

                lastLocations.put(logjobId, loc);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    lastUpdateRealtime.put(logjobId, SystemClock.elapsedRealtime());
                } else {
                    lastUpdateRealtime.put(logjobId, loc.getElapsedRealtimeNanos() / 1000000);
                }
                db.addLocation(logjobId, loc, battery);

                sendBroadcast(BROADCAST_LOCATION_UPDATED, logjobId);
                updateNotificationContent();

                Intent syncOneDev = new Intent(getApplicationContext(), WebTrackService.class);
                syncOneDev.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, logjobId);
                startService(syncOneDev);
            }
        }

        /**
         * Should the location be logged or skipped
         * @param loc Location
         * @return True if skipped
         */
        private boolean skipLocation(DBLogjob logjob, Location loc) {
            // TODO adapt to use logjob values
            int maxAccuracy = logjob.getMinAccuracy();
            // accuracy radius too high
            if (loc.hasAccuracy() && loc.getAccuracy() > maxAccuracy) {
                if (DEBUG) { Log.d(TAG, "[location accuracy above limit: " + loc.getAccuracy() + " > " + maxAccuracy + "]"); }
                // reset gps provider to get better accuracy even if time and distance criteria don't change
                if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                    restartUpdates(logjobId);
                }
                return true;
            }
            // use network provider only if recent gps data is missing
            if (loc.getProvider().equals(LocationManager.NETWORK_PROVIDER) && lastLocations.get(logjobId) != null) {
                // we received update from gps provider not later than after maxTime period
                long elapsedMillis = SystemClock.elapsedRealtime() - lastUpdateRealtime.get(logjobId);
                if (lastLocations.get(logjobId).getProvider().equals(LocationManager.GPS_PROVIDER) && elapsedMillis < maxTimeMillis) {
                    // skip network provider
                    if (DEBUG) { Log.d(TAG, "[location network provider skipped]"); }
                    return true;
                }
            }
            return false;
        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " disabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_DISABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_DISABLED);
            }
        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(String provider) {
            if (DEBUG) { Log.d(TAG, "[location provider " + provider + " enabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_ENABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_ENABLED);
            }
        }

        /**
         * Callback on provider status change
         * @param provider Provider
         * @param status Status
         * @param extras Extras
         */
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (DEBUG) {
                final String statusString;
                switch (status) {
                    case OUT_OF_SERVICE:
                        statusString = "out of service";
                        break;
                    case TEMPORARILY_UNAVAILABLE:
                        statusString = "temporarily unavailable";
                        break;
                    case AVAILABLE:
                        statusString = "available";
                        break;
                    default:
                        statusString = "unknown";
                        break;
                }
                if (DEBUG) { Log.d(TAG, "[location status for " + provider + " changed: " + statusString + "]"); }
            }
        }
    }
}
