package net.eneiluj.moneybuster.android.fragment;

//import android.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.SupportUtil;

//import android.support.v7.preference.PreferenceFragmentCompat;

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
    protected MoneyBusterSQLiteOpenHelper db;
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

    private void hideKeyboard(Context context) {
        // hide keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preference.OnPreferenceClickListener clickListener =  new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                EditText input = ((com.takisoft.fix.support.v7.preference.EditTextPreference) preference).getEditText();
                input.setSelectAllOnFocus(true);
                input.requestFocus();
                input.setSelected(true);
                // show keyboard
                InputMethodManager inputMethodManager = (InputMethodManager) preference.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                return true;
            }
        };

        Preference namePref = findPreference("name");
        namePref.setOnPreferenceClickListener(clickListener);
        namePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("name");
                pref.setSummary((CharSequence) newValue);
                hideKeyboard(preference.getContext());
                return true;
            }
        });
        Preference passwordPref = findPreference("password");
        passwordPref.setOnPreferenceClickListener(clickListener);
        passwordPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("password");
                int nbChars = ((CharSequence)newValue).length();
                String sum = "";
                for (int i=0; i < nbChars; i++) {
                    sum += "*";
                }
                pref.setSummary(sum);
                hideKeyboard(preference.getContext());
                return true;
            }

        });
        Preference emailPref = findPreference("email");
        emailPref.setOnPreferenceClickListener(clickListener);
        emailPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) preference;
                pref.setSummary((CharSequence) newValue);
                hideKeyboard(preference.getContext());
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
                        if (!db.getMoneyBusterServerSyncHelper().deleteRemoteProject(project.getId(), deleteCallBack)) {
                            showToast(getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG);
                            saveButton.clearAnimation();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        //confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.AppThemeDialog));

        confirmDeleteAlertBuilder.setMessage(getString(R.string.confirm_delete_project_dialog_title))
                .setPositiveButton(getString(R.string.simple_yes), deleteDialogClickListener)
                .setNegativeButton(getString(R.string.simple_no), deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());

        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_PROJECT_ID);
            if (id > 0) {
                project = db.getProject(id);
            }
        } else {
            project = (DBProject) savedInstanceState.getSerializable(SAVEDKEY_PROJECT);
        }
        setHasOptionsMenu(true);

        Log.d(getClass().getSimpleName(), "PROJECT on create : "+project);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (EditProjectFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + EditProjectFragmentListener.class);
        }
        db = MoneyBusterSQLiteOpenHelper.getInstance(context);
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
        saveButton.setImageResource(R.drawable.ic_check_white_24dp);
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

                String pwd = getPassword();
                if (pwd == null || pwd.equals("")) {
                    showToast(getString(R.string.error_invalid_project_password), Toast.LENGTH_LONG);
                    saveButton.clearAnimation();
                    return;
                }
                String name = getName();
                if (name == null || name.equals("")) {
                    showToast(getString(R.string.error_invalid_project_name), Toast.LENGTH_LONG);
                    saveButton.clearAnimation();
                    return;
                }
                if (!SupportUtil.isValidEmail(getEmail())) {
                    showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                    saveButton.clearAnimation();
                    return;
                }

                if (!db.getMoneyBusterServerSyncHelper().editRemoteProject(project.getId(), getName(), getEmail(), getPassword(), editCallBack)) {
                    showToast(getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG);
                    saveButton.clearAnimation();
                }



            }
        });
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
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
                if (!SupportUtil.isValidEmail(getEmail())) {
                    showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                }
                else {
                    //saveProject(null);
                    db.getMoneyBusterServerSyncHelper().editRemoteProject(project.getId(), getName(), getEmail(), getPassword(), editCallBack);
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
        //ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        editProjectName = (EditTextPreference) this.findPreference("name");
        editProjectName.setText(project.getName());
        editProjectName.setSummary(project.getName());

        editProjectPassword = (EditTextPreference) this.findPreference("password");
        editProjectPassword.setText(project.getPassword());
        int nbChars = project.getPassword().length();
        String sum = "";
        for (int i=0; i < nbChars; i++) {
            sum += "*";
        }
        editProjectPassword.setSummary(sum);

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
                showToast(getString(R.string.error_edit_remote_project_helper, message), Toast.LENGTH_LONG);
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
                showToast(getString(R.string.error_edit_remote_project_helper, message), Toast.LENGTH_LONG);
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
