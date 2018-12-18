package net.eneiluj.ihatemoney.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import android.support.v7.preference.Preference;
//import android.preference.PreferenceFragment;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.activity.BillsListViewActivity;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.DBProject;
import net.eneiluj.ihatemoney.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.ihatemoney.service.LoggerService;
import net.eneiluj.ihatemoney.util.ICallback;

public class EditProjectFragment extends PreferenceFragmentCompat {

    public interface ProjectFragmentListener {
        void close();

        void onProjectUpdated(DBLogjob logjob);
    }

    public static final String PARAM_PROJECT_ID = "projectId";
    public static final String PARAM_NEWPROJECT = "newProject";
    private static final String SAVEDKEY_PROJECT = "project";
    private static final String SAVEDKEY_ORIGINAL_PROJECT = "original_project";

    protected DBProject project;
    @Nullable
    protected DBProject originalProject;
    protected PhoneTrackSQLiteOpenHelper db;
    protected ProjectFragmentListener listener;

    private Handler handler;

    protected EditTextPreference editProjectId;
    protected EditTextPreference editProjectIHMUrl;
    protected EditTextPreference editProjectPassword;
    protected CheckBoxPreference editProjectCreate;
    protected EditTextPreference editProjectEmail;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    public static EditProjectFragment newInstance(long projectId) {
        EditProjectFragment f = new EditProjectFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_PROJECT_ID, projectId);
        f.setArguments(b);
        return f;
    }

    public static EditProjectFragment newInstanceWithNewProject(DBProject newProject) {
        EditProjectFragment f = new EditProjectFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWPROJECT, newProject);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootkey) {

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_edit_project);

        Preference idPref = findPreference("id");
        idPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("id");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference URLPref = findPreference("url");
        URLPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("url");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference passwordPref = findPreference("password");
        passwordPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("password");
                //pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference emailPref = findPreference("email");
        emailPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) preference;
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });

        Preference createPref = findPreference("createonserver");
        createPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                CheckBoxPreference pref = (CheckBoxPreference) findPreference("createonserver");
                EditTextPreference emailPref = (EditTextPreference) findPreference("email");
                emailPref.setVisible((Boolean) newValue);
                //pref.setChecked((Boolean) newValue);
                return true;
            }

        });

        // delete confirmation
        deleteDialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked
                        db.deleteProject(project.getId());
                        listener.close();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder.setMessage("Are you sure?").setPositiveButton("Yes", deleteDialogClickListener)
                .setNegativeButton("No", deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());

        System.out.println("CUSTOM on create : "+logjob);



        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_LOGJOB_ID);
            if (id > 0) {
                logjob = originalLogjob = db.getLogjob(id);
            } else {
                DBLogjob cloudLogjob = (DBLogjob) getArguments().getSerializable(PARAM_NEWLOGJOB);
                if (cloudLogjob == null) {
                    throw new IllegalArgumentException(PARAM_LOGJOB_ID + " is not given and argument " + PARAM_NEWLOGJOB + " is missing.");
                }
                logjob = db.getLogjob(db.addLogjob(cloudLogjob));
                originalLogjob = null;
            }
        } else {
            logjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_LOGJOB);
            originalLogjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_ORIGINAL_LOGJOB);
        }
        setHasOptionsMenu(true);
        System.out.println("SUPERCLASS on create : " + logjob);

        ///////////////
        //addPreferencesFromResource(R.xml.activity_edit);

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (LogjobFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + LogjobFragmentListener.class);
        }
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        listener.onLogjobUpdated(logjob);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveLogjob(null);
        notifyLoggerService(logjob.getId());
    }

    private void notifyLoggerService(long jobId) {
        Intent intent = new Intent(getActivity(), LoggerService.class);
        intent.putExtra(BillsListViewActivity.UPDATED_LOGJOBS, true);
        intent.putExtra(BillsListViewActivity.UPDATED_LOGJOB_ID, jobId);
        getActivity().startService(intent);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveLogjob(null);
        outState.putSerializable(SAVEDKEY_LOGJOB, logjob);
        outState.putSerializable(SAVEDKEY_ORIGINAL_LOGJOB, originalLogjob);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logjob_fragment, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_share).setVisible(false);

        MenuItem itemSelectSession = menu.findItem(R.id.menu_selectSession);
        itemSelectSession.setVisible(false);
        MenuItem itemFromLogUrl = menu.findItem(R.id.menu_fromLogUrl);
        itemFromLogUrl.setVisible(false);
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_cancel:
                if (originalLogjob == null) {
                    db.deleteLogjob(logjob.getId());
                } else {
                    System.out.println("ORIG ENAB : "+originalLogjob.isEnabled());
                    db.updateLogjobAndSync(originalLogjob, null, null, null, null, false,0,0,0,null);
                }
                listener.close();
                return true;
            case R.id.menu_delete:
                confirmDeleteAlertBuilder.show();
                return true;
            case R.id.menu_share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, logjob.getTitle());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, logjob.getUrl());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(Intent.createChooser(shareIntent, logjob.getTitle()));
                } else {
                    ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
                    actionProvider.setShareIntent(shareIntent);
                }

                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCloseLogjob() {
        if (originalLogjob == null && getTitle().isEmpty()) {
            db.deleteLogjob(logjob.getId());
        }
        Log.d(getClass().getSimpleName(), "onCLOSE()");
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    protected void saveProject(@Nullable ICallback callback) {

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("ACT CREATEDDDDDDD");
        ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        editTitle = (EditTextPreference) this.findPreference("title");
        editTitle.setText(logjob.getTitle());
        if (logjob.getTitle().isEmpty()) {
            editTitle.setSummary(getString(R.string.mandatory));
        }
        else {
            editTitle.setSummary(logjob.getTitle());
        }
        editURL = (EditTextPreference) this.findPreference("URL");
        editURL.setText(logjob.getUrl());
        editURL.setSummary(logjob.getUrl());

        editMintime = (EditTextPreference) this.findPreference("mintime");
        editMintime.setText(String.valueOf(logjob.getMinTime()));
        editMintime.setSummary(String.valueOf(logjob.getMinTime()));

        editMindistance = (EditTextPreference) this.findPreference("mindistance");
        editMindistance.setText(String.valueOf(logjob.getMinDistance()));
        editMindistance.setSummary(String.valueOf(logjob.getMinDistance()));

        editMinaccuracy = (EditTextPreference) this.findPreference("minaccuracy");
        editMinaccuracy.setText(String.valueOf(logjob.getMinAccuracy()));
        editMinaccuracy.setSummary(String.valueOf(logjob.getMinAccuracy()));

        editPost = (CheckBoxPreference) this.findPreference("post");
        editPost.setChecked(logjob.getPost());
    }

    protected String getTitle() {
        return editTitle.getText();
    }
    protected String getURL() {
        return editURL.getText();
    }
    protected String getMintime() {
        return editMintime.getText();
    }
    protected String getMindistance() {
        return editMindistance.getText();
    }
    protected String getMinaccuracy() {
        return editMinaccuracy.getText();
    }

}
