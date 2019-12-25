package net.eneiluj.moneybuster.persistence;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.activity.BillsListViewActivity;
import net.eneiluj.moneybuster.android.activity.SettingsActivity;
import net.eneiluj.moneybuster.model.DBAccountProject;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.util.CospendClient;
import net.eneiluj.moneybuster.util.CospendClientUtil.LoginStatus;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.VersatileProjectSyncClient;
import net.eneiluj.moneybuster.util.ServerResponse;
import net.eneiluj.moneybuster.util.SupportUtil;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.cert4android.CustomCertService;
import at.bitfire.cert4android.ICustomCertService;
import at.bitfire.cert4android.IOnCertificateDecision;


/**
 * Helps to synchronize the Database to the Server.
 */
public class MoneyBusterServerSyncHelper {

    private static final String TAG = MoneyBusterServerSyncHelper.class.getSimpleName();

    public static final String BROADCAST_PROJECT_SYNC_FAILED = "net.eneiluj.moneybuster.broadcast.project_sync_failed";
    public static final String BROADCAST_PROJECT_SYNCED = "net.eneiluj.moneybuster.broadcast.project_synced";
    public static final String BROADCAST_SYNC_PROJECT = "net.eneiluj.moneybuster.broadcast.sync_project";
    public static final String BROADCAST_NETWORK_AVAILABLE = "net.eneiluj.moneybuster.broadcast.network_available";
    public static final String BROADCAST_NETWORK_UNAVAILABLE = "net.eneiluj.moneybuster.broadcast.network_unavailable";

    private static MoneyBusterServerSyncHelper instance;

    /**
     * Get (or create) instance from MoneyBusterServerSyncHelper.
     * This has to be a singleton in order to realize correct registering and unregistering of
     * the BroadcastReceiver, which listens on changes of network connectivity.
     *
     * @param dbHelper MoneyBusterSQLiteOpenHelper
     * @return MoneyBusterServerSyncHelper
     */
    public static synchronized MoneyBusterServerSyncHelper getInstance(MoneyBusterSQLiteOpenHelper dbHelper) {
        if (instance == null) {
            instance = new MoneyBusterServerSyncHelper(dbHelper);
        }
        return instance;
    }

    private final MoneyBusterSQLiteOpenHelper dbHelper;
    private final Context appContext;

    private CustomCertManager customCertManager;
    private ICustomCertService iCustomCertService;

    private boolean networkConnected = false;

    private boolean cert4androidReady = false;
    private final ServiceConnection certService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            iCustomCertService = ICustomCertService.Stub.asInterface(iBinder);
            cert4androidReady = true;
            if (isSyncPossible()) {
                Long lastId = PreferenceManager.getDefaultSharedPreferences(dbHelper.getContext()).getLong("selected_project", 0);
                if (lastId != 0) {
                    DBProject proj = dbHelper.getProject(lastId);
                    if (proj != null) {
                        Intent intent = new Intent(BROADCAST_SYNC_PROJECT);
                        appContext.sendBroadcast(intent);
                        Intent intent2 = new Intent(BROADCAST_NETWORK_AVAILABLE);
                        appContext.sendBroadcast(intent2);
                        //scheduleSync(false, lastId);
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            cert4androidReady = false;
            iCustomCertService = null;
        }
    };

    // current state of the synchronization
    private boolean syncActive = false;
    private boolean syncScheduled = false;

    // current state of the account projects synchronization
    private boolean syncAccountProjectsActive = false;

    // list of callbacks for both parts of synchronziation
    private List<ICallback> callbacksPush = new ArrayList<>();
    private List<ICallback> callbacksPull = new ArrayList<>();

    private ConnectionStateMonitor connectionMonitor;

    private MoneyBusterServerSyncHelper(MoneyBusterSQLiteOpenHelper db) {
        this.dbHelper = db;
        this.appContext = db.getContext().getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                customCertManager = SupportUtil.getCertManager(appContext);
            }
        }.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // track network connectivity changes
            connectionMonitor = new ConnectionStateMonitor();
            connectionMonitor.enable(appContext);
        }
        updateNetworkStatus();
        // bind to certificate service to block sync attempts if service is not ready
        appContext.bindService(new Intent(appContext, CustomCertService.class), certService, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void finalize() throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionMonitor.disable(appContext);
        }
        appContext.unbindService(certService);
        if (customCertManager != null) {
            customCertManager.close();
        }
        super.finalize();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class ConnectionStateMonitor extends ConnectivityManager.NetworkCallback {

        final NetworkRequest networkRequest;

        public ConnectionStateMonitor() {
            networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        }

        public void enable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerNetworkCallback(networkRequest, this);
        }

        // Likewise, you can have a disable method that simply calls ConnectivityManager#unregisterCallback(networkRequest) too.

        public void disable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(this);
        }

        @Override
        public void onAvailable(Network network) {
            if (BillsListViewActivity.DEBUG) {
                Log.d(TAG, "NETWORK AVAILABLE in synchelper !!!!");
            }
            updateNetworkStatus();
            if (isSyncPossible()) {
                long lastId = PreferenceManager.getDefaultSharedPreferences(appContext).getLong("selected_project", 0);
                DBProject proj = dbHelper.getProject(lastId);
                if (lastId != 0 && proj != null && !proj.isLocal()) {
                    scheduleSync(false, lastId);
                }
                Intent intent2 = new Intent(BROADCAST_NETWORK_AVAILABLE);
                appContext.sendBroadcast(intent2);
            }
        }

        @Override
        public void onLost(Network network) {
            if (!isSyncPossible()) {
                Intent intent2 = new Intent(BROADCAST_NETWORK_UNAVAILABLE);
                appContext.sendBroadcast(intent2);
            }
        }
    }

    public static boolean isNextcloudAccountConfigured(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return !preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).isEmpty() ||
                preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
    }

    /**
     * Synchronization is only possible, if there is an active network connection and
     * Cert4Android service is available.
     * MoneyBusterServerSyncHelper observes changes in the network connection.
     * The current state can be retrieved with this method.
     *
     * @return true if sync is possible, otherwise false.
     */
    public boolean isSyncPossible() {
        updateNetworkStatus();
        return networkConnected && cert4androidReady;
    }

    public CustomCertManager getCustomCertManager() {
        return customCertManager;
    }

    public void checkCertificate(byte[] cert, IOnCertificateDecision callback) throws RemoteException {
        iCustomCertService.checkTrusted(cert, true, false, callback);
    }

    /**
     * Adds a callback method to the MoneyBusterServerSyncHelper for the synchronization part push local changes to the server.
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
     * Adds a callback method to the MoneyBusterServerSyncHelper for the synchronization part pull remote changes from the server.
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
     * @param onlyLocalChanges Whether to only push local changes to the server or to also load the project info
     */
    public void scheduleSync(boolean onlyLocalChanges, long projId) {
        Log.d(getClass().getSimpleName(), "Sync requested (" + (onlyLocalChanges ? "onlyLocalChanges" : "full") + "; " + (syncActive ? "sync active" : "sync NOT active") + ") ...");
        Log.d(getClass().getSimpleName(), "(network:" + networkConnected + "; cert4android:" + cert4androidReady + ")");
        updateNetworkStatus();
        if (isSyncPossible() && (!syncActive || onlyLocalChanges)) {
            DBProject project = dbHelper.getProject(projId);
            if (project != null) {
                Log.d(getClass().getSimpleName(), "... starting now");
                SyncTask syncTask = new SyncTask(onlyLocalChanges, project);
                syncTask.addCallbacks(callbacksPush);
                callbacksPush = new ArrayList<>();
                if (!onlyLocalChanges) {
                    syncTask.addCallbacks(callbacksPull);
                    callbacksPull = new ArrayList<>();
                }
                syncTask.execute();
            } else {
                Log.d(getClass().getSimpleName(), "sync asked for project " + projId + " which does not exist : DOING NOTHING");
            }
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
            Log.d(MoneyBusterServerSyncHelper.class.getSimpleName(), "Network connection established.");
            networkConnected = true;
        } else {
            networkConnected = false;
            Log.d(MoneyBusterServerSyncHelper.class.getSimpleName(), "No network connection.");
        }
    }

    /**
     * SyncTask is an AsyncTask which performs the synchronization in a background thread.
     * Synchronization consists of two parts: pushLocalChanges and pullRemoteChanges.
     */
    private class SyncTask extends AsyncTask<Void, Void, LoginStatus> {
        private final boolean onlyLocalChanges;
        private DBProject project;
        private final List<ICallback> callbacks = new ArrayList<>();
        private VersatileProjectSyncClient client;
        private List<Throwable> exceptions = new ArrayList<>();

        public SyncTask(boolean onlyLocalChanges, DBProject project) {
            this.onlyLocalChanges = onlyLocalChanges;
            Log.i(getClass().getSimpleName(), "SYNC TASK project : " + project.getRemoteId());
            this.project = project;
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
            client = createVersatileProjectSyncClient();
            Log.i(getClass().getSimpleName(), "STARTING SYNCHRONIZATION");
            //dbHelper.debugPrintFullDB();
            LoginStatus status = LoginStatus.OK;

            status = pushLocalChanges();
            if (!onlyLocalChanges && status == LoginStatus.OK) {
                status = pullRemoteChanges();
            }
            //dbHelper.debugPrintFullDB();
            Log.i(getClass().getSimpleName(), "SYNCHRONIZATION FINISHED");
            return status;
        }

        private LoginStatus pushLocalChanges() {
            LoginStatus status;

            Log.d(getClass().getSimpleName(), "PUSH LOCAL CHANGES");

            try {
                // get the remote member list to solve conflict when locally added an existing remote member
                ServerResponse.MembersResponse membersResponse = client.getMembers(customCertManager, project);
                List<DBMember> remoteMembers = membersResponse.getMembers(project.getId());
                List<String> remoteMembersNames = new ArrayList<>();
                for (DBMember m : remoteMembers) {
                    remoteMembersNames.add(m.getName());
                }
                // push member changes
                // add members
                List<DBMember> membersToAdd = dbHelper.getMembersOfProjectWithState(project.getId(), DBBill.STATE_ADDED);
                for (DBMember mToAdd : membersToAdd) {
                    // it exists remotely, just update it locally to solve conflict
                    int searchIndex = remoteMembersNames.indexOf(mToAdd.getName());
                    if (searchIndex != -1) {
                        DBMember remoteMember = remoteMembers.get(searchIndex);
                        dbHelper.updateMember(
                                mToAdd.getId(), null,
                                remoteMember.getWeight(), remoteMember.isActivated(),
                                DBBill.STATE_OK, remoteMember.getRemoteId(), remoteMember.getR(),
                                remoteMember.getG(), remoteMember.getB()
                        );
                    }
                    // it does not exist, create it remotely
                    else {
                        ServerResponse.CreateRemoteMemberResponse createRemoteMemberResponse = client.createRemoteMember(customCertManager, project, mToAdd);
                        long newRemoteId = Long.valueOf(createRemoteMemberResponse.getStringContent());
                        if (newRemoteId > 0) {
                            dbHelper.updateMember(
                                    mToAdd.getId(), null,
                                    null, null, DBBill.STATE_OK, newRemoteId, null, null, null
                            );
                        }
                    }
                }

                // edit members
                List<DBMember> membersToEdit = dbHelper.getMembersOfProjectWithState(project.getId(), DBBill.STATE_EDITED);
                for (DBMember mToEdit : membersToEdit) {
                    try {
                        ServerResponse.EditRemoteMemberResponse editRemoteMemberResponse = client.editRemoteMember(customCertManager, project, mToEdit);
                        long remoteId = editRemoteMemberResponse.getRemoteId(project.getId());
                        if (remoteId == mToEdit.getRemoteId()) {
                            dbHelper.updateMember(
                                    mToEdit.getId(), null,
                                    null, null, DBBill.STATE_OK, null,
                                    null, null, null
                            );
                        }
                    } catch (IOException e) {
                        if (e.getMessage().equals("{\"message\": \"Internal Server Error\"}")) {
                            Log.d(getClass().getSimpleName(), "EDIT MEMBER FAILED : it does not exist remotely");
                            // what if member does not exist anymore :
                            // pullremotechanges will delete it locally (if no bill associated)
                            // but maybe there still are bills for this user,
                            // wait untill the end of pullremotechanges to check
                            // and delete useless members
                            //dbHelper.deleteMember(mToEdit.getId());
                        } else {
                            throw e;
                        }
                    }
                }

                // get members
                List<DBMember> members = dbHelper.getMembersOfProject(project.getId(), null);
                // get member id map
                Map<Long, Long> memberIdToRemoteId = new HashMap<>();
                for (DBMember m : members) {
                    memberIdToRemoteId.put(m.getId(), m.getRemoteId());
                }

                // delete what's been deleted
                List<DBBill> toDelete = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_DELETED);
                for (DBBill bToDel : toDelete) {
                    try {
                        ServerResponse.DeleteRemoteBillResponse deleteRemoteBillResponse = client.deleteRemoteBill(customCertManager, project, bToDel.getRemoteId());
                        if (deleteRemoteBillResponse.getStringContent().equals("OK")) {
                            Log.d(getClass().getSimpleName(), "successfully deleted bill on remote project : delete it locally");
                            dbHelper.deleteBill(bToDel.getId());
                        }
                    } catch (IOException e) {
                        // if it's not there on the server
                        if (e.getMessage().equals("\"Not Found\"")) {
                            Log.d(getClass().getSimpleName(), "failed to delete bill on remote project : delete it locally anyway");
                            dbHelper.deleteBill(bToDel.getId());
                        } else {
                            throw e;
                        }
                    }
                }
                // edit what's been edited
                List<DBBill> toEdit = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_EDITED);
                for (DBBill bToEdit : toEdit) {
                    try {
                        ServerResponse.EditRemoteBillResponse editRemoteBillResponse = client.editRemoteBill(customCertManager, project, bToEdit, memberIdToRemoteId);
                        if (editRemoteBillResponse.getStringContent().equals(String.valueOf(bToEdit.getRemoteId()))) {
                            dbHelper.setBillState(bToEdit.getId(), DBBill.STATE_OK);
                            Log.d(getClass().getSimpleName(), "SUCCESSFUL remote bill edition (" + editRemoteBillResponse.getStringContent() + ")");
                        } else {
                            Log.d(getClass().getSimpleName(), "FAILED to edit remote bill (" + editRemoteBillResponse.getStringContent() + ")");
                        }
                    } catch (IOException e) {
                        // if it's not there on the server
                        if (e.getMessage().equals("{\"message\": \"Internal Server Error\"}")) {
                            Log.d(getClass().getSimpleName(), "FAILED to edit remote bill : it does not exist remotely");
                            // pullremotechanges will take care of deletion
                            //dbHelper.deleteBill(bToEdit.getId());
                        } else {
                            throw e;
                        }
                    }
                }
                // add what's been added
                List<DBBill> toAdd = dbHelper.getBillsOfProjectWithState(project.getId(), DBBill.STATE_ADDED);
                for (DBBill bToAdd : toAdd) {
                    ServerResponse.CreateRemoteBillResponse createRemoteBillResponse = client.createRemoteBill(customCertManager, project, bToAdd, memberIdToRemoteId);
                    long newRemoteId = Long.valueOf(createRemoteBillResponse.getStringContent());
                    if (newRemoteId > 0) {
                        dbHelper.updateBill(
                                bToAdd.getId(), newRemoteId, null,
                                null, null, null,
                                DBBill.STATE_OK, null, null, null
                        );
                    }
                }
                status = LoginStatus.OK;
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
            Log.d(getClass().getSimpleName(), "END PUSH LOCAL CHANGES");
            return status;
        }

        /**
         * Pull remote Changes: update or create each remote members/bills and remove remotely deleted ones
         */
        private LoginStatus pullRemoteChanges() {
            Log.d(getClass().getSimpleName(), "pullRemoteChanges(" + project + ")");
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(appContext);
            boolean cospendSmartSync = pref.getBoolean(appContext.getString(R.string.pref_key_smart_sync), false);
            String lastETag = null;
            long lastModified = 0;
            LoginStatus status;
            try {
                ServerResponse.ProjectResponse projResponse = client.getProject(customCertManager, project, lastModified, lastETag);
                String name = projResponse.getName();
                String email = projResponse.getEmail();
                // update project if needed
                if (project.getName() == null || project.getName().equals("")
                        || !name.equals(project.getName())
                        || project.getEmail() == null
                        || project.getEmail().equals("")
                        || !email.equals(project.getEmail())) {
                    Log.d(getClass().getSimpleName(), "update local project : " + project);
                    // this is usefull to transmit correct info back to billListActivity when project was just added
                    project.setName(name);
                    dbHelper.updateProject(project.getId(), name, email,
                            null, null, null);
                }

                // get members
                List<DBMember> remoteMembers = projResponse.getMembers(project.getId());
                Map<Long, DBMember> remoteMembersByRemoteId = new HashMap<>();
                for (DBMember remoteMember : remoteMembers) {
                    remoteMembersByRemoteId.put(remoteMember.getRemoteId(), remoteMember);
                }

                // add/update/delete members
                for (DBMember m : remoteMembers) {
                    DBMember localMember = dbHelper.getMember(m.getRemoteId(), project.getId());
                    // member does not exist locally, add it
                    if (localMember == null) {
                        Log.d(getClass().getSimpleName(), "Add local member : " + m);
                        dbHelper.addMember(m);
                    }
                    // member exists, check if needs update
                    else {
                        if (m.getName().equals(localMember.getName()) &&
                                m.getWeight() == localMember.getWeight() &&
                                m.isActivated() == localMember.isActivated() &&
                                m.getR() == localMember.getR() &&
                                m.getG() == localMember.getG() &&
                                m.getB() == localMember.getB()
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for member : " + localMember);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local member : " + m);
                            // long memberId, @Nullable String newName, @Nullable Double newWeight,
                            // @Nullable Boolean newActivated, @Nullable Integer newState, @Nullable Long newRemoteId
                            dbHelper.updateMember(
                                    localMember.getId(), m.getName(), m.getWeight(),
                                    m.isActivated(), null, null,
                                    m.getR(), m.getG(), m.getB()
                            );
                        }
                    }
                }

                // get up-to-date DB members
                List<DBMember> dbMembers = dbHelper.getMembersOfProject(project.getId(), null);
                // get member id map
                Map<Long, Long> memberRemoteIdToId = new HashMap<>();
                for (DBMember m : dbMembers) {
                    memberRemoteIdToId.put(m.getRemoteId(), m.getId());
                }

                // get bills
                ServerResponse.BillsResponse billsResponse = client.getBills(customCertManager, project, cospendSmartSync);
                List<DBBill> remoteBills;
                List<Long> remoteAllBillIds = new ArrayList<>();
                long serverSyncTimestamp = project.getLastSyncedTimestamp();
                // get all bill ids
                // and get server timestamp when this request was done
                // COSPEND => we only get new/changed bills since last sync
                // and bill id list and server timestamp at the sync moment
                if (ProjectType.COSPEND.equals(project.getType())) {
                    remoteBills = billsResponse.getBillsCospend(project.getId(), memberRemoteIdToId, cospendSmartSync);
                    if (cospendSmartSync) {
                        remoteAllBillIds = billsResponse.getAllBillIds();
                        serverSyncTimestamp = billsResponse.getSyncTimestamp();
                    }
                }
                // IHATEMONEY => we get all bills
                else {
                    remoteBills = billsResponse.getBillsIHM(project.getId(), memberRemoteIdToId);
                }

                Map<Long, DBBill> remoteBillsByRemoteId = new HashMap<>();
                for (DBBill remoteBill : remoteBills) {
                    remoteBillsByRemoteId.put(remoteBill.getRemoteId(), remoteBill);
                }
                List<DBBill> localBills = dbHelper.getBillsOfProject(project.getId());
                Map<Long, DBBill> localBillsByRemoteId = new HashMap<>();
                for (DBBill localBill : localBills) {
                    localBillsByRemoteId.put(localBill.getRemoteId(), localBill);
                }

                // add, update or delete DB bills
                for (DBBill remoteBill : remoteBills) {
                    // add if local does not exist
                    if (!localBillsByRemoteId.containsKey(remoteBill.getRemoteId())) {
                        long billId = dbHelper.addBill(remoteBill);
                        Log.d(TAG, "Add local bill : " + remoteBill);
                    }
                    // update bill if necessary
                    // and billOwers if necessary
                    else {
                        DBBill localBill = localBillsByRemoteId.get(remoteBill.getRemoteId());
                        if (hasChanged(localBill, remoteBill)) {
                            dbHelper.updateBill(
                                    localBill.getId(), null, remoteBill.getPayerId(),
                                    remoteBill.getAmount(), remoteBill.getDate(),
                                    remoteBill.getWhat(), DBBill.STATE_OK, remoteBill.getRepeat(),
                                    remoteBill.getPaymentMode(), remoteBill.getCategoryId()
                            );
                            Log.d(TAG, "Update local bill : " + remoteBill);
                        } else {
                            // fine
                            Log.d(TAG, "Nothing to do for bill : " + localBill);
                            Log.d(TAG, "remote bill : " + remoteBill);
                        }
                        //////// billowers
                        Map<Long, DBBillOwer> localBillOwersByIds = new HashMap<>();
                        for (DBBillOwer bo : localBill.getBillOwers()) {
                            localBillOwersByIds.put(bo.getMemberId(), bo);
                        }
                        Map<Long, DBBillOwer> remoteBillOwersByIds = new HashMap<>();
                        for (DBBillOwer bo : remoteBill.getBillOwers()) {
                            remoteBillOwersByIds.put(bo.getMemberId(), bo);
                        }
                        // add remote which are not here
                        for (DBBillOwer rbo : remoteBill.getBillOwers()) {
                            if (!localBillOwersByIds.containsKey(rbo.getMemberId())) {
                                dbHelper.addBillower(localBill.getId(), rbo.getMemberId());
                                Log.d(TAG, "Add local billOwer : " + rbo);
                            }
                        }
                        // delete local which are not there remotely
                        for (DBBillOwer lbo : localBill.getBillOwers()) {
                            if (!remoteBillOwersByIds.containsKey(lbo.getMemberId())) {
                                dbHelper.deleteBillOwer(lbo.getId());
                                Log.d(TAG, "Delete local billOwer : " + lbo);
                            }
                        }
                    }
                }

                // delete local bill
                // DELETION is now different between IHM and COSPEND
                if (ProjectType.COSPEND.equals(project.getType()) && cospendSmartSync) {
                    for (DBBill localBill : localBills) {
                        // if local bill does not exist remotely
                        if (remoteAllBillIds.indexOf(localBill.getRemoteId()) <= -1) {
                            dbHelper.deleteBill(localBill.getId());
                            Log.d(TAG, "Delete local bill : " + localBill);
                        }
                    }
                }
                else {
                    for (DBBill localBill : localBills) {
                        // if local bill does not exist remotely
                        if (!remoteBillsByRemoteId.containsKey(localBill.getRemoteId())) {
                            dbHelper.deleteBill(localBill.getId());
                            Log.d(TAG, "Delete local bill : " + localBill);
                        }
                    }
                }

                // delete local members
                // do this at the end to check if member that are not there remotely
                // don't have any bill anymore
                List<DBMember> localMembers = dbHelper.getMembersOfProject(project.getId(), null);
                for (DBMember localMember : localMembers) {
                    // if local member does not exist remotely
                    // we could trust the server, member should not be involved in anything anymore
                    if (!remoteMembersByRemoteId.containsKey(localMember.getRemoteId())) {
                        // but we check if there is any bill/billower related to the member
                        if (dbHelper.getBillsOfMember(localMember.getId()).size() == 0
                                && dbHelper.getBillowersOfMember(localMember.getId()).size() == 0) {
                            dbHelper.deleteMember(localMember.getId());
                            Log.d(TAG, "Delete local member : " + localMember);
                        } else {
                            Log.d(TAG,
                                    "WARNING local member : " + localMember.getName() + " does not exist remotely but is " +
                                            "still involved in some bills");
                        }
                    }
                }

                // finally update local project last sync timestamp
                //serverSyncTimestamp
                dbHelper.updateProject(
                        project.getId(), null, null,
                        null, null, serverSyncTimestamp
                );
                status = LoginStatus.OK;
            } catch (ServerResponse.NotModifiedException e) {
                Log.d(TAG, "No changes, nothing to do.");
                status = LoginStatus.OK;
            } catch (IOException e) {
                Log.e(TAG, "Exception", e);
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                Log.e(TAG, "Exception", e);
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
                // if the bills list is not visible, no toast
                Intent intent = new Intent(BROADCAST_PROJECT_SYNC_FAILED);
                intent.putExtra(BillsListViewActivity.BROADCAST_ERROR_MESSAGE, errorString);
                appContext.sendBroadcast(intent);
            } else {
                Intent intent = new Intent(BROADCAST_PROJECT_SYNCED);
                intent.putExtra(BillsListViewActivity.BROADCAST_EXTRA_PARAM, project.getName());
                appContext.sendBroadcast(intent);
            }
            syncActive = false;
            // notify callbacks
            for (ICallback callback : callbacks) {
                callback.onFinish();
            }
            // start next sync if scheduled meanwhile
            if (syncScheduled) {
                scheduleSync(false, project.getId());
            }
        }
    }

    private VersatileProjectSyncClient createVersatileProjectSyncClient() {
        return new VersatileProjectSyncClient();
    }

    public boolean editRemoteProject(long projId, String newName, String newEmail, String newPassword, ICallback callback) {
        updateNetworkStatus();
        if (isSyncPossible()) {
            EditRemoteProjectTask editRemoteProjectTask = new EditRemoteProjectTask(projId, newName, newEmail, newPassword, callback);
            editRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class EditRemoteProjectTask extends AsyncTask<Void, Void, LoginStatus> {
        private VersatileProjectSyncClient client;
        private String newName;
        private String newEmail;
        private String newPassword;
        private DBProject project;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public EditRemoteProjectTask(long projId, String newName, String newEmail, String newPassword, ICallback callback) {
            this.project = dbHelper.getProject(projId);
            this.newName = newName;
            this.newEmail = newEmail;
            this.newPassword = newPassword;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING edit remote project");
            }
            LoginStatus status = LoginStatus.OK;
            try {
                ServerResponse.EditRemoteProjectResponse response = client.editRemoteProject(customCertManager, project, newName, newEmail, newPassword);
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE edit remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED edit remote project");
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
            } else {
                dbHelper.updateProject(project.getId(), newName, newEmail, newPassword,
                          null, null);
            }
            callback.onFinish(newName, errorString);
        }
    }

    public boolean deleteRemoteProject(long projId, ICallback callback) {
        updateNetworkStatus();
        if (isSyncPossible()) {
            DeleteRemoteProjectTask deleteRemoteProjectTask = new DeleteRemoteProjectTask(projId, callback);
            deleteRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class DeleteRemoteProjectTask extends AsyncTask<Void, Void, LoginStatus> {
        private VersatileProjectSyncClient client;
        private DBProject project;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public DeleteRemoteProjectTask(long projId, ICallback callback) {
            this.project = dbHelper.getProject(projId);
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING delete remote project");
            }
            LoginStatus status = LoginStatus.OK;
            try {
                ServerResponse.DeleteRemoteProjectResponse response = client.deleteRemoteProject(customCertManager, project);
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE delete remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED delete device");
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
            } else {
                dbHelper.deleteProject(project.getId());
            }
            callback.onFinish(String.valueOf(project.getId()), errorString);
        }
    }

    public boolean createRemoteProject(String remoteId, String name, String email, String password, String ihmUrl, ProjectType projectType, ICallback callback) {
        if (isSyncPossible()) {
            DBProject proj = new DBProject(0, remoteId, password, name, ihmUrl, email,
                    null, projectType, Long.valueOf(0));
            CreateRemoteProjectTask createRemoteProjectTask = new CreateRemoteProjectTask(proj, callback);
            createRemoteProjectTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     */
    private class CreateRemoteProjectTask extends AsyncTask<Void, Void, LoginStatus> {
        private VersatileProjectSyncClient client;
        private DBProject project;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public CreateRemoteProjectTask(DBProject project, ICallback callback) {
            this.project = project;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "STARTING create remote project");
            }
            LoginStatus status = LoginStatus.OK;
            try {
                ServerResponse.CreateRemoteProjectResponse response = client.createRemoteProject(customCertManager, project);
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE create remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            }
            if (BillsListViewActivity.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED create remote project");
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
            } else {
                //dbHelper.deleteProject(project.getId());
            }
            callback.onFinish(project.getRemoteId(), errorString);
        }
    }

    private boolean hasChanged(DBBill localBill, DBBill remoteBill) {
        if (
                localBill.getPayerId() == remoteBill.getPayerId() &&
                        localBill.getAmount() == remoteBill.getAmount() &&
                        localBill.getDate().equals(remoteBill.getDate()) &&
                        localBill.getWhat().equals(remoteBill.getWhat()) &&
                        localBill.getPaymentMode().equals(remoteBill.getPaymentMode()) &&
                        localBill.getCategoryId() == remoteBill.getCategoryId()
        ) {
            String localRepeat = localBill.getRepeat() == null ? DBBill.NON_REPEATED : localBill.getRepeat();
            String remoteRepeat = remoteBill.getRepeat() == null ? DBBill.NON_REPEATED : remoteBill.getRepeat();

            return !localRepeat.equals(remoteRepeat);
        } else {
            return true;
        }
    }

    // ACCOUNT SYNC

    public void runAccountProjectsSync() {
        Log.d(getClass().getSimpleName(), "Account projects sync requested; " + (syncAccountProjectsActive ? "sync active" : "sync NOT active") + ") ...");
        Log.d(getClass().getSimpleName(), "(network:" + networkConnected + "; cert4android:" + cert4androidReady + ")");
        updateNetworkStatus();
        if (isNextcloudAccountConfigured(appContext) && isSyncPossible() && (!syncAccountProjectsActive)) {
            SyncAccountProjectsTask syncAccountProjectTask = new SyncAccountProjectsTask();
            syncAccountProjectTask.execute();
        }
    }

    private NextcloudAPI.ApiConnectedListener apiCallback = new NextcloudAPI.ApiConnectedListener() {
        @Override
        public void onConnected() {
            // ignore this one..
            Log.d(getClass().getSimpleName(), "API connected!!!!");
        }

        @Override
        public void onError(Exception ex) {
            // TODO handle error in your app
        }
    };

    private CospendClient createCospendClient() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = "";
        String username = "";
        String password = "";
        boolean useSSO = preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
        if (useSSO) {
            try {
                SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext.getApplicationContext());
                NextcloudAPI nextcloudAPI = new NextcloudAPI(appContext.getApplicationContext(), ssoAccount, new GsonBuilder().create(), apiCallback);
                return new CospendClient(url, username, password, nextcloudAPI);
            }
            catch (NextcloudFilesAppAccountNotFoundException e) {
                return null;
            }
            catch (NoCurrentAccountSelectedException e) {
                return null;
            }
        }
        else {
            url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
            username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
            password = preferences.getString(SettingsActivity.SETTINGS_PASSWORD, SettingsActivity.DEFAULT_SETTINGS);
            return new CospendClient(url, username, password, null);
        }
    }

    private class SyncAccountProjectsTask extends AsyncTask<Void, Void, LoginStatus> {
        private final List<ICallback> callbacks = new ArrayList<>();
        private CospendClient client;
        private List<Throwable> exceptions = new ArrayList<>();

        public SyncAccountProjectsTask() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            syncAccountProjectsActive = true;
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createCospendClient(); // recreate client on every sync in case the connection settings was changed
            Log.i(getClass().getSimpleName(), "STARTING account projects SYNCHRONIZATION");
            LoginStatus status = LoginStatus.OK;

            if (client != null) {
                status = pullRemoteProjects();
            }
            else {
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }
            Log.i(getClass().getSimpleName(), "SYNCHRONIZATION FINISHED");
            return status;
        }

        /**
         * Pull remote Changes: update or create each remote session and remove remotely deleted sessions.
         */
        private LoginStatus pullRemoteProjects() {
            Log.d(getClass().getSimpleName(), "pullRemoteProjects()");
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            LoginStatus status;
            try {
                // get NC url
                String url = "";
                boolean useSSO = preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
                if (useSSO) {
                    try {
                        SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext.getApplicationContext());
                        url = ssoAccount.url;
                    }
                    catch (NextcloudFilesAppAccountNotFoundException e) {
                    }
                    catch (NoCurrentAccountSelectedException e) {
                    }
                }
                else {
                    url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
                }

                List<DBProject> localProjects = dbHelper.getProjects();

                ServerResponse.AccountProjectsResponse response = client.getAccountProjects(customCertManager);
                List<DBAccountProject> remoteAccountProjects = response.getAccountProjects(url);
                // we successfully got accounts (or zero), so we can clear local ones before adding new ones
                dbHelper.clearAccountProjects();
                for (DBAccountProject remoteAccountProject : remoteAccountProjects) {
                    dbHelper.addAccountProject(remoteAccountProject);
                    Log.v(getClass().getSimpleName(), "received account project "+remoteAccountProject);
                    // if the project does not exist yet, add it with empty password
                    boolean found = false;
                    for (DBProject localProject : localProjects) {
                        Log.v(getClass().getSimpleName(), "QQQQQQ "+localProject.getName()+" "+localProject.getIhmUrl());
                        if (localProject.getRemoteId().equals(remoteAccountProject.getRemoteId())
                                && localProject.getIhmUrl().replaceAll("/+$", "")
                                    .equals(remoteAccountProject.getncUrl().replaceAll("/+$", "") + "/index.php/apps/cospend")
                        ) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        DBProject newProj = new DBProject(0,
                                remoteAccountProject.getRemoteId(),
                                "",
                                remoteAccountProject.getName(),
                                remoteAccountProject.getncUrl().replaceAll("/+$", "") + "/index.php/apps/cospend",
                                "",
                                null,
                                ProjectType.COSPEND,
                                Long.valueOf(0)
                        );
                        dbHelper.addProject(newProj);
                    }
                }
                status = LoginStatus.OK;
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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
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
                // if the bill list is not visible, no toast
                Intent intent = new Intent(BillsListViewActivity.BROADCAST_ACCOUNT_PROJECTS_SYNC_FAILED);
                intent.putExtra(BillsListViewActivity.BROADCAST_ERROR_MESSAGE, errorString);
                appContext.sendBroadcast(intent);
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    Intent intent2 = new Intent(BillsListViewActivity.BROADCAST_SSO_TOKEN_MISMATCH);
                    appContext.sendBroadcast(intent2);
                }
            }
            else {
                Intent intent = new Intent(BillsListViewActivity.BROADCAST_ACCOUNT_PROJECTS_SYNCED);
                appContext.sendBroadcast(intent);
            }
            syncAccountProjectsActive = false;
        }
    }
}
