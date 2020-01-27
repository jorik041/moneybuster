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

import net.eneiluj.moneybuster.android.activity.BillsListViewActivity;
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
     * Start main thread, start synchronization.
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return Always returns START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            mSyncWorker.startSyncLoop();
            // anyway, first run is over
            firstRun = false;
        }

        return START_STICKY;
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {
        if (DEBUG) { Log.d(TAG, "[onDestroy]"); }

        mSyncWorker.stop();

        isRunning = false;

        mNotificationManager.cancel(NOTIFICATION_ID);


        if (thread != null) {
            thread.interrupt();
            //sendBroadcast(BROADCAST_LOCATION_STOPPED);
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
     * Main service thread class handling sync requests.
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
        // TODO get last sync date and set notification icon
        String lastSyncDate = "plop";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setPriority(priority)
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(getString(R.string.is_running), lastSyncDate));
                        //.setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        //.setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        mNotificationBuilder = mBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder.setChannelId(channelId);
        }

        Intent resultIntent = new Intent(this, BillsListViewActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(BillsListViewActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        mNotificationManager.notify(mId, mNotification);
        return mNotification;
    }

    private void updateNotificationContent() {
        String lastSyncDate = "plop";
        mNotificationBuilder.setContentText(String.format(getString(R.string.is_running), lastSyncDate));
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
            //startService(syncIntent);
            // TODO trigger the sync here
        }
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
