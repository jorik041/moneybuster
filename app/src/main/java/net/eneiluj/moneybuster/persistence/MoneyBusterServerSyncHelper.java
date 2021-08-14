package net.eneiluj.moneybuster.persistence;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;
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
import net.eneiluj.moneybuster.model.DBCategory;
import net.eneiluj.moneybuster.model.DBCurrency;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.service.SyncService;
import net.eneiluj.moneybuster.util.CospendClient;
import net.eneiluj.moneybuster.util.CospendClientUtil.LoginStatus;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.IProjectCreationCallback;
import net.eneiluj.moneybuster.util.VersatileProjectSyncClient;
import net.eneiluj.moneybuster.util.ServerResponse;
import net.eneiluj.moneybuster.util.SupportUtil;

import org.json.JSONException;
import org.json.JSONObject;

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
    public static final String BROADCAST_AVATAR_UPDATED = "net.eneiluj.moneybuster.broadcast.avatar_updated";
    public static final String BROADCAST_AVATAR_UPDATED_MEMBER = "net.eneiluj.moneybuster.broadcast.avatar_updated_for_member";

    private static int NOTIFICATION_ID = 1526756699;

    private SharedPreferences preferences;

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
    private static List<Long> projectIdsToSync = new ArrayList<>();

    // current state of the account projects synchronization
    private boolean syncAccountProjectsActive = false;

    // list of callbacks for both parts of synchronziation
    private List<ICallback> callbacksPush = new ArrayList<>();
    private List<ICallback> callbacksPull = new ArrayList<>();

    private MoneyBusterServerSyncHelper(MoneyBusterSQLiteOpenHelper db) {
        this.dbHelper = db;
        this.appContext = db.getContext().getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(dbHelper.getContext());
        new Thread() {
            @Override
            public void run() {
                customCertManager = SupportUtil.getCertManager(appContext);
            }
        }.start();

        updateNetworkStatus();
        // bind to certificate service to block sync attempts if service is not ready
        appContext.bindService(new Intent(appContext, CustomCertService.class), certService, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void finalize() throws Throwable {
        appContext.unbindService(certService);
        if (customCertManager != null) {
            customCertManager.close();
        }
        super.finalize();
    }

    public static boolean isNextcloudAccountConfigured(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return !preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).isEmpty() ||
                preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
    }

    public static String getNextcloudAccountServerUrl(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false)) {
            try {
                SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context.getApplicationContext());
                return ssoAccount.url.replaceAll("/+$", "");
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException e) {
                return "";
            }
        } else {
            return preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).replaceAll("/+$", "");
        }
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
            projectIdsToSync.add(projId);
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
        private List<String> errorMessages = new ArrayList<>();
        private int nbPulledNewBills = 0;
        private int nbPulledUpdatedBills = 0;
        private int nbPulledDeletedBills = 0;
        private String newBillsDialogText = "";
        private String updatedBillsDialogText = "";
        private String deletedBillsDialogText = "";

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
            syncActive = true;
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createVersatileProjectSyncClient();
            Log.i(getClass().getSimpleName(), "STARTING SYNCHRONIZATION");
            //dbHelper.debugPrintFullDB();
            LoginStatus status = LoginStatus.OK;

            status = pushLocalChanges();
            if (status == LoginStatus.OK) {
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
                                remoteMember.getG(), remoteMember.getB(),
                                remoteMember.getNcUserId(), ""
                        );
                    }
                    // it does not exist, create it remotely
                    else {
                        ServerResponse.CreateRemoteMemberResponse createRemoteMemberResponse = client.createRemoteMember(customCertManager, project, mToAdd);
                        long newRemoteId = Long.valueOf(createRemoteMemberResponse.getStringContent());
                        if (newRemoteId > 0) {
                            dbHelper.updateMember(
                                mToAdd.getId(), null,
                                null, null, DBBill.STATE_OK, newRemoteId,
                                null, null, null, null, null
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
                                    null, null, null, null, null
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
                                DBBill.STATE_OK, null, null, null,
                                null
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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch SSO HTTP req FAILED", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
                if (e.getCause() != null) {
                    exceptions.add(e.getCause());
                }
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
                String currencyName = projResponse.getCurrencyName();
                // update project if needed
                if (project.getName() == null || project.getName().equals("")
                        || !name.equals(project.getName())
                        || project.getEmail() == null
                        || project.getEmail().equals("")
                        || (
                                (currencyName == null && project.getCurrencyName() != null) ||
                                (currencyName != null && project.getCurrencyName() == null) ||
                                (currencyName != null && !currencyName.equals(project.getCurrencyName()))
                        )
                        || !email.equals(project.getEmail())) {
                    Log.d(getClass().getSimpleName(), "update local project : " + project);
                    // this is usefull to transmit correct info back to billListActivity when project was just added
                    project.setName(name);
                    project.setCurrencyName(currencyName);
                    dbHelper.updateProject(project.getId(), name, email,
                            null, null, null, currencyName);
                }

                // get categories
                List<DBCategory> remoteCategories = projResponse.getCategories(project.getId());
                Map<Long, DBCategory> remoteCategoriesByRemoteId = new HashMap<>();
                for (DBCategory remoteCategory : remoteCategories) {
                    remoteCategoriesByRemoteId.put(remoteCategory.getRemoteId(), remoteCategory);
                }

                // add/update/delete categories
                for (DBCategory c : remoteCategories) {
                    DBCategory localCategory = dbHelper.getCategory(c.getRemoteId(), project.getId());
                    // category does not exist locally, add it
                    if (localCategory == null) {
                        Log.d(getClass().getSimpleName(), "Add local category : " + c);
                        dbHelper.addCategory(c);
                    }
                    // category exists, check if needs update
                    else {
                        if (c.getName().equals(localCategory.getName()) &&
                                c.getColor().equals(localCategory.getColor()) &&
                                c.getIcon().equals(localCategory.getIcon())
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for category : " + localCategory);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local category : " + c);

                            dbHelper.updateCategory(
                                    localCategory.getId(), c.getName(), c.getIcon(), c.getColor()
                            );
                        }
                    }
                }

                // delete local categories which are not there remotely
                List<DBCategory> localCategories = dbHelper.getCategories(project.getId());
                for (DBCategory localCategory : localCategories) {
                    if (!remoteCategoriesByRemoteId.containsKey(localCategory.getRemoteId())) {
                        dbHelper.deleteCategory(localCategory.getId());
                        Log.d(TAG, "Delete local category : " + localCategory);
                    }
                }

                // get currencies
                List<DBCurrency> remoteCurrencies = projResponse.getCurrencies(project.getId());
                Map<Long, DBCurrency> remoteCurrenciesByRemoteId = new HashMap<>();
                for (DBCurrency remoteCurrency : remoteCurrencies) {
                    remoteCurrenciesByRemoteId.put(remoteCurrency.getRemoteId(), remoteCurrency);
                }

                // add/update/delete currencies
                for (DBCurrency c : remoteCurrencies) {
                    DBCurrency localCurrency = dbHelper.getCurrency(c.getRemoteId(), project.getId());
                    // currency does not exist locally, add it
                    if (localCurrency == null) {
                        Log.d(getClass().getSimpleName(), "Add local currency : " + c);
                        dbHelper.addCurrency(c);
                    }
                    // currency exists, check if needs update
                    else {
                        if (c.getName().equals(localCurrency.getName()) &&
                                c.getExchangeRate() == localCurrency.getExchangeRate()
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for currency : " + localCurrency);
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local currency : " + c);

                            dbHelper.updateCurrency(
                                    localCurrency.getId(), c.getName(), c.getExchangeRate()
                            );
                        }
                    }
                }

                // delete local currencies which are not there remotely
                List<DBCurrency> localCurrencies = dbHelper.getCurrencies(project.getId());
                for (DBCurrency localCurrency : localCurrencies) {
                    if (!remoteCurrenciesByRemoteId.containsKey(localCurrency.getRemoteId())) {
                        dbHelper.deleteCurrency(localCurrency.getId());
                        Log.d(TAG, "Delete local currency : " + localCurrencies);
                    }
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
                        long mid = dbHelper.addMember(m);
                        if (m.getNcUserId() != null && !"".equals(m.getNcUserId())) {
                            updateMemberAvatar(mid);
                        }
                    }
                    // member exists, check if needs update
                    else {
                        boolean ncUserIdChanged = (
                                (m.getNcUserId() == null && localMember.getNcUserId() != null) ||
                                (m.getNcUserId() != null && localMember.getNcUserId() == null) ||
                                (
                                        m.getNcUserId() != null && localMember.getNcUserId() != null &&
                                        !m.getNcUserId().equals(localMember.getNcUserId())
                                )
                        );
                        Log.e("PULLREMOTE", "member NC user id : "+localMember.getNcUserId()+" => "+m.getNcUserId()+" ID changed "+ncUserIdChanged);
                        if (ncUserIdChanged && m.getNcUserId() == null) {
                            m.setNcUserId("");
                        }
                        if (m.getName().equals(localMember.getName()) &&
                                m.getWeight() == localMember.getWeight() &&
                                m.isActivated() == localMember.isActivated() &&
                                (
                                        // if we get null color, then keep our local color
                                        (m.getR() == null && m.getG() == null && m.getB() == null) ||
                                        (m.getR() == localMember.getR() &&
                                            m.getG() == localMember.getG() &&
                                            m.getB() == localMember.getB())
                                ) &&
                                !ncUserIdChanged
                        ) {
                            // alright
                            Log.d(getClass().getSimpleName(), "Nothing to do for member : " + localMember);
                            // if there was no change BUT there is a userid and no avatar => update avatar
                            if (localMember.getNcUserId() != null && !"".equals(localMember.getNcUserId()) &&
                                    (localMember.getAvatar() == null || "".equals(localMember.getAvatar()))
                            ) {
                                Log.d(getClass().getSimpleName(), "except updating avatar");
                                updateMemberAvatar(localMember.getId());
                            }
                        } else {
                            Log.d(getClass().getSimpleName(), "Update local member : " + m);
                            Integer r = m.getR();
                            Integer g = m.getG();
                            Integer b = m.getB();
                            // don't change color if we get null from server
                            if (m.getR() == null && m.getG() == null && m.getB() == null) {
                                r = localMember.getR();
                                g = localMember.getG();
                                b = localMember.getB();
                            }
                            // determine if we reset local avatar
                            boolean needAvatarUpdate = (ncUserIdChanged && m.getNcUserId() != null && !m.getNcUserId().equals(""));
                            String newAvatar = null;
                            if (ncUserIdChanged) {
                                newAvatar = "";
                            }
                            dbHelper.updateMember(
                                    localMember.getId(), m.getName(), m.getWeight(),
                                    m.isActivated(), null, null,
                                    r, g, b, m.getNcUserId(), newAvatar
                            );
                            if (needAvatarUpdate) {
                                Log.e("PLOP", "pullremote : update member avatar");
                                updateMemberAvatar(localMember.getId());
                            }
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
                    remoteBills = billsResponse.getBillsCospend(project.getId(), memberRemoteIdToId);
                    if (cospendSmartSync || client.canAccessProjectWithSSO(project) || client.canAccessProjectWithNCLogin(project)) {
                        remoteAllBillIds = billsResponse.getAllBillIds();
                        serverSyncTimestamp = billsResponse.getSyncTimestamp();
                    } else {
                        // if smartsync is disabled, we still set last sync timestamp for the sidebar indicator
                        serverSyncTimestamp = System.currentTimeMillis() / 1000;
                    }
                }
                // IHATEMONEY => we get all bills
                else {
                    remoteBills = billsResponse.getBillsIHM(project.getId(), memberRemoteIdToId);
                    serverSyncTimestamp = System.currentTimeMillis() / 1000;
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
                        nbPulledNewBills++;
                        newBillsDialogText += "+ " + remoteBill.getWhat() + "\n";
                        Log.d(TAG, "Add local bill : " + remoteBill);
                    }
                    // update bill if necessary
                    // and billOwers if necessary
                    else {
                        DBBill localBill = localBillsByRemoteId.get(remoteBill.getRemoteId());
                        if (hasChanged(localBill, remoteBill)) {
                            dbHelper.updateBill(
                                    localBill.getId(), null, remoteBill.getPayerId(),
                                    remoteBill.getAmount(), remoteBill.getTimestamp(),
                                    remoteBill.getWhat(), DBBill.STATE_OK, remoteBill.getRepeat(),
                                    remoteBill.getPaymentMode(), remoteBill.getCategoryRemoteId(),
                                    remoteBill.getComment()
                            );
                            nbPulledUpdatedBills++;
                            updatedBillsDialogText += "✏ " + remoteBill.getWhat() + "\n";
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
                // if smartsync is enable OR if project is accessed with NC login =>
                // we just get bill ids
                if (ProjectType.COSPEND.equals(project.getType())
                        && (cospendSmartSync || client.canAccessProjectWithSSO(project) || client.canAccessProjectWithNCLogin(project))) {
                    for (DBBill localBill : localBills) {
                        // if local bill does not exist remotely
                        if (remoteAllBillIds.indexOf(localBill.getRemoteId()) <= -1) {
                            dbHelper.deleteBill(localBill.getId());
                            nbPulledDeletedBills++;
                            deletedBillsDialogText += "\uD83D\uDDD1 " + localBill.getWhat() + "\n";
                            Log.d(TAG, "Delete local bill : " + localBill);
                        }
                    }
                }
                else {
                    for (DBBill localBill : localBills) {
                        // if local bill does not exist remotely
                        if (!remoteBillsByRemoteId.containsKey(localBill.getRemoteId())) {
                            dbHelper.deleteBill(localBill.getId());
                            nbPulledDeletedBills++;
                            deletedBillsDialogText += "\uD83D\uDDD1 " + localBill.getWhat() + "\n";
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
                        null, null, serverSyncTimestamp,
                        null
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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch NC REQ failed", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
                if (e.getCause() != null) {
                    exceptions.add(e.getCause());
                }
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            if (status != LoginStatus.OK) {
                String errorString = "";
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    JSONObject obj = SupportUtil.getJsonObject(e.getMessage());
                    if (obj != null && obj.has("message")) {
                        try {
                            errorString += obj.getString("message") + "\n";
                        } catch (JSONException jsonEx) {
//                            errorString += e.getMessage() + "\n";
                        }
                    } else {
//                        errorString += e.getMessage() + "\n";
                    }
                }
                // broadcast the error
                // if the bills list is not visible, no toast
                Intent intent = new Intent(BROADCAST_PROJECT_SYNC_FAILED);
                intent.putExtra(BillsListViewActivity.BROADCAST_ERROR_MESSAGE, errorString);
                intent.putExtra(BillsListViewActivity.BROADCAST_PROJECT_ID, project.getId());
                appContext.sendBroadcast(intent);
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    Intent intent2 = new Intent(BillsListViewActivity.BROADCAST_SSO_TOKEN_MISMATCH);
                    appContext.sendBroadcast(intent2);
                }
            } else {
                Intent intent = new Intent(BROADCAST_PROJECT_SYNCED);
                intent.putExtra(BillsListViewActivity.BROADCAST_EXTRA_PARAM, project.getName());
                appContext.sendBroadcast(intent);
                // NOTIFICATION
                boolean notifyNew = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                boolean notifyUpdated = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                boolean notifyDeleted = preferences.getBoolean(appContext.getString(R.string.pref_key_notify_new), true);
                if (SyncService.isRunning() && !BillsListViewActivity.isActivityVisible()) {
                    String dialogContent = "";
                    String notificationContent = "";
                    if (notifyNew && nbPulledNewBills > 0) {
                        dialogContent += newBillsDialogText + "\n";
                        notificationContent += "+" + nbPulledNewBills + "  ";
                    }
                    if (notifyUpdated && nbPulledUpdatedBills > 0) {
                        dialogContent += updatedBillsDialogText + "\n";
                        notificationContent += "✏" + nbPulledUpdatedBills + "  ";
                    }
                    if (notifyDeleted && nbPulledDeletedBills > 0) {
                        dialogContent += deletedBillsDialogText;
                        notificationContent += "\uD83D\uDDD1" + nbPulledDeletedBills;
                    }
                    if (!notificationContent.equals("")) {
                        notificationContent = notificationContent.replaceAll(" \\| $", "");
                        notificationContent = appContext.getString(R.string.project_activity_notification, project.getName()) + ": " + notificationContent;
                        notifyProjectEvent(dialogContent, notificationContent, project.getId());
                    }
                }
            }
            syncActive = false;
            // notify callbacks
            for (ICallback callback : callbacks) {
                callback.onFinish();
            }
            // start next sync if there are ids in the schedule
            if (projectIdsToSync.size() > 0) {
                long pid = projectIdsToSync.remove(projectIdsToSync.size() - 1);
                scheduleSync(false, pid);
            }
        }
    }

    public String getErrorMessageFromException(NextcloudHttpRequestFailedException e) {
        int errorCode = e.getStatusCode();
        if (errorCode == 503) {
            return appContext.getString(R.string.error_maintenance_mode);
        } else if (errorCode == 401) {
            return appContext.getString(R.string.error_401);
        } else if (errorCode == 403) {
            return appContext.getString(R.string.error_403);
        } else if (errorCode == 404) {
            return appContext.getString(R.string.error_404);
        }
        return "";
    }

    public void notifyProjectEvent(String dialogContent, String notificationContent, long projectId) {
        // intent of notification
        Intent ptIntent = new Intent(appContext.getApplicationContext(), BillsListViewActivity.class);
        ptIntent.putExtra(BillsListViewActivity.PARAM_DIALOG_CONTENT, dialogContent);
        ptIntent.putExtra(BillsListViewActivity.PARAM_PROJECT_TO_SELECT, projectId);

        String chanId = String.valueOf(SyncService.MAIN_CHANNEL_ID + projectId);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, chanId)
                .setSmallIcon(R.drawable.ic_dollar_grey_24dp)
                .setContentTitle(appContext.getString(R.string.app_name))
                .setContentText(notificationContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(PendingIntent.getActivity(appContext, 1, ptIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        NOTIFICATION_ID++;
    }

    private VersatileProjectSyncClient createVersatileProjectSyncClient() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = "";
        String username = "";
        String password = "";
        boolean useSSO = preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
        if (useSSO) {
            try {
                SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext.getApplicationContext());
                NextcloudAPI nextcloudAPI = new NextcloudAPI(appContext.getApplicationContext(), ssoAccount, new GsonBuilder().create(), apiCallback);
                //Log.d(TAG, "SSSSSSSSSSSSS "+ssoAccount.url+" "+ssoAccount.userId);
                return new VersatileProjectSyncClient(url, username, password, nextcloudAPI, ssoAccount);
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
            return new VersatileProjectSyncClient(url, username, password, null, null);
        }
    }

    // if this is a cospend project and the server URL is the same as the configured account
    // => authenticated creation is possible
    public boolean canCreateAuthenticatedProject(DBProject project) {
        boolean isCospend = ProjectType.COSPEND.equals(project.getType());
        String projUrl = project.getServerUrl().replaceAll("/index.php/apps/cospend", "").replaceAll("/+$", "");

        String accountUrl = "";

        boolean useSSO = preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
        if (useSSO) {
            try {
                SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext.getApplicationContext());
                accountUrl = ssoAccount.url.replaceAll("/+$", "");
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException e) {
                return false;
            }
        } else {
            accountUrl = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).replaceAll("/$", "");
        }

        Log.v(TAG, "proj url : "+projUrl+" ; account url : "+accountUrl);
        return (isCospend && projUrl.equals(accountUrl));
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
        private List<String> errorMessages = new ArrayList<>();

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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch NC REQ failed", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
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
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    Intent intent2 = new Intent(BillsListViewActivity.BROADCAST_SSO_TOKEN_MISMATCH);
                    appContext.sendBroadcast(intent2);
                }
            } else {
                dbHelper.updateProject(project.getId(), newName, newEmail, newPassword,
                          null, null, null);
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
        private List<String> errorMessages = new ArrayList<>();

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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch NC REQ failed", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
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
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    Intent intent2 = new Intent(BillsListViewActivity.BROADCAST_SSO_TOKEN_MISMATCH);
                    appContext.sendBroadcast(intent2);
                }
            } else {
                dbHelper.deleteProject(project.getId());
            }
            callback.onFinish(String.valueOf(project.getId()), errorString);
        }
    }

    public boolean createRemoteProject(String remoteId, String name, String email, String password, String ihmUrl, ProjectType projectType, IProjectCreationCallback callback) {
        if (isSyncPossible()) {
            DBProject proj = new DBProject(0, remoteId, password, name, ihmUrl, email,
                    null, projectType, Long.valueOf(0), null);
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
        private IProjectCreationCallback callback;
        private List<Throwable> exceptions = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();
        private boolean usePrivateApi = false;

        public CreateRemoteProjectTask(DBProject project, IProjectCreationCallback callback) {
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
                ServerResponse.CreateRemoteProjectResponse response;
                // determine if we can create authenticated of just anonymous remote project
                if (canCreateAuthenticatedProject(project)) {
                    response = client.createAuthenticatedRemoteProject(customCertManager, project);
                    usePrivateApi = true;
                } else {
                    response = client.createAnonymousRemoteProject(customCertManager, project);
                }
                if (BillsListViewActivity.DEBUG) {
                    Log.i(getClass().getSimpleName(), "RESPONSE create remote project : " + response.getStringContent());
                }
            } catch (IOException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (TokenMismatchException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (NextcloudHttpRequestFailedException e) {
                if (BillsListViewActivity.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
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
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            } else {
                //dbHelper.deleteProject(project.getId());
            }
            callback.onFinish(project.getRemoteId(), errorString, usePrivateApi);
        }
    }

    private boolean hasChanged(DBBill localBill, DBBill remoteBill) {
        if (
                localBill.getPayerId() == remoteBill.getPayerId() &&
                        localBill.getAmount() == remoteBill.getAmount() &&
                        localBill.getTimestamp() == remoteBill.getTimestamp() &&
                        localBill.getWhat().equals(remoteBill.getWhat()) &&
                        localBill.getComment().equals(remoteBill.getComment()) &&
                        localBill.getPaymentMode().equals(remoteBill.getPaymentMode()) &&
                        localBill.getCategoryRemoteId() == remoteBill.getCategoryRemoteId()
        ) {
            String localRepeat = localBill.getRepeat() == null ? DBBill.NON_REPEATED : localBill.getRepeat();
            String remoteRepeat = remoteBill.getRepeat() == null ? DBBill.NON_REPEATED : remoteBill.getRepeat();

            return !localRepeat.equals(remoteRepeat);
        } else {
            return true;
        }
    }

    // update member avatar with Nextcloud user one
    public void updateMemberAvatar(long memberId) {
        updateNetworkStatus();
        if (isNextcloudAccountConfigured(appContext) && isSyncPossible()) {
            UpdateMemberAvatarTask updateMemberAvatarTask = new UpdateMemberAvatarTask(memberId);
            updateMemberAvatarTask.execute();
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
            // get NC color
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            boolean settingServerColor = preferences.getBoolean(appContext.getString(R.string.pref_key_use_server_color), false);
            if (settingServerColor) {
                GetNCColorTask getColorTask = new GetNCColorTask();
                getColorTask.execute();
            }
            GetNCUserAvatarTask getAvatarTask = new GetNCUserAvatarTask();
            getAvatarTask.execute();
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
                return new CospendClient(url, ssoAccount.userId, password, nextcloudAPI);
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
        private List<String> errorMessages = new ArrayList<>();

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
                    } catch (NextcloudFilesAppAccountNotFoundException e) {
                    } catch (NoCurrentAccountSelectedException e) {
                    }
                } else {
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
                        if (localProject.getRemoteId().equals(remoteAccountProject.getRemoteId())
                                && localProject.getServerUrl().replaceAll("/+$", "")
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
                                Long.valueOf(0),
                                null
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
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch REQ FAILED", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
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
                for (String errorMessage : errorMessages) {
                    errorString += errorMessage + "\n";
                }
                errorString += "\n";
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

    private class GetNCColorTask extends AsyncTask<Void, Void, LoginStatus> {

        private final List<ICallback> callbacks = new ArrayList<>();
        private CospendClient client;
        private List<Throwable> exceptions = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();

        public GetNCColorTask() {

        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createCospendClient(); // recreate CospendClients on every sync in case the connection settings was changed
            Log.i(getClass().getSimpleName(), "STARTING get color");

            LoginStatus status = LoginStatus.OK;

            if (client != null) {
                status = getNextcloudColor();
            }
            else {
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }

            Log.i(getClass().getSimpleName(), "Get color FINISHED");
            return status;
        }

        private LoginStatus getNextcloudColor() {
            Log.d(getClass().getSimpleName(), "getNextcloudColor()");
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            String lastETag = preferences.getString(SettingsActivity.SETTINGS_KEY_ETAG, null);
            long lastModified = preferences.getLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, 0);
            LoginStatus status;
            try {

                ServerResponse.CapabilitiesResponse response = client.getColor(customCertManager);
                String color = response.getColor();

                status = LoginStatus.OK;

                // update ETag and Last-Modified in order to reduce size of next response
                SharedPreferences.Editor editor = preferences.edit();

                if (color != null && !color.isEmpty() && color.startsWith("#")) {
                    if (color.length() == 4) {
                        color = "#" + color.charAt(1) + color.charAt(1)
                                + color.charAt(2) + color.charAt(2)
                                + color.charAt(3) + color.charAt(3);
                    }
                    int intColor = Color.parseColor(color);
                    Log.d(getClass().getSimpleName(), "COLOR from server is "+color);
                    editor.putInt(appContext.getString(R.string.pref_key_server_color), intColor);
                } else {
                    //editor.remove(SettingsActivity.SETTINGS_KEY_ETAG);
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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch REQ FAILED", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
            }

            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
        }
    }

    private class GetNCUserAvatarTask extends AsyncTask<Void, Void, LoginStatus> {

        private final List<ICallback> callbacks = new ArrayList<>();
        private CospendClient client;
        private List<Throwable> exceptions = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();

        public GetNCUserAvatarTask() {

        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createCospendClient();
            Log.i(getClass().getSimpleName(), "STARTING get account avatar");

            LoginStatus status = LoginStatus.OK;

            if (client != null) {
                status = getNextcloudUserAvatar();
            }
            else {
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }
            return status;
        }

        private LoginStatus getNextcloudUserAvatar() {
            Log.d(getClass().getSimpleName(), "getNextcloudUserAvatar()");
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            //String lastETag = preferences.getString(SettingsActivity.SETTINGS_KEY_ETAG, null);
            //long lastModified = preferences.getLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, 0);
            LoginStatus status;
            try {

                ServerResponse.AvatarResponse response = client.getAvatar(customCertManager, null);
                String avatar = response.getAvatarString();

                status = LoginStatus.OK;

                // update ETag and Last-Modified in order to reduce size of next response
                SharedPreferences.Editor editor = preferences.edit();

                if (avatar != null && !avatar.isEmpty()) {
                    //Log.d(getClass().getSimpleName(), "avatar from server is "+avatar);
                    editor.putString(appContext.getString(R.string.pref_key_avatar), avatar);
                } else {
                    //editor.remove(SettingsActivity.SETTINGS_KEY_ETAG);
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
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch REQ FAILED", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
            }

            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            if (status == LoginStatus.OK) {
                Intent intent = new Intent(BROADCAST_AVATAR_UPDATED);
                appContext.sendBroadcast(intent);
            }
        }
    }

    private class UpdateMemberAvatarTask extends AsyncTask<Void, Void, LoginStatus> {

        private final List<ICallback> callbacks = new ArrayList<>();
        private CospendClient client;
        private long memberId;
        private List<Throwable> exceptions = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();

        public UpdateMemberAvatarTask(long memberId) {
            this.memberId = memberId;
        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createCospendClient();
            Log.i(getClass().getSimpleName(), "STARTING get avatar for member");

            LoginStatus status = LoginStatus.OK;

            if (client != null) {
                status = getNextcloudUserAvatar();
            }
            else {
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }
            return status;
        }

        private LoginStatus getNextcloudUserAvatar() {
            Log.d(getClass().getSimpleName(), "getNextcloudUserAvatar() "+memberId);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            //String lastETag = preferences.getString(SettingsActivity.SETTINGS_KEY_ETAG, null);
            //long lastModified = preferences.getLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, 0);
            LoginStatus status = LoginStatus.OK;
            try {
                DBMember m = dbHelper.getMember(memberId);
                String targetUserName = m.getNcUserId();
                if (targetUserName != null && !targetUserName.equals("")) {
                    ServerResponse.AvatarResponse response = client.getAvatar(customCertManager, targetUserName);
                    String avatar = response.getAvatarString();

                    if (avatar != null && !avatar.isEmpty()) {
                        //Log.d(getClass().getSimpleName(), "avatar from server is "+avatar);
                        dbHelper.updateMember(
                                memberId, null, null, null,
                                null, null, null, null, null,
                                null, avatar
                        );
                        Log.d(getClass().getSimpleName(), "RECEIVED AVATAR for member "+memberId+" length "+avatar.length());
                    }
                }
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
            } catch (NextcloudHttpRequestFailedException e) {
                Log.e(getClass().getSimpleName(), "Catch REQ FAILED", e);
                status = LoginStatus.REQ_FAILED;
                errorMessages.add(getErrorMessageFromException(e));
            }

            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            if (status == LoginStatus.OK) {
                Intent intent = new Intent(BROADCAST_AVATAR_UPDATED);
                intent.putExtra(BROADCAST_AVATAR_UPDATED_MEMBER, memberId);
                appContext.sendBroadcast(intent);
            }
        }
    }
}
