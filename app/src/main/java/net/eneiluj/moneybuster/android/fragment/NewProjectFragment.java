package net.eneiluj.moneybuster.android.fragment;

import android.content.Context;
import android.content.SharedPreferences;
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
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.takisoft.fix.support.v7.preference.EditTextPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.SupportUtil;

import java.util.ArrayList;
import java.util.List;

import static android.webkit.URLUtil.isValidUrl;

public class NewProjectFragment extends PreferenceFragmentCompat {
    private static final String TAG = NewProjectFragment.class.getSimpleName();

    private static final String SAVEDKEY_PROJECT = "project";
    public static final String PARAM_DEFAULT_IHM_URL = "defaultIhmUrl";
    public static final String PARAM_DEFAULT_NC_URL = "defaultNcUrl";
    public static final String PARAM_DEFAULT_PROJECT_ID = "defaultProjectId";
    public static final String PARAM_DEFAULT_PROJECT_PASSWORD = "defaultProjectPassword";
    public static final String PARAM_DEFAULT_PROJECT_TYPE = "defaultProjectType";

    public interface NewProjectFragmentListener {
        void close(long pid);
    }

    @Nullable
    protected MoneyBusterSQLiteOpenHelper db;
    protected NewProjectFragmentListener listener;

    private Handler handler;

    protected ListPreference newProjectType;
    protected EditTextPreference newProjectId;
    protected EditTextPreference newProjectIHMUrl;
    protected EditTextPreference newProjectPassword;
    protected CheckBoxPreference newProjectCreate;
    protected EditTextPreference newProjectEmail;
    protected EditTextPreference newProjectName;

    protected String defaultIhmUrl;
    protected String defaultNcUrl;

    public static NewProjectFragment newInstance(String defaultIhmUrl, String defaultNCUrl,
                                                 @Nullable String defaultProjectId,
                                                 @Nullable String defaultProjectPassword,
                                                 ProjectType defaultProjectType) {
        NewProjectFragment f = new NewProjectFragment();
        Bundle b = new Bundle();
        b.putString(PARAM_DEFAULT_IHM_URL, defaultIhmUrl);
        b.putString(PARAM_DEFAULT_NC_URL, defaultNCUrl);
        b.putString(PARAM_DEFAULT_PROJECT_ID, defaultProjectId);
        b.putString(PARAM_DEFAULT_PROJECT_PASSWORD, defaultProjectPassword);
        b.putString(PARAM_DEFAULT_PROJECT_TYPE, defaultProjectType.getId());
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
        addPreferencesFromResource(R.xml.activity_new_project);
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

        Preference typePref = findPreference("type");
        typePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                ListPreference pref = (ListPreference) findPreference("type");
                int index = pref.findIndexOfValue((String)newValue);
                Log.d(TAG, index + " ----> " + newValue);
                preference.setSummary(pref.getEntries()[index]);

                EditTextPreference urlPref = (EditTextPreference) findPreference("url");
                EditTextPreference passwordPref = (EditTextPreference) findPreference("password");
                CheckBoxPreference createPref = (CheckBoxPreference) findPreference("createonserver");
                EditTextPreference emailPref = (EditTextPreference) findPreference("email");
                EditTextPreference namePref = (EditTextPreference) findPreference("name");

                urlPref.setVisible(!newValue.equals(ProjectType.LOCAL.getId()));
                passwordPref.setVisible(!newValue.equals(ProjectType.LOCAL.getId()));
                createPref.setVisible(!newValue.equals(ProjectType.LOCAL.getId()));
                if (newValue.equals(ProjectType.LOCAL.getId())) {
                    newProjectType.setIcon(R.drawable.ic_cellphone_grey_24dp);
                    createPref.setChecked(false);
                    emailPref.setVisible(false);
                    namePref.setVisible(false);
                }
                else if (newValue.equals(ProjectType.IHATEMONEY.getId())) {
                    newProjectType.setIcon(R.drawable.ic_ihm_grey_24dp);
                    urlPref.setTitle(getString(R.string.setting_ihm_project_url));
                    urlPref.setDialogTitle(getString(R.string.setting_ihm_project_url));
                    urlPref.setDialogMessage(getString(R.string.setting_ihm_project_url_long));

                    newProjectIHMUrl.setText(defaultIhmUrl);
                    newProjectIHMUrl.setSummary(defaultIhmUrl);
                }
                else if (newValue.equals(ProjectType.COSPEND.getId())) {
                    newProjectType.setIcon(R.drawable.ic_cospend_grey_24dp);
                    urlPref.setTitle(getString(R.string.setting_cospend_project_url));
                    urlPref.setDialogTitle(getString(R.string.setting_cospend_project_url));
                    urlPref.setDialogMessage(getString(R.string.setting_cospend_project_url_long));

                    newProjectIHMUrl.setText(defaultNcUrl);
                    newProjectIHMUrl.setSummary(defaultNcUrl);
                }
                return true;
            }

        });

        Preference.OnPreferenceClickListener clickListener =  new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                EditText input = ((EditTextPreference) preference).getEditText();
                input.setSelectAllOnFocus(true);
                input.requestFocus();
                input.setSelected(true);
                // show keyboard
                InputMethodManager inputMethodManager = (InputMethodManager) preference.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                return true;
            }
        };

        Preference idPref = findPreference("id");
        idPref.setOnPreferenceClickListener(clickListener);
        idPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("id");
                pref.setSummary((CharSequence) newValue);
                hideKeyboard(preference.getContext());
                return true;
            }

        });
        Preference URLPref = findPreference("url");
        URLPref.setOnPreferenceClickListener(clickListener);
        URLPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("url");
                pref.setSummary((CharSequence) newValue);
                hideKeyboard(preference.getContext());
                return true;
            }

        });
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

        Preference createPref = findPreference("createonserver");
        createPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                CheckBoxPreference pref = (CheckBoxPreference) findPreference("createonserver");
                EditTextPreference emailPref = (EditTextPreference) findPreference("email");
                emailPref.setVisible((Boolean) newValue);
                EditTextPreference namePref = (EditTextPreference) findPreference("name");
                namePref.setVisible((Boolean) newValue);
                return true;
            }

        });

        handler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "PROJECT on create : ");

        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (NewProjectFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + NewProjectFragmentListener.class);
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
        Log.d(TAG, "PROJECT SAVE INSTANCE STATE");
        //saveBill(null);
        //outState.putSerializable(SAVEDKEY_PROJECT, project);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_new_project_fragment, menu);
        //ImageView addButton = getActivity().findViewById(R.id.menu_create);
        final ImageView addButton = (ImageView) menu.findItem(R.id.menu_create).getActionView();
        addButton.setImageResource(R.drawable.ic_add_circle_white_24dp);
        addButton.setPadding(40,0,40,0);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Animation animation1 =
                        AnimationUtils.loadAnimation(
                                getActivity().getApplicationContext(),
                                R.anim.rotation
                        );
                addButton.startAnimation(animation1);

                String rid = getRemoteId();
                if (rid == null || rid.equals("")) {
                    showToast(getString(R.string.error_invalid_project_remote_id), Toast.LENGTH_LONG);
                    addButton.clearAnimation();
                    return;
                }

                ProjectType type = getProjectType();

                if (!ProjectType.LOCAL.equals(type)) {
                    // check values
                    String url = getIhmUrl();
                    if (!isValidUrl(url)) {
                        showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
                        addButton.clearAnimation();
                        return;
                    }
                    String pwd = getPassword();
                    if (url != null && !url.equals("") && (pwd == null || pwd.equals(""))) {
                        showToast(getString(R.string.error_invalid_project_password), Toast.LENGTH_LONG);
                        addButton.clearAnimation();
                        return;
                    }
                }

                // do not create remote : quit immediately
                if (!newProjectCreate.isChecked()) {
                    long pid = saveProject(null);
                    listener.close(pid);
                }
                // create remote project (we know the type is not local)
                // the callback will quit this activity
                else {
                    String name = getName();
                    if (name == null || name.equals("")) {
                        showToast(getString(R.string.error_invalid_project_name), Toast.LENGTH_LONG);
                        addButton.clearAnimation();
                        return;
                    }
                    if (!SupportUtil.isValidEmail(getEmail())) {
                        showToast(getString(R.string.error_invalid_email), Toast.LENGTH_LONG);
                        addButton.clearAnimation();
                        return;
                    }
                    if (!db.getMoneyBusterServerSyncHelper().createRemoteProject(getRemoteId(), getName(), getEmail(), getPassword(), getIhmUrl(), getProjectType(), createRemoteCallBack)) {
                        showToast(getString(R.string.remote_project_operation_no_network), Toast.LENGTH_LONG);
                        addButton.clearAnimation();
                    }
                }

            }
        });

    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //menu.findItem(R.id.menu_delete_remote).setVisible(false);
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_create:
                // remote project should already exists, just add it locally
                /*if (!newProjectCreate.isChecked()) {
                    long pid = saveProject(null);
                    listener.close(pid);
                }
                else {
                    db.getMoneyBusterServerSyncHelper().createRemoteProject(getRemoteId(), getName(), getEmail(), getPassword(), getIhmUrl(), createRemoteCallBack);
                }*/
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
    protected long saveProject(@Nullable ICallback callback) {
        ProjectType type = getProjectType();
        String remoteId = getRemoteId();
        String ihmUrl = null;
        String password = null;
        String email = null;
        String name = null;
        if (!type.equals(ProjectType.LOCAL)) {
            ihmUrl = getIhmUrl();
            password = getPassword();
            email = getEmail();
            name = getName();
        } else {

        }

        DBProject newProject = new DBProject(0, remoteId, password, name, ihmUrl, email, null, type);
        long pid = db.addProject(newProject);

        // to make it the selected project even if we got here because of a VIEW intent
        // this is supposed to be done in activity result in billListViewActivity
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        preferences.edit().putLong("selected_project", pid).apply();

        showToast(getString(R.string.project_added_success), Toast.LENGTH_LONG);

        Log.i(TAG, "PROJECT local id : "+pid+" : "+newProject);
        return pid;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "ACT CREATED");
        //ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        newProjectType = (ListPreference) this.findPreference("type");
        List<String> types = new ArrayList<>();
        types.add(getString(R.string.project_type_local));
        types.add(getString(R.string.project_type_ihatemoney));
        types.add(getString(R.string.project_type_nextcloud_cospend));
        CharSequence[] typesArray = types.toArray(new CharSequence[types.size()]);
        newProjectType.setEntries(typesArray);

        List<String> typeValues = new ArrayList<>();
        typeValues.add(ProjectType.LOCAL.getId());
        typeValues.add(ProjectType.IHATEMONEY.getId());
        typeValues.add(ProjectType.COSPEND.getId());
        CharSequence[] typeValuesArray = typeValues.toArray(new CharSequence[typeValues.size()]);
        newProjectType.setEntryValues(typeValuesArray);

        newProjectEmail = (EditTextPreference) this.findPreference("email");
        newProjectName = (EditTextPreference) this.findPreference("name");
        newProjectId = (EditTextPreference) this.findPreference("id");
        newProjectPassword = (EditTextPreference) this.findPreference("password");
        newProjectIHMUrl = (EditTextPreference) this.findPreference("url");

        defaultIhmUrl = getArguments().getString(PARAM_DEFAULT_IHM_URL);
        defaultNcUrl = getArguments().getString(PARAM_DEFAULT_NC_URL);

        newProjectType.setValue(getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE));
        if (ProjectType.LOCAL.getId().equals(getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE))) {
            newProjectType.setSummary(getString(R.string.project_type_local));
            newProjectType.setIcon(R.drawable.ic_cellphone_grey_24dp);
        }
        if (ProjectType.IHATEMONEY.getId().equals(getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE))) {
            newProjectType.setSummary(getString(R.string.project_type_ihatemoney));
            newProjectType.setIcon(R.drawable.ic_ihm_grey_24dp);
            newProjectIHMUrl.setText(getArguments().getString(PARAM_DEFAULT_IHM_URL));
            newProjectIHMUrl.setSummary(getArguments().getString(PARAM_DEFAULT_IHM_URL));

            newProjectIHMUrl.setTitle(getString(R.string.setting_ihm_project_url));
            newProjectIHMUrl.setDialogTitle(getString(R.string.setting_ihm_project_url));
            newProjectIHMUrl.setDialogMessage(getString(R.string.setting_ihm_project_url_long));
        }
        if (ProjectType.COSPEND.getId().equals(getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE))) {
            newProjectType.setSummary(getString(R.string.project_type_nextcloud_cospend));
            newProjectType.setIcon(R.drawable.ic_cospend_grey_24dp);
            newProjectIHMUrl.setText(getArguments().getString(PARAM_DEFAULT_NC_URL));
            newProjectIHMUrl.setSummary(getArguments().getString(PARAM_DEFAULT_NC_URL));

            newProjectIHMUrl.setTitle(getString(R.string.setting_cospend_project_url));
            newProjectIHMUrl.setDialogTitle(getString(R.string.setting_cospend_project_url));
            newProjectIHMUrl.setDialogMessage(getString(R.string.setting_cospend_project_url_long));
        }

        newProjectId.setText(getArguments().getString(PARAM_DEFAULT_PROJECT_ID));
        newProjectId.setSummary(getArguments().getString(PARAM_DEFAULT_PROJECT_ID));

        String defaultPassword = getArguments().getString(PARAM_DEFAULT_PROJECT_PASSWORD);
        if (defaultPassword != null) {
            newProjectPassword.setText(getArguments().getString(PARAM_DEFAULT_PROJECT_PASSWORD));
            int nbChars = ((CharSequence) getArguments().getString(PARAM_DEFAULT_PROJECT_PASSWORD)).length();
            String sum = "";
            for (int i = 0; i < nbChars; i++) {
                sum += "*";
            }
            newProjectPassword.setSummary(sum);
        }

        newProjectCreate = (CheckBoxPreference) this.findPreference("createonserver");
        newProjectCreate.setChecked(false);

        if (ProjectType.LOCAL.getId().equals(getArguments().getString(PARAM_DEFAULT_PROJECT_TYPE))) {
            newProjectIHMUrl.setVisible(false);
            newProjectPassword.setVisible(false);
            newProjectCreate.setVisible(false);
        }
        newProjectEmail.setVisible(false);
        newProjectName.setVisible(false);
    }

    protected ProjectType getProjectType() {
        return ProjectType.getTypeById(newProjectType.getValue());
    }
    protected String getRemoteId() {
        return newProjectId.getText();
    }
    protected String getIhmUrl() {
        String url = newProjectIHMUrl.getText();
        ProjectType type = getProjectType();
        if (ProjectType.COSPEND.equals(type)) {
            url = url.replaceAll("/+$", "") + "/index.php/apps/cospend";
        }
        return url;
    }
    protected String getPassword() {
        return newProjectPassword.getText();
    }
    protected boolean getCreateRemote() {
        return newProjectCreate.isChecked();
    }
    protected String getName() {
        return newProjectName.getText();
    }
    protected String getEmail() {
        return newProjectEmail.getText();
    }

    private ICallback createRemoteCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String result, String message) {
            if (message.isEmpty()) {
                long pid = saveProject(null);
                listener.close(pid);
            }
            else {
                showToast(getString(R.string.error_create_remote_project_helper, message), Toast.LENGTH_LONG);
                // stop animation
                ImageView addButton = getActivity().findViewById(R.id.menu_create);
                addButton.clearAnimation();
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
