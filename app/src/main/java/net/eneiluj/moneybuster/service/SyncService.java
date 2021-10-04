package net.eneiluj.moneybuster.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.activity.BillsListViewActivity;
import net.eneiluj.moneybuster.android.fragment.PreferencesFragment;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.util.SupportUtil;


public class SyncService extends Service {

    private static final String TAG = SyncService.class.getSimpleName();

    private static volatile boolean isRunning = false;
    private static volatile boolean firstRun = false;
    private SyncServiceThread thread;
    private Looper looper;
    private MoneyBusterSQLiteOpenHelper db;
    private long intervalMinutes;

    private final int NOTIFICATION_ID = 1526756648;
    public static final int MAIN_CHANNEL_ID = 1234567890;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    public static boolean DEBUG = true;

    private SyncWorker mSyncWorker;

    private ConnectionStateMonitor connectionMonitor;
    private BroadcastReceiver powerSaverChangeReceiver;
    private BroadcastReceiver airplaneModeChangeReceiver;

    private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm MM-dd");

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "[onCreate]");
        firstRun = true;

        connectionMonitor = null;
        powerSaverChangeReceiver = null;
        airplaneModeChangeReceiver = null;

        db = MoneyBusterSQLiteOpenHelper.getInstance(getApplicationContext());

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String intervalStr = prefs.getString(getString(R.string.pref_key_sync_interval), "15");
        intervalMinutes = 15;
        try {
            intervalMinutes = Long.valueOf(intervalStr);
        }
        catch (Exception e) {

        }

        mSyncWorker = new SyncWorker(intervalMinutes * 60);

        // NOTIFICATIONS
        createNotificationChannels();

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
        Log.d(TAG, "[onstartCommand]");
        if (isRunning) {
            Log.d(TAG, "[onstartCommand isrunning yes]");
            final boolean stopService = (intent != null) && intent.getBooleanExtra(PreferencesFragment.STOP_SYNC_SERVICE, false);
            final long newInterval = (intent != null) ? intent.getLongExtra(PreferencesFragment.CHANGE_SYNC_INTERVAL, 0) : 0;
            if (stopService) {
                Log.d(TAG, "[stop sync service]");
                stopSelf();
            } else if (newInterval != 0) {
                intervalMinutes = newInterval;
                requestSync();
                updateNotificationContent();
                mSyncWorker.stop();
                mSyncWorker.setInterval(intervalMinutes * 60);
                mSyncWorker.startSyncLoop();
            } else {
                if (firstRun) {
                    Log.d(TAG, "[start sync service => loop]");
                    mSyncWorker.startSyncLoop();
                    // anyway, first run is over
                    firstRun = false;
                }
            }
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

    public void requestSync() {
        Log.v(TAG, "request sync of all projects");
        List<DBProject> projs = db.getProjects();
        for (DBProject proj: projs) {
            Log.v(TAG, "request sync of project "+proj.getRemoteId());
            if (!proj.isLocal()) {
                db.getMoneyBusterServerSyncHelper().scheduleSync(false, proj.getId());
            }
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // main channel
            SupportUtil.createNotificationChannel(MAIN_CHANNEL_ID, getString(R.string.permanent_notification_title), true, getApplicationContext());
            // one channel per project
            List<DBProject> projs = db.getProjects();
            long channelId;
            for (DBProject p: projs) {
                if (!p.getType().equals(ProjectType.LOCAL)) {
                    channelId = MAIN_CHANNEL_ID + p.getId();
                    //Log.e("LLL", "channel ID : "+channelId+" name "+p.getRemoteId());
                    SupportUtil.createNotificationChannel(channelId, p.getRemoteId(), false, getApplicationContext());
                }
            }
        }
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
        int priority = NotificationCompat.PRIORITY_MIN;

        final String channelId = String.valueOf(MAIN_CHANNEL_ID);
        String lastSyncDate = "∞";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_dollar_grey_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setPriority(priority)
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(
                                getString(R.string.is_running),
                                intervalMinutes,
                                lastSyncDate
                        ));
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
        String lastSyncDate = sdf.format(new Date());
        mNotificationBuilder.setContentText(String.format(
                getString(R.string.is_running),
                intervalMinutes,
                lastSyncDate
        ));
        mNotificationManager.notify(this.NOTIFICATION_ID, mNotificationBuilder.build());
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
            requestSync();
        }
    }

    // worker superclass
    private class SyncWorker {

        protected Long mLastSyncSystemTime;

        protected Handler mIntervalHandler;
        protected Runnable mIntervalRunnable;

        protected long mIntervalTimeSecs;

        SyncWorker(long intervalSec) {
            mIntervalTimeSecs = intervalSec;
            mLastSyncSystemTime = Long.valueOf(0);
            //lastAcquisitionStartTimestamp = System.currentTimeMillis()/1000;

            mIntervalHandler = null;
            mIntervalRunnable = null;
        }

        public void setInterval(long intervalSec) {
            mIntervalTimeSecs = intervalSec;
        }

        public void startSyncLoop() {
            scheduleSyncAfterInterval(mIntervalTimeSecs * 1000);
        }

        private void scheduleSyncAfterInterval(long millisDelay) {
            Log.d(TAG, "Scheduling sync in " + millisDelay/1000.0 + "s");
            // Create new handler if one doesn't exist
            if (mIntervalHandler == null) {
                mIntervalHandler = new Handler();
            }

            mIntervalRunnable = new Runnable() {
                public void run() {
                    Log.d(TAG, "End of delay => sync");

                    // Assists with ensuring we don't end up with two interval sequences running for one job
                    mIntervalRunnable = null;

                    requestSync();
                    updateNotificationContent();
                    scheduleSyncAfterInterval(mIntervalTimeSecs * 1000);
                }
            };

            // Create and post
            mIntervalHandler.postDelayed(mIntervalRunnable, millisDelay);
        }

        protected void stop() {
            if (mIntervalHandler != null) {
                if (mIntervalRunnable != null) {
                    mIntervalHandler.removeCallbacks(mIntervalRunnable);
                    mIntervalRunnable = null;
                }
                mIntervalHandler = null;
            }
        }
    }
}
