package net.eneiluj.ihatemoney.android.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import at.bitfire.cert4android.CustomCertManager;
import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;
import net.eneiluj.ihatemoney.persistence.SessionServerSyncHelper;
import net.eneiluj.ihatemoney.util.PhoneTrackClientUtil;
import net.eneiluj.ihatemoney.util.PhoneTrackClientUtil.LoginStatus;

/**
 * Allows to set Settings like URL, Username and Password for Server-Synchronization
 * Created by stefan on 22.09.15.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String SETTINGS_URL = "settingsUrl";
    public static final String SETTINGS_USERNAME = "settingsUsername";
    public static final String SETTINGS_PASSWORD = "settingsPassword";
    public static final String SETTINGS_KEY_ETAG = "sessions_last_etag";
    public static final String SETTINGS_KEY_LAST_MODIFIED = "sessions_last_modified";
    public static final String DEFAULT_SETTINGS = "";
    public static final int CREDENTIALS_CHANGED = 3;

    private SharedPreferences preferences = null;

    @BindView(R.id.settings_url)
    EditText field_url;
    @BindView(R.id.settings_username)
    EditText field_username;
    @BindView(R.id.settings_password)
    EditText field_password;
    @BindView(R.id.settings_password_wrapper)
    TextInputLayout password_wrapper;
    @BindView(R.id.settings_submit)
    Button btn_submit;
    @BindView(R.id.settings_url_warn_http)
    View urlWarnHttp;
    private String old_password = "";

    private boolean first_run = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ButterKnife.bind(this);

        preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        if (!SessionServerSyncHelper.isConfigured(this)) {
            first_run = true;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }

        field_url.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                new URLValidatorAsyncTask().execute(PhoneTrackClientUtil.formatURL(field_url.getText().toString()));
            }
        });

        field_url.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String url = PhoneTrackClientUtil.formatURL(field_url.getText().toString());

                if (PhoneTrackClientUtil.isHttp(url)) {
                    urlWarnHttp.setVisibility(View.VISIBLE);
                } else {
                    urlWarnHttp.setVisibility(View.GONE);
                }

                handleSubmitButtonEnabled(field_url.getText(), field_username.getText());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        field_username.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleSubmitButtonEnabled(field_url.getText(), field_username.getText());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // Load current Preferences
        field_url.setText(preferences.getString(SETTINGS_URL, DEFAULT_SETTINGS));
        field_username.setText(preferences.getString(SETTINGS_USERNAME, DEFAULT_SETTINGS));
        old_password = preferences.getString(SETTINGS_PASSWORD, DEFAULT_SETTINGS);

        field_password.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                login();
                return true;
            }
        });
        field_password.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setPasswordHint(hasFocus);
            }
        });
        setPasswordHint(false);

        btn_submit.setEnabled(false);
        btn_submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
    }

    private void setPasswordHint(boolean hasFocus) {
        boolean unchangedHint = !hasFocus && field_password.getText().toString().isEmpty() && !old_password.isEmpty();
        password_wrapper.setHint(getString(unchangedHint ? R.string.settings_password_unchanged : R.string.settings_password));
    }


    @Override
    protected void onResume() {
        super.onResume();

        /*if ((first_run) && (SessionServerSyncHelper.isConfigured(this))) {
            finish();
        }*/
    }

    /**
     * Prevent pressing back button on first run
     */
    @Override
    public void onBackPressed() {
        //if (!first_run) {
            super.onBackPressed();
        //}
    }

    private void login() {
        String url = field_url.getText().toString().trim();
        String username = field_username.getText().toString();
        String password = field_password.getText().toString();

        if (password.isEmpty()) {
            password = old_password;
        }

        url = PhoneTrackClientUtil.formatURL(url);

        new LoginValidatorAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, username, password);
    }

    private void handleSubmitButtonEnabled(Editable url, Editable username) {
        if (field_username.getText().length() > 0 && field_url.getText().length() > 0) {
            btn_submit.setEnabled(true);
        } else {
            btn_submit.setEnabled(false);
        }
    }

    /************************************ Async Tasks ************************************/

    /**
     * Checks if the given URL returns a valid status code and sets the Check next to the URL-Input Field to visible.
     * Created by stefan on 23.09.15.
     */
    private class URLValidatorAsyncTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            field_url.setCompoundDrawables(null, null, null, null);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            CustomCertManager ccm = SessionServerSyncHelper.getInstance(IHateMoneySQLiteOpenHelper.getInstance(getApplicationContext())).getCustomCertManager();
            return PhoneTrackClientUtil.isValidURL(ccm, params[0]);
        }

        @Override
        protected void onPostExecute(Boolean o) {
            if (o) {
                Drawable actionDoneDark = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_check_grey600_24dp);
                actionDoneDark.setBounds( 0, 0, actionDoneDark.getIntrinsicWidth(), actionDoneDark.getIntrinsicHeight() );
                field_url.setCompoundDrawables(null, null, actionDoneDark, null);
            } else {
                field_url.setCompoundDrawables(null, null, null, null);
            }
        }
    }

    /**
     * If Log-In-Credentials are correct, save Credentials to Shared Preferences and finish First Run Wizard.
     */
    private class LoginValidatorAsyncTask extends AsyncTask<String, Void, LoginStatus> {
        String url, username, password;

        @Override
        protected void onPreExecute() {
            setInputsEnabled(false);
            btn_submit.setText(R.string.settings_submitting);
        }

        /**
         * @param params url, username and password
         * @return isValidLogin Boolean
         */
        @Override
        protected LoginStatus doInBackground(String... params) {
            url = params[0];
            username = params[1];
            password = params[2];
            CustomCertManager ccm = SessionServerSyncHelper.getInstance(IHateMoneySQLiteOpenHelper.getInstance(getApplicationContext())).getCustomCertManager();
            return PhoneTrackClientUtil.isValidLogin(ccm, url, username, password);
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            if (LoginStatus.OK.equals(status)) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(SETTINGS_URL, url);
                editor.putString(SETTINGS_USERNAME, username);
                editor.putString(SETTINGS_PASSWORD, password);
                editor.remove(SETTINGS_KEY_ETAG);
                editor.remove(SETTINGS_KEY_LAST_MODIFIED);
                editor.apply();

                final Intent data = new Intent();
                data.putExtra(BillsListViewActivity.CREDENTIALS_CHANGED, CREDENTIALS_CHANGED);
                setResult(RESULT_OK, data);
                finish();
            } else {
                Log.e("IHateMoney", "invalid login");
                btn_submit.setText(R.string.settings_submit);
                setInputsEnabled(true);
                Toast.makeText(getApplicationContext(), getString(R.string.error_invalid_login, getString(status.str)), Toast.LENGTH_LONG).show();
            }
        }

        /**
         * Sets all Input-Fields and Buttons to enabled or disabled depending on the given boolean.
         *
         * @param enabled - boolean
         */
        private void setInputsEnabled(boolean enabled) {
            btn_submit.setEnabled(enabled);
            field_url.setEnabled(enabled);
            field_username.setEnabled(enabled);
            field_password.setEnabled(enabled);
        }
    }
}
