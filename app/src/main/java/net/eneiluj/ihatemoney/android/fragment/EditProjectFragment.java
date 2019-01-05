package net.eneiluj.ihatemoney.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
//import android.support.v7.preference.PreferenceFragmentCompat;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import android.support.annotation.Nullable;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.model.DBProject;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;
import net.eneiluj.ihatemoney.util.ICallback;

public class EditProjectFragment extends PreferenceFragmentCompat {

    public interface EditProjectFragmentListener {
        void close();

        void closeOnDelete(long projId);

        void closeOnEdit(long projId);

        void onProjectUpdated(DBProject project);
    }

    public static final String PARAM_PROJECT_ID = "projectId";
    private static final String SAVEDKEY_PROJECT = "project";

    protected DBProject project;
    @Nullable
    protected IHateMoneySQLiteOpenHelper db;
    protected EditProjectFragmentListener listener;

    private Handler handler;

    protected EditTextPreference editProjectName;
    protected EditTextPreference editProjectPassword;
    protected EditTextPreference editProjectEmail;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    private Menu myMenu = null;

    public static EditProjectFragment newInstance(long projectId) {
        EditProjectFragment f = new EditProjectFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_PROJECT_ID, projectId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
        addPreferencesFromResource(R.xml.activity_edit_project);
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

        Preference namePref = findPreference("name");
        namePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("name");
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

        // delete confirmation
        deleteDialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        Animation animation1 =
                                AnimationUtils.loadAnimation(
                                        getActivity().getApplicationContext(),
                                        R.anim.rotation
                                );
                        ImageView saveButton = (ImageView) myMenu.findItem(R.id.menu_save).getActionView();
                        saveButton.startAnimation(animation1);
                        db.getIhateMoneyServerSyncHelper().deleteRemoteProject(project.getId(), deleteCallBack);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        //confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.Theme_AppCompat_DayNight_Dialog));

        confirmDeleteAlertBuilder.setMessage("Are you sure?").setPositiveButton("Yes", deleteDialogClickListener)
                .setNegativeButton("No", deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());

        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_PROJECT_ID);
            if (id > 0) {
                // TODO
                project = db.getProject(id);
            }
        } else {
            project = (DBProject) savedInstanceState.getSerializable(SAVEDKEY_PROJECT);
        }
        setHasOptionsMenu(true);

        System.out.println("PROJECT on create : "+project);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (EditProjectFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + EditProjectFragmentListener.class);
        }
        db = IHateMoneySQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        //listener.onProjectUpdated(project);
    }

    @Override
    public void onPause() {
        super.onPause();
        //saveBill(null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //saveBill(null);
        outState.putSerializable(SAVEDKEY_PROJECT, project);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_edit_project_fragment, menu);
        myMenu = menu;
        final ImageView saveButton = (ImageView) menu.findItem(R.id.menu_save).getActionView();
        saveButton.setImageResource(android.R.drawable.ic_menu_save);
        saveButton.setPadding(20,0,20,0);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Animation animation1 =
                        AnimationUtils.loadAnimation(
                                getActivity().getApplicationContext(),
                                R.anim.rotation
                        );
                saveButton.startAnimation(animation1);

                if (!isValidEmail(getEmail())) {
                    showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                }
                else {
                    //saveProject(null);
                    db.getIhateMoneyServerSyncHelper().editRemoteProject(project.getId(), getName(), getEmail(), getPassword(), editCallBack);
                }

            }
        });
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    public final static boolean isValidEmail(CharSequence target) {
        if (target == null)
            return false;

        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete_remote:
                confirmDeleteAlertBuilder.show();
                return true;
            case R.id.menu_save:
                if (!isValidEmail(getEmail())) {
                    showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                }
                else {
                    //saveProject(null);
                    db.getIhateMoneyServerSyncHelper().editRemoteProject(project.getId(), getName(), getEmail(), getPassword(), editCallBack);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCloseProject() {
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

        editProjectName = (EditTextPreference) this.findPreference("name");
        editProjectName.setText(project.getName());
        editProjectName.setSummary(project.getName());

        editProjectPassword = (EditTextPreference) this.findPreference("password");
        editProjectPassword.setText(project.getPassword());
        //editProjectPassword.setSummary(logjob.getUrl());

        editProjectEmail = (EditTextPreference) this.findPreference("email");
        editProjectEmail.setText(String.valueOf(project.getEmail()));
        editProjectEmail.setSummary(String.valueOf(project.getEmail()));
    }

    protected String getName() {
        return editProjectName.getText();
    }
    protected String getPassword() {
        return editProjectPassword.getText();
    }
    protected String getEmail() {
        return editProjectEmail.getText();
    }

    private ICallback editCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String result, String message) {
            if (message.isEmpty()) {
                listener.closeOnEdit(project.getId());
            }
            else {
                showToast(getString(R.string.error_share_dev_helper, message), Toast.LENGTH_LONG);
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    private ICallback deleteCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String result, String message) {
            if (message.isEmpty()) {
                listener.closeOnDelete(Long.valueOf(result));
            }
            else {
                showToast(getString(R.string.error_share_dev_helper, message), Toast.LENGTH_LONG);
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
