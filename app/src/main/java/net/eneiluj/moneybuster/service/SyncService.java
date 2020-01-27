/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.eneiluj.moneybuster.service;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.preference.PreferenceManager;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */

public class SyncService extends Service {

    private static final String TAG = SyncService.class.getSimpleName();

    private static volatile boolean isRunning = false;
    private static volatile boolean firstRun = false;
    private SyncServiceThread thread;
    private Looper looper;
    private MoneyBusterSQLiteOpenHelper db;

    private final int NOTIFICATION_ID = 1526756648;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    public static boolean DEBUG = true;

    private LogjobWorker mSyncWorker;

    private ConnectionStateMonitor connectionMonitor;
    private BroadcastReceiver powerSaverChangeReceiver;
    private BroadcastReceiver airplaneModeChangeReceiver;

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "[onCreate]");
        }
        firstRun = true;

        connectionMonitor = null;
        powerSaverChangeReceiver = null;
        airplaneModeChangeReceiver = null;

        db = MoneyBusterSQLiteOpenHelper.getInstance(getApplicationContext());

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        mSyncWorker = new LogjobWorker();


        final Notification notification = showNotification(NOTIFICATION_ID);
        startForeground(NOTIFICATION_ID, notification);
        updateNotificationContent();

        isRunning = true;

        //sendBroadcast(BROADCAST_LOCATION_STARTED);

        thread = new SyncServiceThread();
        thread.start();
        looper = thread.getLooper();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // track network connectivity changes
            connectionMonitor = new ConnectionStateMonitor();
            connectionMonitor.enable(getApplicationContext());
        }

        // listen to power saving mode change
        powerSaverChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "[POWER LISTENER] power saving state changed");
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean respectPowerSaveMode = prefs.getBoolean(getString(R.string.pref_key_power_saving_awareness), false);
                if (respectPowerSaveMode) {
                    updateAllActiveLogjobs();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.os.action.POWER_SAVE_MODE_CHANGED");
        registerReceiver(powerSaverChangeReceiver, filter);

        // listen to offline (airplane) mode change
        airplaneModeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "[AIRPLANE MODE LISTENER] airplane mode state changed");
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                boolean respectAirplaneMode = prefs.getBoolean(getString(R.string.pref_key_offline_mode_awareness), false);
                if (respectAirplaneMode) {
                    updateAllActiveLogjobs();
                }
            }
        };
        IntentFilter filterAirplane = new IntentFilter();
        //filterAirplane.addAction("android.intent.action.AIRPLANE_MODE_CHANGED");
        filterAirplane.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(airplaneModeChangeReceiver, filterAirplane);

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
                    long ljId = intent.getLongExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, 0);
                    if (DEBUG) {
                        Log.d(TAG, "[onStartCommand : upd logjob]");
                    }
                    handleLogjobUpdated(ljId);
                }
            } else if (providersUpdated) {
                if (DEBUG) {
                    Log.d(TAG, "[onStartCommand : upd providers]");
                }
                String providersValue = intent.getStringExtra(PreferencesFragment.UPDATED_PROVIDERS_VALUE);
                updatePreferences(providersValue);
                for (long ljId : logjobs.keySet()) {
                    restartUpdates(ljId);
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
    private void handleLogjobUpdated(long ljId) {
        boolean wasAlreadyThere = logjobs.containsKey(ljId);
        updateLogjob(ljId);
        // if it was not deleted or disabled
        if (logjobs.containsKey(ljId)) {
            if (isRunning) {
                // it was modified
                if (wasAlreadyThere) {
                    restartUpdates(ljId);
                }
                // it was created
                else {
                    requestLocationUpdates(ljId, true, true);
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

    private void updateAllActiveLogjobs() {
        List<DBLogjob> logjobs = db.getLogjobs();
        // we tell logger service to restart updates for enabled logjobs
        for (DBLogjob lj: logjobs) {
            if (lj.isEnabled()) {
                handleLogjobUpdated(lj.getId());
            }
        }
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    private boolean canAccessLocation() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // first we check is device is in power saving mode
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isPowerSaveMode = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pm != null) {
            isPowerSaveMode = pm.isPowerSaveMode();
        }
        if (DEBUG) { Log.d(TAG, "POWEEEEEEEEE "+ isPowerSaveMode); }
        if (DEBUG) { Log.d(TAG, "AIRPLANEEEEEEEEEEEEEEEEEEEEEEEE "+ SupportUtil.isAirplaneModeOn(this)); }

        boolean respectPowerSaveMode = prefs.getBoolean(getString(R.string.pref_key_power_saving_awareness), false);

        // then we check airplane mode related stuff
        boolean respectAirplaneMode = prefs.getBoolean(getString(R.string.pref_key_offline_mode_awareness), false);

        // then we check if we have location permissions
        boolean hasLocPermissions = (
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
        );

        return (!respectPowerSaveMode || !isPowerSaveMode)
                && (!respectAirplaneMode || !SupportUtil.isAirplaneModeOn(this))
                && hasLocPermissions;
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
        useGps = ((providersPref.equals("1")
                   || providersPref.equals("3")
                   || providersPref.equals("5")
                   || providersPref.equals("7")
                 ) && providerExists(LocationManager.GPS_PROVIDER));
        useNet = ((providersPref.equals("2")
                   || providersPref.equals("3")
                   || providersPref.equals("6")
                   || providersPref.equals("7")
                 ) && providerExists(LocationManager.NETWORK_PROVIDER));
        usePassive = ((providersPref.equals("4")
                || providersPref.equals("5")
                || providersPref.equals("6")
                || providersPref.equals("7")
        ) && providerExists(LocationManager.PASSIVE_PROVIDER));
        if (DEBUG) { Log.d(TAG, "[update prefs "+providersPref+", gps : "+useGps+
                                     ", net : "+useNet+", passive : "+usePassive+"]"); }
    }

    /**
     * update internal values
     *
     * logjob might have been added, modified or deleted
     *
     */
    private void updateLogjob(long ljId) {
        DBLogjob lj = db.getLogjob(ljId);
        if (lj != null && lj.isEnabled()) {
            // new or modified : update logjob
            logjobs.put(ljId, lj);

            mLocationListener ll;
            // this is a new logjob
            if (!locListeners.containsKey(ljId)) {
                ll = new mLocationListener(lj);
                locListeners.put(ljId, ll);
                lastLocations.put(ljId, null);
                lastUpdateRealtime.put(ljId, Long.valueOf(0));
            } else {
                ll = locListeners.get(ljId);
                // Update listener for changed parameters
                ll.populateFromLogjob(lj);

                mLogjobWorkers.get(ljId).stop();
                //mLogjobWorkers.get(ljId).populate(lj);
            }

            // anyway (new/existing logjob) we instanciate a new one
            LogjobWorker jw;
            if (lj.useSignificantMotion()) {
                // Assume motion exists when logging begins
                jw = new LogjobSignificantMotionWorker(lj, ll);
            } else {
                if (lj.keepGpsOnBetweenFixes()) {
                    jw = new LogjobClassicGpsOnWorker(lj, ll);
                }
                else {
                    jw = new LogjobClassicWorker(lj, ll);
                }
            }
            mLogjobWorkers.put(ljId, jw);
        }
        // it has been deleted or disabled
        else {
            if (locListeners.containsKey(ljId)) {
                // Stop requested updates, sleeping motion-based job
                stopJob(ljId);

                locListeners.remove(ljId);
                lastLocations.remove(ljId);
                lastUpdateRealtime.remove(ljId);
                logjobs.remove(ljId);
                mLogjobWorkers.remove(ljId);
            }
        }
    }

    /**
     * Restart request for location updates
     *
     * @return True if succeeded, false otherwise (eg. disabled all providers)
     */
    private boolean restartUpdates(long jobId) {
        if (DEBUG) { Log.d(TAG, "[job "+jobId+" location updates restart]"); }

        stopJob(jobId);

        return requestLocationUpdates(jobId, true, true);
    }

    private void stopJob(long jobId) {
        locManager.removeUpdates(locListeners.get(jobId));

        // stop any runnables waiting for an interval
        DBLogjob lj = db.getLogjob(jobId);
        if (lj != null) {
            mLogjobWorkers.get(jobId).stop();
        }
    }

    /**
     * Request location updates
     * @return True if succeeded from at least one provider
     */
    @SuppressWarnings({"MissingPermission"})
    private boolean requestLocationUpdates(long ljId, boolean startTimeout, boolean firstRequestAfterAccepted) {
        // here we start a location request for each activated logjob
        DBLogjob lj = logjobs.get(ljId);
        int minTimeMillis = lj.getMinTime() * 1000;
        int minDistance = lj.getMinDistance();
        boolean keepGpsOn = lj.keepGpsOnBetweenFixes();
        mLocationListener locListener = locListeners.get(ljId);
        boolean hasLocationUpdates = false;
        if (canAccessLocation()) {
            // update last acquisition start time only if we know
            // we are not in an "accuracy improvement" loop
            // in other words: if we start a timeout
            if (firstRequestAfterAccepted) {
                mLogjobWorkers.get(ljId).updateLastAcquisitionStart();
            }
            if (useNet) {
                // normal or significant motion based sampling, request single update
                // the worker takes care of looping
                locManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locListener, looper);

                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using net provider, freq "+lj.getMinTime()+"]"); }
                }
            }
            if (usePassive) {
                locManager.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, locListener, looper);

                if (locManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using passive provider, freq "+lj.getMinTime()+"]"); }
                }
            }
            if (useGps) {
                locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locListener, looper);

                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (DEBUG) { Log.d(TAG, "job "+ljId+" [Using gps provider, freq "+(minTimeMillis/1000)+"]"); }
                }
            }
            if (hasLocationUpdates) {
                // start timeout only if we're not in an "accuracy improvement" loop
                if (startTimeout) {
                    mLogjobWorkers.get(ljId).startResultTimeout();
                }
            } else {
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
            for (long ljId : locListeners.keySet()) {
                stopJob(ljId);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (connectionMonitor != null) {
                connectionMonitor.disable(getApplicationContext());
            }
        }

        if (powerSaverChangeReceiver != null) {
            unregisterReceiver(powerSaverChangeReceiver);
        }
        if (airplaneModeChangeReceiver != null) {
            unregisterReceiver(airplaneModeChangeReceiver);
        }
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
    public static long lastUpdateRealtime(long ljId) {
        return lastUpdateRealtime.get(ljId);
    }

    /**
     * Reset realtime of last update
     */
    public static void resetUpdateRealtime(long ljId) {
        lastUpdateRealtime.put(ljId, Long.valueOf(0));
    }

    /**
     * Main service thread class handling location updates.
     */
    private class SyncServiceThread extends HandlerThread {
        SyncServiceThread() {
            super("SyncServiceThread");
        }
        private final String TAG = SyncServiceThread.class.getSimpleName();

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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean lowImportance = prefs.getBoolean(getString(R.string.pref_key_notification_importance), false);
        int priority = NotificationCompat.PRIORITY_DEFAULT;
        if (lowImportance) {
            priority = NotificationCompat.PRIORITY_MIN;
        }

        final String channelId = String.valueOf(mId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId, lowImportance);
        }
        String nbLocations = String.valueOf(db.getLocationNotSyncedCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setPriority(priority)
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
                        //.setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        //.setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        mNotificationBuilder = mBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder.setChannelId(channelId);
        }

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
        String nbLocations = String.valueOf(db.getLocationNotSyncedCount());
        String nbSent = String.valueOf(db.getNbTotalSync());
        mNotificationBuilder.setContentText(String.format(getString(R.string.is_running), nbLocations, nbSent));
        mNotificationManager.notify(this.NOTIFICATION_ID, mNotificationBuilder.build());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId, boolean lowImportance) {
        int importance = NotificationManager.IMPORTANCE_LOW;
        if (lowImportance) {
            importance = NotificationManager.IMPORTANCE_MIN;
        }
        NotificationChannel chan = new NotificationChannel(channelId, getString(R.string.app_name), importance);
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
    private void sendBroadcast(String broadcast, long ljId) {
        Intent intent = new Intent(broadcast);
        intent.putExtra(SyncService.BROADCAST_EXTRA_PARAM, ljId);
        sendBroadcast(intent);
    }

    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if(level == -1 || scale == -1) {
                battery = 0.0;
            }
            double batLevel = ((double)level / (double)scale) * 100.0;
            battery = Math.round(batLevel * 100.0) / 100.0;
            if (SyncService.DEBUG) { Log.d(TAG, "[BATT changed " + battery + "]"); }
        }
    };

    private double getBatteryLevelOnce() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 0.0;
        }

        double batLevel = ((double)level / (double)scale) * 100.0;
        batLevel = Math.round(batLevel * 100.0) / 100.0;
        return batLevel;
    }

    /**
     * Location listener class
     */
    private class mLocationListener implements LocationListener {

        private DBLogjob logjob;
        private long logjobId;
        private long minTimeTolerance;
        private long maxTimeMillis;
        private long minTimeMillis;
        private boolean keepGpsOn;
        private boolean useSignificantMotion;

        public mLocationListener(DBLogjob logjob) {
            populateFromLogjob(logjob);
        }

        /**
         * Populate logging job and cache values
         * @param logjob The logging job
         */
        public void populateFromLogjob(DBLogjob logjob) {
            this.logjob = logjob;
            this.logjobId = logjob.getId();
            this.keepGpsOn = logjob.keepGpsOnBetweenFixes();
            this.useSignificantMotion = logjob.useSignificantMotion();
            // max time tolerance is half min time, but not more that 5 min
            this.minTimeMillis = logjob.getMinTime() * 1000;
            minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
            maxTimeMillis = minTimeMillis + minTimeTolerance;
        }

        @Override
        public void onLocationChanged(Location loc) {
            if (DEBUG) {
                Log.d(TAG, "[location changed: " + logjobId + "/" + logjob.getTitle() + " : bat : " + battery + ", " + loc + "]");
            }

            // always pass to the worker (sig motion or not)
            mLogjobWorkers.get(logjobId).handleLocationChange(loc);
        }

        /**
         * Should the location be logged or skipped
         * @param loc Location
         * @return True if skipped
         */
        private boolean skipLocation(DBLogjob logjob, Location loc) {
            // if we keep gps on, we take care of the timing between points
            if (keepGpsOn) {
                long elapsedMillisSinceLastUpdate;
                elapsedMillisSinceLastUpdate = (loc.getElapsedRealtimeNanos() / 1000000) - lastUpdateRealtime.get(logjobId);

                if (elapsedMillisSinceLastUpdate < minTimeMillis) {
                    if (DEBUG) { Log.d(TAG,"skip because "+elapsedMillisSinceLastUpdate + " < "+ minTimeMillis); }
                    return true;
                }
            }
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class ConnectionStateMonitor extends ConnectivityManager.NetworkCallback {

        final NetworkRequest networkRequest;

        public ConnectionStateMonitor() {
            networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        }

        public void enable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerNetworkCallback(networkRequest , this);
        }

        // Likewise, you can have a disable method that simply calls ConnectivityManager#unregisterCallback(networkRequest) too.

        public void disable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(this);
        }

        @Override
        public void onAvailable(Network network) {
            if (DEBUG) { Log.d(TAG, "Network is available again : launch sync from loggerservice"); }
            try {
                // just to be sure the connection is effective
                // sometimes i experienced problems when connecting to slow wifi networks
                // i think internet access was not yet established when syncService was launched
                TimeUnit.SECONDS.sleep(5);
            }
            catch (InterruptedException e) {

            }
            startService(syncIntent);
        }
    }

    private void acceptAndSyncLocation(long logjobId, Location loc) {
        lastLocations.put(logjobId, loc);
        lastUpdateRealtime.put(logjobId, loc.getElapsedRealtimeNanos() / 1000000);

        db.addLocation(logjobId, loc, battery);

        sendBroadcast(BROADCAST_LOCATION_UPDATED, logjobId);
        updateNotificationContent();

        Intent syncOneDev = new Intent(getApplicationContext(), WebTrackService.class);
        syncOneDev.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, logjobId);
        startService(syncOneDev);
    }

    // worker superclass
    private abstract class LogjobWorker extends TriggerEventListener {
        protected mLocationListener mLocationListener;
        protected DBLogjob mLogJob;
        protected long mJobId;
        protected Location lastLocation;

        protected Long mLastUpdateRealtime;

        protected Handler mIntervalHandler;
        protected Runnable mIntervalRunnable;
        protected Handler mTimeoutHandler;
        protected Runnable mTimeoutRunnable;
        protected Boolean mMotionDetected;
        protected Location mCachedNetworkResult;

        protected boolean mUseSignificantMotion;
        protected boolean mUseMixedMode;

        protected long mIntervalTimeMillis;
        protected boolean mUseInterval;
        protected int mLocationTimeout;
        protected long lastAcquisitionStartTimestamp;

        LogjobWorker(DBLogjob logjob, mLocationListener listener) {
            populate(logjob);
            mLocationListener = listener;
        }

        protected void populate(DBLogjob logjob) {
            mLogJob = logjob;
            mJobId = logjob.getId();
            lastLocation = null;

            mLastUpdateRealtime = Long.valueOf(0);
            lastAcquisitionStartTimestamp = System.currentTimeMillis()/1000;

            mIntervalHandler = null;
            mIntervalRunnable = null;
            mTimeoutHandler = null;
            mTimeoutRunnable = null;
            mCachedNetworkResult = null;

            mIntervalTimeMillis = mLogJob.getMinTime() * 1000;
            mUseInterval = mIntervalTimeMillis > 0;
            mUseSignificantMotion = logjob.useSignificantMotion();
            mUseMixedMode = logjob.useSignificantMotionMixed();
            mLocationTimeout = mLogJob.getLocationRequestTimeout();
        }

        protected boolean isMinDistanceOk(Location loc) {
            int minDistance = mLogJob.getMinDistance();
            if (minDistance == 0 || lastLocation == null) {
                return true;
            }
            else {
                double distance = SupportUtil.distance(
                        lastLocation.getLatitude(), loc.getLatitude(),
                        lastLocation.getLongitude(), loc.getLongitude(),
                        lastLocation.getAltitude(), loc.getAltitude()
                );
                Log.d(TAG, "Distance with last point: "+distance);
                Log.d(TAG, "Logjob minimum distance: "+minDistance);
                return (distance >= minDistance);
            }
        }

        protected boolean isMinAccuracyOk(Location loc) {
            int minAccuracy = mLogJob.getMinAccuracy();
            Log.d(TAG, "Accuracy of current point: "+loc.getAccuracy());
            return (loc.getAccuracy() <= minAccuracy);
        }

        public void updateLastAcquisitionStart() {
            // store time when position acquisition was launched
            lastAcquisitionStartTimestamp = System.currentTimeMillis()/1000;
        }

        protected void stop() {
            if (mIntervalHandler != null) {
                if (mIntervalRunnable != null) {
                    mIntervalHandler.removeCallbacks(mIntervalRunnable);
                    mIntervalRunnable = null;
                }
                mIntervalHandler = null;
            }
            if (mTimeoutHandler != null) {
                if (mTimeoutRunnable != null) {
                    mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                    mTimeoutRunnable = null;
                }
                mTimeoutHandler = null;
            }
        }

        public void startResultTimeout() {
            mCachedNetworkResult = null;

            if (mLocationTimeout > 0) {
                if (mTimeoutHandler == null)
                    mTimeoutHandler = new Handler();

                // Create and post
                mTimeoutRunnable = createSampleTimeoutDelayRunnable();
                Log.d(TAG, "Waiting " + mLocationTimeout + "s for timeout");
                mTimeoutHandler.postDelayed(mTimeoutRunnable, mLocationTimeout * 1000);
            }
        }

        protected abstract Runnable createSampleTimeoutDelayRunnable();

        public abstract void handleLocationChange(Location loc);
    }

    private class LogjobClassicWorker extends LogjobWorker {

        LogjobClassicWorker(DBLogjob logjob, mLocationListener listener) {
            super(logjob, listener);
        }

        protected Runnable createSampleTimeoutDelayRunnable() {

            Runnable runnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "Sampling timeout hit");

                    if (mCachedNetworkResult != null) {
                        // Cancel location request
                        if (useGps || usePassive) {
                            locManager.removeUpdates(mLocationListener);
                        }

                        Log.d(TAG, "Reached timeout before GPS or passive sample, using network sample");
                        lastLocation = mCachedNetworkResult;
                        acceptAndSyncLocation(mJobId, mCachedNetworkResult);

                        mCachedNetworkResult = null;
                    } else {
                        // Cancel location request
                        if (useGps || useNet || usePassive) {
                            locManager.removeUpdates(mLocationListener);
                        }
                    }

                    // Schedule sample for X seconds from last time a sample was asked
                    if (mUseInterval) {
                        long timeToWait = mIntervalTimeMillis - (mLocationTimeout * 1000);
                        Log.d(TAG, "Schedule next sample in "+(timeToWait / 1000)+"s");
                        scheduleSampleAfterInterval(timeToWait);
                    }
                }
            };
            return runnable;
        }

        private void scheduleSampleAfterInterval(long millisDelay) {
            Log.d(TAG, "Scheduling sampling delay for " + millisDelay/1000.0 + "s");
            // Create new handler if one doesn't exist
            if (mIntervalHandler == null) {
                mIntervalHandler = new Handler();
            }

            mIntervalRunnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "End of delay in NORMAL mode, recording point");

                    // Assists with ensuring we don't end up with two interval sequences running for one job
                    mIntervalRunnable = null;

                    requestLocationUpdates(mJobId, true, true);
                }
            };

            // Create and post
            mIntervalHandler.postDelayed(mIntervalRunnable, millisDelay);
        }

        public void handleLocationChange(Location loc) {
            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                    || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER)
                    || (!useGps && !usePassive)
            ) {
                // Got GPS result, accept
                Log.d(TAG, "Got position result, immediately accepting");

                // Remove any cached network result or disable update
                if (mCachedNetworkResult == null) {
                    if (useNet
                            && (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                            || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER))
                    ) {
                        locManager.removeUpdates(mLocationListener);
                    }
                } else {
                    mCachedNetworkResult = null;
                }

                // respect minimum distance/accuracy settings
                boolean minDistanceOk = isMinDistanceOk(loc);
                boolean minAccuracyOk = isMinAccuracyOk(loc);
                if (minDistanceOk && minAccuracyOk) {
                    // Accept, store and sync location
                    lastLocation = loc;
                    acceptAndSyncLocation(mJobId, loc);

                    mLastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;
                }
                else {
                    Log.d(TAG, "Not enough DISTANCE (min "+mLogJob.getMinDistance()+
                            ") or ACCURACY (min "+mLogJob.getMinAccuracy()+"), we skip this location");
                }

                // we stop the timeout only if there was no accuracy problem
                // if there was an accuracy problem, we will launch a position request again,
                // staying in same timeout
                if (minAccuracyOk) {
                    // Cancel timeout runnable
                    if (mTimeoutHandler != null) {
                        mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                        mTimeoutRunnable = null;
                    }
                }

                // If using an interval and NO accuracy problem, schedule sample for X seconds from last sample
                if (mUseInterval) {
                    if (minAccuracyOk) {
                        long timeToWaitSecond = mLogJob.getMinTime();

                        // how much time did it take to get current position?
                        long cTs = System.currentTimeMillis() / 1000;
                        long timeSpentSearching = cTs - lastAcquisitionStartTimestamp;
                        timeToWaitSecond = mLogJob.getMinTime() - timeSpentSearching;
                        if (timeToWaitSecond < 0) {
                            timeToWaitSecond = 0;
                        }
                        Log.d(TAG, "As we spent " + timeSpentSearching + "s to search position, " +
                                "with interval=" + mLogJob.getMinTime() + ", " +
                                "we now wait " + timeToWaitSecond + "s before getting a new one");

                        scheduleSampleAfterInterval(timeToWaitSecond * 1000);
                    }
                    // if there was an accuracy problem, just request position again, staying in same timeout
                    else {
                        Log.d(TAG, "ACCURACY is not good enough, launch location REQUEST again, staying in same timeout");
                        // except this request does not start a timeout
                        requestLocationUpdates(mJobId, false, false);
                    }
                }
            } else {
                Log.d(TAG, "Network location returned first, caching");
                // Cache lower quality network result
                mCachedNetworkResult = loc;
            }
        }

        // this is triggered only when significant motion mode is enabled
        @Override
        public void onTrigger(TriggerEvent event) {
        }
    }

    private class LogjobClassicGpsOnWorker extends LogjobWorker {

        LogjobClassicGpsOnWorker(DBLogjob logjob, mLocationListener listener) {
            super(logjob, listener);
        }

        protected Runnable createSampleTimeoutDelayRunnable() {
            return null;
        }

        private void scheduleSampleAfterInterval(long millisDelay, boolean firstRequestAfterAccepted) {
            Log.d(TAG, "Scheduling sampling delay for " + millisDelay/1000.0 + "s");
            // Create new handler if one doesn't exist
            if (mIntervalHandler == null) {
                mIntervalHandler = new Handler();
            }

            mIntervalRunnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "End of delay in NORMAL mode, recording point");

                    // Assists with ensuring we don't end up with two interval sequences running for one job
                    mIntervalRunnable = null;

                    requestLocationUpdates(mJobId, false, firstRequestAfterAccepted);
                }
            };

            // Create and post
            mIntervalHandler.postDelayed(mIntervalRunnable, millisDelay);
        }

        public void handleLocationChange(Location loc) {
            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                    || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER)
                    || (!useGps && !usePassive)
            ) {
                // Got GPS result, accept
                Log.d(TAG, "Got position result, immediately accepting if constraints are respected");

                // Remove any cached network result or disable update
                if (mCachedNetworkResult == null) {
                    if (useNet
                            && (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                            || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER))
                    ) {
                        locManager.removeUpdates(mLocationListener);
                    }
                } else {
                    mCachedNetworkResult = null;
                }

                // respect minimum distance/accuracy/time settings
                boolean minDistanceOk = isMinDistanceOk(loc);
                boolean minAccuracyOk = isMinAccuracyOk(loc);
                boolean minTimeOk = isMinTimeOk(loc);
                long timeSinceLastAccepted = (loc.getElapsedRealtimeNanos() / 1000000000) - (mLastUpdateRealtime / 1000);
                boolean positionAccepted;
                if (minDistanceOk && minAccuracyOk && minTimeOk) {
                    // Accept, store and sync location
                    lastLocation = loc;
                    acceptAndSyncLocation(mJobId, loc);
                    positionAccepted = true;

                    mLastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;

                    // how much time did it take to get current position?
                    long cTs = System.currentTimeMillis() / 1000;
                    long timeSpentSearching = cTs - lastAcquisitionStartTimestamp;
                }
                else {
                    positionAccepted = false;
                    Log.d(TAG, "Not enough DISTANCE (min "+mLogJob.getMinDistance()+
                            ") or ACCURACY (min "+mLogJob.getMinAccuracy()+
                            ") or TIME ("+timeSinceLastAccepted+"/"+mLogJob.getMinTime()+"), we skip this location");
                }

                // we always want a new position
                scheduleSampleAfterInterval(1000, positionAccepted);
            } else {
                Log.d(TAG, "Network location returned first, caching");
                // Cache lower quality network result
                mCachedNetworkResult = loc;
            }
        }

        protected boolean isMinTimeOk(Location loc) {
            long timeSinceLastAccepted = (loc.getElapsedRealtimeNanos() / 1000000000) - (mLastUpdateRealtime / 1000);
            int minTime = mLogJob.getMinTime();
            Log.d(TAG, "is "+timeSinceLastAccepted+" >= "+minTime+" ?");
            return (timeSinceLastAccepted >= minTime);
        }

        // this is triggered only when significant motion mode is enabled
        @Override
        public void onTrigger(TriggerEvent event) {
        }
    }

    // this worker can be used for significant motion ones (with or without hybrid mode)
    private class LogjobSignificantMotionWorker extends LogjobWorker {
        private SensorManager mSensorManager;
        private Sensor mSensor;

        LogjobSignificantMotionWorker(DBLogjob logjob, mLocationListener listener) {
            super(logjob, listener);

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        }

        protected Runnable createSampleTimeoutDelayRunnable() {

            Runnable runnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "Sampling timeout hit");

                    if (mCachedNetworkResult != null) {
                        // Cancel location request
                        if (useGps || usePassive) {
                            locManager.removeUpdates(mLocationListener);
                        }

                        Log.d(TAG, "Reached timeout before GPS or passive sample, using network sample");
                        lastLocation = mCachedNetworkResult;
                        acceptAndSyncLocation(mJobId, mCachedNetworkResult);

                        mCachedNetworkResult = null;
                    } else {
                        // Cancel location request
                        if (useGps || useNet || usePassive) {
                            locManager.removeUpdates(mLocationListener);
                        }
                    }

                    // Clear significant motion flag for next interval
                    mMotionDetected = false;
                    // Request significant motion notification
                    mSensorManager.requestTriggerSensor(LogjobSignificantMotionWorker.this, mSensor);

                    // Schedule sample for X seconds from last time a sample was asked
                    if (mUseInterval) {
                        long timeToWait = mIntervalTimeMillis - (mLocationTimeout * 1000);
                        Log.d(TAG, "Schedule next sample in " + (timeToWait / 1000) + "s");
                        scheduleSampleAfterInterval(timeToWait);
                    }
                }
            };
            return runnable;
        }

        private void scheduleSampleAfterInterval(long millisDelay) {
            Log.d(TAG, "Scheduling sampling delay for " + millisDelay / 1000.0 + "s");
            // Create new handler if one doesn't exist
            if (mIntervalHandler == null) {
                mIntervalHandler = new Handler();
            }

            mIntervalRunnable = new Runnable() {
                public void run() {
                    if (mMotionDetected || mUseMixedMode) {
                        if (mUseMixedMode) {
                            Log.d(TAG, "End of delay in SIGMOTION MIXED mode, recording point regardless of motion");
                        } else {
                            Log.d(TAG, "End of delay in SIGMOTION normal mode, significant motion detected during delay, recording point");
                        }

                        // Assists with ensuring we don't end up with two interval sequences running for one job
                        mIntervalRunnable = null;

                        requestLocationUpdates(mJobId, true, true);
                    } else {
                        Log.d(TAG, "No significant motion, not recording point");

                        scheduleSampleAfterInterval(mIntervalTimeMillis);
                    }
                }
            };

            // Ensure significant motion notifications are enabled
            mSensorManager.requestTriggerSensor(LogjobSignificantMotionWorker.this, mSensor);

            // Create and post
            mIntervalHandler.postDelayed(mIntervalRunnable, millisDelay);
        }

        public void handleLocationChange(Location loc) {
            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                    || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER)
                    || (!useGps && !usePassive)
            ) {
                // Got GPS result, accept
                Log.d(TAG, "Got position result, immediately accepting");

                // Remove any cached network result or disable update
                if (mCachedNetworkResult == null) {
                    if (useNet
                            && (loc.getProvider().equals(LocationManager.GPS_PROVIDER)
                                || loc.getProvider().equals(LocationManager.PASSIVE_PROVIDER))
                    ) {
                        locManager.removeUpdates(mLocationListener);
                    }
                } else {
                    mCachedNetworkResult = null;
                }

                // respect minimum distance/accuracy settings
                boolean minDistanceOk = isMinDistanceOk(loc);
                boolean minAccuracyOk = isMinAccuracyOk(loc);
                if (minDistanceOk && minAccuracyOk) {
                    // Accept, store and sync location
                    lastLocation = loc;
                    acceptAndSyncLocation(mJobId, loc);

                    mLastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;
                }
                else {
                    Log.d(TAG, "Not enough DISTANCE (min "+mLogJob.getMinDistance()+
                            ") or ACCURACY (min "+mLogJob.getMinAccuracy()+"), we skip this location");
                }

                // we stop the timeout only if there was no accuracy problem
                // if there was an accuracy problem, we will launch a position request again,
                // staying in same timeout
                if (minAccuracyOk) {
                    // Cancel timeout runnable
                    if (mTimeoutHandler != null) {
                        mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                        mTimeoutRunnable = null;
                    }
                }

                    // Clear significant motion flag for next interval
                    mMotionDetected = false;

                    // Request significant motion notification
                    mSensorManager.requestTriggerSensor(LogjobSignificantMotionWorker.this, mSensor);

                // If using an interval and NO accuracy problem, schedule sample for X seconds from last sample
                if (mUseInterval) {
                    if (minAccuracyOk) {
                        long timeToWaitSecond = mLogJob.getMinTime();
                        scheduleSampleAfterInterval(timeToWaitSecond * 1000);
                    }
                    // if there was an accuracy problem, just request position again, staying in same timeout
                    else {
                        Log.d(TAG, "ACCURACY is not good enough, launch location REQUEST again, staying in same timeout");
                        // except this request does not start a timeout
                        requestLocationUpdates(mJobId, false, false);
                    }
                }
            } else {
                Log.d(TAG, "Network location returned first, caching");
                // Cache lower quality network result
                mCachedNetworkResult = loc;
            }
        }

        // this is triggered only when significant motion mode is enabled
        @Override
        public void onTrigger(TriggerEvent event) {
            Log.d(TAG, "Significant motion seen");

            // Flag motion in interval
            mMotionDetected = true;

            // If the job doesn't have a minimum interval, or hasn't taken a sample for longer than its interval,
            // sample immediately
            long millisSinceLast = SystemClock.elapsedRealtime() - mLastUpdateRealtime;
            // without mixed mode
            if (!mUseInterval || !mUseMixedMode) {
                if (!mUseInterval || millisSinceLast > mIntervalTimeMillis) {
                    // If not using an interval, definitely request updates
                    boolean requestUpdates = !mUseInterval;

                    // If we're interval-based and there is a runnable waiting for the next interval we know we haven't
                    // already requested a location. This checks helps us prevent having two sampling sequences running
                    // for the same job.
                    if (mUseInterval && mIntervalRunnable != null) {
                        // Stop waiting runnable
                        mIntervalHandler.removeCallbacks(mIntervalRunnable);
                        mIntervalRunnable = null;

                        Log.d(TAG, "Triggering immediate sample after significant motion due to " +
                                millisSinceLast / 1000.0 + "s since last point");

                        requestUpdates = true;
                    }

                    if (requestUpdates) {
                        requestLocationUpdates(mJobId, true, true);
                    }
                }
            }
            // with mixed mode
            // we take the position anyway, then runnable has to be killed, it will be launched again
            // when handling the position result
            else {
                Log.d(TAG, "Triggering immediate sample after significant motion because we're in MIXED mode");

                // If we're interval-based and there is a runnable waiting for the next interval we know we haven't
                // already requested a location. This checks helps us prevent having two sampling sequences running
                // for the same job.
                if (mIntervalRunnable != null) {
                    Log.d(TAG, "stop runnable because MIXED mode");
                    // Stop waiting runnable
                    mIntervalHandler.removeCallbacks(mIntervalRunnable);
                    mIntervalRunnable = null;
                }

                requestLocationUpdates(mJobId, true, true);
            }

            // Request notification for next significant motion
            mSensorManager.requestTriggerSensor(LogjobSignificantMotionWorker.this, mSensor);
        }
    }
}
