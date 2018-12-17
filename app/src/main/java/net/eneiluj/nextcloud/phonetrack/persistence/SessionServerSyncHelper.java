package net.eneiluj.nextcloud.phonetrack.persistence;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.cert4android.CustomCertService;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.CloudSession;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClient;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClientUtil.LoginStatus;
import net.eneiluj.nextcloud.phonetrack.util.ServerResponse;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

/**
 * Helps to synchronize the Database to the Server.
 */
public class SessionServerSyncHelper {

    public static final String BROADCAST_SESSIONS_SYNC_FAILED = "net.eneiluj.nextcloud.phonetrack.broadcast.sessions_sync_failed";
    public static final String BROADCAST_SESSIONS_SYNCED = "net.eneiluj.nextcloud.phonetrack.broadcast.sessions_synced";

    private static SessionServerSyncHelper instance;

    /**
     * Get (or create) instance from SessionServerSyncHelper.
     * This has to be a singleton in order to realize correct registering and unregistering of
     * the BroadcastReceiver, which listens on changes of network connectivity.
     *
     * @param dbHelper PhoneTrackSQLiteOpenHelper
     * @return SessionServerSyncHelper
     */
    public static synchronized SessionServerSyncHelper getInstance(PhoneTrackSQLiteOpenHelper dbHelper) {
        if (instance == null) {
            instance = new SessionServerSyncHelper(dbHelper);
        }
        return instance;
    }

    private final PhoneTrackSQLiteOpenHelper dbHelper;
    private final Context appContext;

    private CustomCertManager customCertManager;

    // Track network connection changes using a BroadcastReceiver
    private boolean networkConnected = false;
    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNetworkStatus();
            if (isSyncPossible()) {
                scheduleSync(false);
            }
        }
    };

    private boolean cert4androidReady = false;
    private final ServiceConnection certService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            cert4androidReady = true;
            if (isSyncPossible()) {
                scheduleSync(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            cert4androidReady = false;
        }
    };

    // current state of the synchronization
    private boolean syncActive = false;
    private boolean syncScheduled = false;

    // list of callbacks for both parts of synchronziation
    private List<ICallback> callbacksPush = new ArrayList<>();
    private List<ICallback> callbacksPull = new ArrayList<>();


    private SessionServerSyncHelper(PhoneTrackSQLiteOpenHelper db) {
        this.dbHelper = db;
        this.appContext = db.getContext().getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                customCertManager = SupportUtil.getCertManager(appContext);
            }
        }.start();

        // Registers BroadcastReceiver to track network connection changes.
        appContext.registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateNetworkStatus();
        // bind to certifciate service to block sync attempts if service is not ready
        appContext.bindService(new Intent(appContext, CustomCertService.class), certService, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void finalize() throws Throwable {
        appContext.unregisterReceiver(networkReceiver);
        appContext.unbindService(certService);
        if (customCertManager != null) {
            customCertManager.close();
        }
        super.finalize();
    }

    public static boolean isConfigured(Context context) {
        return !PreferenceManager.getDefaultSharedPreferences(context).getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).isEmpty();
    }

    /**
     * Synchronization is only possible, if there is an active network connection and
     * Cert4Android service is available.
     * SessionServerSyncHelper observes changes in the network connection.
     * The current state can be retrieved with this method.
     *
     * @return true if sync is possible, otherwise false.
     */
    public boolean isSyncPossible() {
        return networkConnected && isConfigured(appContext) && cert4androidReady;
    }

    public CustomCertManager getCustomCertManager() {
        return customCertManager;
    }

    /**
     * Adds a callback method to the SessionServerSyncHelper for the synchronization part push local changes to the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPush(ICallback callback) {
        callbacksPush.add(callback);
    }

    /**
     * Adds a callback method to the SessionServerSyncHelper for the synchronization part pull remote changes from the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPull(ICallback callback) {
        callbacksPull.add(callback);
    }


    /**
     * Schedules a synchronization and start it directly, if the network is connected and no
     * synchronization is currently running.
     *
     * @param onlyLocalChanges Whether to only push local changes to the server or to also load the whole list of sessions from the server.
     */
    public void scheduleSync(boolean onlyLocalChanges) {
        Log.d(getClass().getSimpleName(), "Sync requested (" + (onlyLocalChanges ? "onlyLocalChanges" : "full") + "; " + (syncActive ? "sync active" : "sync NOT active") + ") ...");
        Log.d(getClass().getSimpleName(), "(network:" + networkConnected + "; conf:" + isConfigured(appContext) + "; cert4android:" + cert4androidReady + ")");
        if (isSyncPossible() && (!syncActive || onlyLocalChanges)) {
            Log.d(getClass().getSimpleName(), "... starting now");
            SyncTask syncTask = new SyncTask(onlyLocalChanges);
            syncTask.addCallbacks(callbacksPush);
            callbacksPush = new ArrayList<>();
            if (!onlyLocalChanges) {
                syncTask.addCallbacks(callbacksPull);
                callbacksPull = new ArrayList<>();
            }
            syncTask.execute();
        } else if (!onlyLocalChanges) {
            Log.d(getClass().getSimpleName(), "... scheduled");
            syncScheduled = true;
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        } else {
            Log.d(getClass().getSimpleName(), "... do nothing");
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        }
    }

    private void updateNetworkStatus() {
        ConnectivityManager connMgr = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            Log.d(SessionServerSyncHelper.class.getSimpleName(), "Network connection established.");
            networkConnected = true;
        } else {
            networkConnected = false;
            Log.d(SessionServerSyncHelper.class.getSimpleName(), "No network connection.");
        }
    }

    /**
     * SyncTask is an AsyncTask which performs the synchronization in a background thread.
     * Synchronization consists of two parts: pushLocalChanges and pullRemoteChanges.
     */
    private class SyncTask extends AsyncTask<Void, Void, LoginStatus> {
        private final boolean onlyLocalChanges;
        private final List<ICallback> callbacks = new ArrayList<>();
        private PhoneTrackClient client;
        private List<Throwable> exceptions = new ArrayList<>();

        public SyncTask(boolean onlyLocalChanges) {
            this.onlyLocalChanges = onlyLocalChanges;
        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!onlyLocalChanges && syncScheduled) {
                syncScheduled = false;
            }
            syncActive = true;
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient(); // recreate PhoneTrackClients on every sync in case the connection settings was changed
            Log.i(getClass().getSimpleName(), "STARTING SYNCHRONIZATION");
            //dbHelper.debugPrintFullDB();
            LoginStatus status = LoginStatus.OK;
            // TODO avoid doing getsessions everytime
            //pushLocalChanges();
            //if (!onlyLocalChanges) {
                status = pullRemoteChanges();
            //}
            //dbHelper.debugPrintFullDB();
            Log.i(getClass().getSimpleName(), "SYNCHRONIZATION FINISHED");
            return status;
        }

        /**
         * Pull remote Changes: update or create each remote session and remove remotely deleted sessions.
         */
        private LoginStatus pullRemoteChanges() {
            // TODO add/remove sessions
            Log.d(getClass().getSimpleName(), "pullRemoteChanges()");
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            String lastETag = preferences.getString(SettingsActivity.SETTINGS_KEY_ETAG, null);
            long lastModified = preferences.getLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, 0);
            LoginStatus status;
            try {
                Map<String, Long> locIdMap = dbHelper.getTokenMap();
                ServerResponse.SessionsResponse response = client.getSessions(customCertManager, lastModified, lastETag);
                List<CloudSession> remoteSessions = response.getSessions(dbHelper);
                Set<String> remoteTokens = new HashSet<>();
                // pull remote changes: update or create each remote logjob
                for (CloudSession remoteSession : remoteSessions) {
                    Log.v(getClass().getSimpleName(), "   Process Remote Session: " + remoteSession);
                    remoteTokens.add(remoteSession.getToken());
                    if (locIdMap.containsKey(remoteSession.getToken())) {
                        Log.v(getClass().getSimpleName(), "   ... found -> Update");
                        dbHelper.updateSession(locIdMap.get(remoteSession.getToken()), remoteSession);
                    } else {
                        Log.v(getClass().getSimpleName(), "   ... create");
                        dbHelper.addSession(remoteSession);
                    }
                }
                Log.d(getClass().getSimpleName(), "   Remove remotely deleted Sessions");
                // remove remotely deleted sessions
                for (Map.Entry<String, Long> locEntry : locIdMap.entrySet()) {
                    if (!remoteTokens.contains(locEntry.getKey())) {
                        Log.v(getClass().getSimpleName(), "   ... remove " + locEntry.getValue());
                        dbHelper.deleteSession(locEntry.getValue());
                    }
                }
                status = LoginStatus.OK;

                // update ETag and Last-Modified in order to reduce size of next response
                SharedPreferences.Editor editor = preferences.edit();
                String etag = response.getETag();
                if (etag != null && !etag.isEmpty()) {
                    editor.putString(SettingsActivity.SETTINGS_KEY_ETAG, etag);
                } else {
                    editor.remove(SettingsActivity.SETTINGS_KEY_ETAG);
                }
                long modified = response.getLastModified();
                if (modified != 0) {
                    editor.putLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, modified);
                } else {
                    editor.remove(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED);
                }
                editor.apply();
            } catch (ServerResponse.NotModifiedException e) {
                Log.d(getClass().getSimpleName(), "No changes, nothing to do.");
                status = LoginStatus.OK;
            } catch (IOException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            if (status != LoginStatus.OK) {
                String errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
                // broadcast the error
                // if the log job list is not visible, no toast
                Intent intent = new Intent(BROADCAST_SESSIONS_SYNC_FAILED);
                intent.putExtra(LoggerService.BROADCAST_ERROR_MESSAGE, errorString);
                appContext.sendBroadcast(intent);
            }
            else {
                Intent intent = new Intent(BROADCAST_SESSIONS_SYNCED);
                appContext.sendBroadcast(intent);
            }
            syncActive = false;
            // notify callbacks
            for (ICallback callback : callbacks) {
                callback.onFinish();
            }
            dbHelper.notifySessionsChanged();
            // start next sync if scheduled meanwhile
            if (syncScheduled) {
                scheduleSync(false);
            }
        }
    }

    private PhoneTrackClient createPhoneTrackClient() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
        String username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
        String password = preferences.getString(SettingsActivity.SETTINGS_PASSWORD, SettingsActivity.DEFAULT_SETTINGS);
        return new PhoneTrackClient(url, username, password);
    }

    public boolean shareDevice(String token, String deviceName, ICallback callback) {
        if (isSyncPossible()) {
            ShareDeviceTask shareDeviceTask = new ShareDeviceTask(token, deviceName, callback);
            shareDeviceTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     *
     */
    private class ShareDeviceTask extends AsyncTask<Void, Void, LoginStatus> {
        private PhoneTrackClient client;
        private String token;
        private String deviceName;
        private String publicUrl = null;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public ShareDeviceTask(String token, String deviceName, ICallback callback) {
            this.token = token;
            this.deviceName = deviceName;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (LoggerService.DEBUG) { Log.i(getClass().getSimpleName(), "STARTING share device"); }
            LoginStatus status = LoginStatus.OK;
            String sharetoken;
            try {
                ServerResponse.ShareDeviceResponse response = client.shareDevice(customCertManager, token, deviceName);
                sharetoken = response.getPublicToken();
                if (LoggerService.DEBUG) {
                    Log.i(getClass().getSimpleName(), "HERE IS THE TOKEN BIIIITCH "+sharetoken);
                }
                publicUrl = prefs.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS)
                        .replaceAll("/+$", "")
                        + "/index.php/apps/phonetrack/publicSessionWatch/" + sharetoken;


            } catch (IOException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            }
            if (LoggerService.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED share device");
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            String errorString = "";
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            }
            callback.onFinish(publicUrl, errorString);
        }
    }
}
