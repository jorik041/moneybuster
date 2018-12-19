package net.eneiluj.ihatemoney.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
//import android.preference.EditTextPreference;
import android.support.v7.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import android.support.v7.preference.Preference;
//import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.activity.SettingsActivity;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.DBSession;
import net.eneiluj.ihatemoney.persistence.SessionServerSyncHelper;
import net.eneiluj.ihatemoney.util.ICallback;

//public abstract class EditLogjobFragment extends Fragment implements CategoryDialogFragment.CategoryDialogListener {
//public class EditLogjobFragment extends PreferencesFragment {
public class EditPhoneTrackLogjobFragment extends EditLogjobFragment {

    private EditTextPreference editToken;
    private EditTextPreference editDevicename;

    private AlertDialog.Builder selectBuilder;
    private AlertDialog selectDialog;

    private AlertDialog.Builder fromUrlBuilder;
    private AlertDialog fromUrlDialog;
    private EditText fromUrlEdit;

    private List<DBSession> sessionList;
    private List<String> sessionNameList;
    private List<String> sessionIdList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_edit);

        endOnCreate();

        System.out.println("PHONEFRAG on create : "+logjob);

        Preference tokenPref = findPreference("token");
        tokenPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("token");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference devicenamePref = findPreference("devicename");
        devicenamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("devicename");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (db.getSessions().size() == 0) {
            MenuItem itemSelectSession = menu.findItem(R.id.menu_selectSession);
            itemSelectSession.setVisible(false);
        }
        menu.findItem(R.id.menu_share).setVisible(db.getIhateMoneyServerSyncHelper().isConfigured(getActivity()));
        menu.findItem(R.id.menu_share).setTitle(R.string.menu_share_dev_title);
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    @Override
    protected void saveLogjob(@Nullable ICallback callback) {
        Log.d(getClass().getSimpleName(), "saveData()");
        String newTitle = getTitle();
        String newUrl = getURL();
        String newToken = getToken();
        String newDevicename = getDevicename();
        int newMinTime = Integer.valueOf(getMintime());
        int newMinDistance = Integer.valueOf(getMindistance());
        int newMinAccuracy = Integer.valueOf(getMinaccuracy());
        if(logjob.getTitle().equals(newTitle) &&
                logjob.getUrl().equals(newUrl) &&
                logjob.getToken().equals(newToken) &&
                logjob.getMinTime() == newMinTime &&
                logjob.getMinDistance() == newMinDistance &&
                logjob.getMinAccuracy() == newMinAccuracy &&
                logjob.getDeviceName().equals(newDevicename)) {
            Log.v(getClass().getSimpleName(), "... not saving, since nothing has changed");
        } else {
            System.out.println("====== update logjob");
            logjob = db.updateLogjobAndSync(logjob, newTitle, newToken, newUrl, newDevicename, false, newMinTime, newMinDistance, newMinAccuracy, callback);
            listener.onLogjobUpdated(logjob);
        }
    }

    public static EditPhoneTrackLogjobFragment newInstance(long logjobId) {
        EditPhoneTrackLogjobFragment f = new EditPhoneTrackLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_LOGJOB_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditPhoneTrackLogjobFragment newInstanceWithNewLogjob(DBLogjob newLogjob) {
        EditPhoneTrackLogjobFragment f = new EditPhoneTrackLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWLOGJOB, newLogjob);
        f.setArguments(b);
        return f;
    }

    private ICallback shareCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String publicUrl, String message) {
            if (publicUrl != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_dev_title));
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, publicUrl);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_dev_title)));
            }
            else {
                showToast(getString(R.string.error_share_dev_helper, message), Toast.LENGTH_LONG);
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_fromLogUrl:
                fromUrlDialog.show();
                return true;
            case R.id.menu_selectSession:
                selectDialog.show();
                return true;
            case R.id.menu_share:
                String token = getToken();
                String devicename = getDevicename();
                String nextURL = getURL().replaceAll("/+$", "");
                SessionServerSyncHelper syncHelper = db.getIhateMoneyServerSyncHelper();
                if (syncHelper.isConfigured(this.getActivity())) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    String configUrl = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS)
                            .replaceAll("/+$", "");
                    if (nextURL.equals(configUrl)) {
                        if (!syncHelper.shareDevice(token, devicename, shareCallBack)) {
                            showToast(getString(R.string.error_share_dev_network), Toast.LENGTH_LONG);
                        }
                    }
                    else {
                        Log.d(getClass().getSimpleName(), "NOT THE SAME NEXTCLOUD URL");
                        showToast(getString(R.string.error_share_dev_same_url), Toast.LENGTH_LONG);
                    }
                }
                else {
                    showToast(getString(R.string.error_share_dev_configured), Toast.LENGTH_LONG);
                }

                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("PHONETRACK ACT CREATEDDDDDDD");

        editToken = (EditTextPreference) this.findPreference("token");
        editToken.setText(logjob.getToken());
        editToken.setSummary(logjob.getToken());
        editDevicename = (EditTextPreference) this.findPreference("devicename");
        editDevicename.setText(logjob.getDeviceName());
        editDevicename.setSummary(logjob.getDeviceName());

        // manage session list
        sessionList = db.getSessions();
        sessionNameList = new ArrayList<>();
        sessionIdList = new ArrayList<>();
        for (DBSession session : sessionList) {
            sessionNameList.add(session.getName());
            sessionIdList.add(String.valueOf(session.getId()));
        }

        // manage session list DIALOG
        selectBuilder = new AlertDialog.Builder(getContext());
        selectBuilder.setTitle("Choose a session");

        if (sessionNameList.size() > 0) {
            CharSequence[] entcs = sessionNameList.toArray(new CharSequence[sessionNameList.size()]);
            selectBuilder.setSingleChoiceItems(entcs, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user checked an item
                    System.out.println("CHECKED :" + which);
                    setFieldsFromSession(sessionList.get(which));
                    saveLogjob(null);
                    dialog.dismiss();
                }
            });

            // add OK and Cancel buttons
            selectBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user clicked OK
                    System.out.println("CHECKED OK :" + which);
                }
            });
            selectBuilder.setNegativeButton("Cancel", null);

            // create the alert dialog
            System.out.println("I SET THE DIALOGGGGGGGG");
            selectDialog = selectBuilder.create();
        }

        // manage from URL DIALOG
        fromUrlEdit = new EditText(getContext());
        fromUrlBuilder = new AlertDialog.Builder(getContext());
        fromUrlBuilder.setMessage(getString(R.string.dialog_msg_import_pt_url));
        fromUrlBuilder.setTitle(getString(R.string.dialog_title_import_pt_url));

        fromUrlBuilder.setView(fromUrlEdit);

        fromUrlBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                setFieldsFromPhoneTrackLoggingUrl(fromUrlEdit.getText().toString());
                saveLogjob(null);
            }
        });

        fromUrlBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        // create the alert dialog
        fromUrlDialog = fromUrlBuilder.create();
    }

    private String getToken() {
        return editToken.getText();
    }
    private String getDevicename() {
        return editDevicename.getText();
    }

    private void setFieldsFromSession(DBSession s) {
        editTitle.setText("Log to "+s.getName());
        editTitle.setSummary("Log to "+s.getName());
        editURL.setText(s.getNextURL());
        editURL.setSummary(s.getNextURL());
        editToken.setText(s.getToken());
        editToken.setSummary(s.getToken());
    }

    private void setFieldsFromPhoneTrackLoggingUrl(String url) {
        String[] spl = url.split("/apps/phonetrack/");
        if (spl.length == 2) {
            String nextURL = spl[0];
            if (nextURL.contains("index.php")) {
                nextURL = nextURL.replace("index.php", "");
            }

            String right = spl[1];
            String[] spl2 = right.split("/");
            if (spl2.length > 2) {
                String token;
                String[] splEnd;
                // example .../apps/ihatemoney/logGet/token/devname?lat=0.1...
                if (spl2.length == 3) {
                    token = spl2[1];
                    splEnd = spl2[2].split("\\?");
                }
                // example .../apps/ihatemoney/log/osmand/token/devname?lat=0.1...
                else {
                    token = spl2[2];
                    splEnd = spl2[3].split("\\?");
                }
                String devname = splEnd[0];
                editTitle.setText("From IHateMoney logging URL");
                editTitle.setSummary("From IHateMoney logging URL");
                editDevicename.setText(devname);
                editDevicename.setSummary(devname);
                editToken.setText(token);
                editToken.setSummary(token);
                editURL.setText(nextURL);
                editURL.setSummary(nextURL);
            }
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

}
