package net.eneiluj.moneybuster.android.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.cert4android.IOnCertificateDecision;
import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.LoginDialogFragment;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.persistence.MoneyBusterServerSyncHelper;
import net.eneiluj.moneybuster.util.CospendClientUtil;
import net.eneiluj.moneybuster.util.CospendClientUtil.LoginStatus;
import net.eneiluj.moneybuster.util.ThemeUtils;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Allows to set Settings like URL, Username and Password for Server-Synchronization
 * Created by stefan on 22.09.15.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = SettingsActivity.class.getSimpleName();
    private final static int PERMISSION_GET_ACCOUNTS = 42;

    public static final String SETTINGS_USE_SSO = "settingsUseSSO";
    public static final String SETTINGS_SSO_URL = "settingsSSOUrl";
    public static final String SETTINGS_SSO_USERNAME = "settingsSSOUsername";
    public static final String SETTINGS_URL = "settingsUrl";
    public static final String SETTINGS_USERNAME = "settingsUsername";
    public static final String SETTINGS_PASSWORD = "settingsPassword";
    public static final String SETTINGS_KEY_ETAG = "sessions_last_etag";
    public static final String SETTINGS_KEY_LAST_MODIFIED = "sessions_last_modified";
    public static final String SETTINGS_LAST_SELECTED_SESSION_ID = "settingsLastSelectedSessionId";
    public static final String DEFAULT_SETTINGS = "";
    public static final int CREDENTIALS_CHANGED = 3;

    public static final String SETTINGS_NO_MORE_SEARCH_HELP = "settingsNoMoreSearchHelp";

    public static final String LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":";
    public static final String WEBDAV_PATH_4_0_AND_LATER = "/remote.php/webdav";

    private SharedPreferences preferences = null;

    Switch use_sso_switch;
    EditText field_url;
    TextInputLayout url_wrapper;
    TextInputLayout username_wrapper;
    EditText field_username;
    EditText field_password;
    TextInputLayout password_wrapper;
    Button btn_submit;
    View urlWarnHttp;
    private String old_password = "";

    private WebView webView;

    private boolean first_run = false;
    private boolean useWebLogin = true;

    private LoginDialogFragment loginDialogFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = LayoutInflater.from(this).inflate(R.layout.activity_settings, null);
        setContentView(view);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        use_sso_switch = findViewById(R.id.use_sso_switch);
        field_url = findViewById(R.id.settings_url);
        url_wrapper = findViewById(R.id.settings_url_wrapper);
        username_wrapper = findViewById(R.id.settings_username_wrapper);
        field_username = findViewById(R.id.settings_username);
        field_password = findViewById(R.id.settings_password);
        password_wrapper = findViewById(R.id.settings_password_wrapper);
        btn_submit = findViewById(R.id.settings_submit);
        urlWarnHttp = findViewById(R.id.settings_url_warn_http);

        // this appears not to be mandatory or even useless after API level 25... (for SSO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS)
                != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "[request get accounts permission]");
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.GET_ACCOUNTS},
                    PERMISSION_GET_ACCOUNTS
            );
        }

        preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        if (!MoneyBusterServerSyncHelper.isNextcloudAccountConfigured(this)) {
            first_run = true;
            if (getSupportActionBar() != null) {
//                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }

        setupListener();

        // Load current Preferences
        use_sso_switch.setChecked(preferences.getBoolean(SETTINGS_USE_SSO, false));
        if (use_sso_switch.isChecked()) {
            url_wrapper.setVisibility(View.INVISIBLE);
            urlWarnHttp.setVisibility(View.GONE);
            btn_submit.setVisibility(View.INVISIBLE);
        }
        // manage switch color
        if (use_sso_switch.isChecked()) {
            use_sso_switch.getTrackDrawable().setColorFilter(ThemeUtils.primaryDarkColor(this), PorterDuff.Mode.SRC_IN);
            use_sso_switch.getThumbDrawable().setColorFilter(ThemeUtils.primaryColor(this), PorterDuff.Mode.MULTIPLY);
        } else {
            use_sso_switch.getTrackDrawable().setColorFilter(ContextCompat.getColor(this, R.color.fg_default_low), PorterDuff.Mode.SRC_IN);
            use_sso_switch.getThumbDrawable().setColorFilter(ContextCompat.getColor(this, R.color.fg_default_high), PorterDuff.Mode.MULTIPLY);
        }
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

        handleSubmitButtonEnabled();
    }

    private void setupListener() {

        use_sso_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isChecked = use_sso_switch.isChecked();

                if (isChecked) {
                    loginDialogFragment = new LoginDialogFragment();
                    loginDialogFragment.show(SettingsActivity.this.getSupportFragmentManager(), "NoticeDialogFragment");

                    use_sso_switch.setChecked(false);
                } else {
                    use_sso_switch.getTrackDrawable().setColorFilter(ContextCompat.getColor(SettingsActivity.this, R.color.fg_default_low), PorterDuff.Mode.SRC_IN);
                    use_sso_switch.getThumbDrawable().setColorFilter(ContextCompat.getColor(SettingsActivity.this, R.color.fg_default_high), PorterDuff.Mode.MULTIPLY);

                    url_wrapper.setVisibility(View.VISIBLE);
                    //urlWarnHttp.setVisibility(View.VISIBLE);
                    // stimulate url field to update http warning
                    String url = field_url.getText().toString();
                    field_url.setText("");
                    field_url.setText(url);
                    btn_submit.setVisibility(View.VISIBLE);

                    // update preferences
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean(SETTINGS_USE_SSO, false);
                    editor.apply();

                    // empty account list
                    MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(view.getContext());
                    // TODO db.clearAccountProjects();
                }
            }

        });

        field_url.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                new URLValidatorAsyncTask().execute(CospendClientUtil.formatURL(field_url.getText().toString()));
            }
        });

        field_url.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String url = CospendClientUtil.formatURL(field_url.getText().toString());

                if (CospendClientUtil.isHttp(url) && !preferences.getBoolean(SETTINGS_USE_SSO, false)) {
                    urlWarnHttp.setVisibility(View.VISIBLE);
                } else {
                    urlWarnHttp.setVisibility(View.GONE);
                }
                new URLValidatorAsyncTask().execute(CospendClientUtil.formatURL(field_url.getText().toString()));
                //handleSubmitButtonEnabled();
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
                handleSubmitButtonEnabled();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

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

    private void legacyLogin() {
        String url = field_url.getText().toString().trim();
        String username = field_username.getText().toString();
        String password = field_password.getText().toString();

        if (password.isEmpty()) {
            password = old_password;
        }

        url = CospendClientUtil.formatURL(url);

        new LoginValidatorAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, username, password);
    }

    private void login() {
        if (useWebLogin) {
            webLogin();
        } else {
            legacyLogin();
        }
    }

    /**
     * Obtain the X509Certificate from SslError
     *
     * @param error SslError
     * @return X509Certificate from error
     */
    public static X509Certificate getX509CertificateFromError(SslError error) {
        Bundle bundle = SslCertificate.saveState(error.getCertificate());
        X509Certificate x509Certificate;
        byte[] bytes = bundle.getByteArray("x509-certificate");
        if (bytes == null) {
            x509Certificate = null;
        } else {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                Certificate cert = certFactory.generateCertificate(new ByteArrayInputStream(bytes));
                x509Certificate = (X509Certificate) cert;
            } catch (CertificateException e) {
                x509Certificate = null;
            }
        }
        return x509Certificate;
    }

    private void webLogin() {
        setContentView(R.layout.activity_settings_webview);
        webView = findViewById(R.id.login_webview);
        webView.setVisibility(View.GONE);

        final ProgressBar progressBar = findViewById(R.id.login_webview_progress_bar);

        WebSettings settings = webView.getSettings();
        settings.setAllowFileAccess(false);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(getWebLoginUserAgent());
        settings.setSaveFormData(false);
        settings.setSavePassword(false);

        Map<String, String> headers = new HashMap<>();
        headers.put("OCS-APIREQUEST", "true");


        webView.loadUrl(normalizeUrlSuffix(field_url.getText().toString()) + "index.php/login/flow", headers);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("nc://login/")) {
                    parseAndLoginFromWebView(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
                X509Certificate cert = getX509CertificateFromError(error);

                try {
                    final boolean[] accepted = new boolean[1];
                    MoneyBusterServerSyncHelper.getInstance(MoneyBusterSQLiteOpenHelper.getInstance(getApplicationContext()))
                            .checkCertificate(cert.getEncoded(), new IOnCertificateDecision.Stub() {
                                @Override
                                public void accept() {
                                    Log.d("MoneyBuster", "cert accepted");
                                    handler.proceed();
                                    accepted[0] = true;
                                }

                                @Override
                                public void reject() {
                                    Log.d("MoneyBuster", "cert rejected");
                                    handler.cancel();
                                }
                            });

                    if (!accepted[0]) {
                        // this should never happen, submit button is only enabled if url has been validated
                        Log.e("MoneyBuster", "No response from certificate service");
                        handler.cancel();
                    }
                } catch (Exception e) {
                    Log.e("MoneyBuster", "Cert could not be verified");
                    handler.cancel();
                }
            }

        });

        // don't show old login method because SSO+weblogin is enough
        // TODO cleanup
        /*// show snackbar after 60s to switch back to old login method
        new Handler().postDelayed(() -> {
            Snackbar.make(webView, R.string.fallback_weblogin_text, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.fallback_weblogin_back, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            initLegacyLogin(field_url.getText().toString());
                        }
                    }).show();
        }, 60 * 1000);*/
    }

    private String getWebLoginUserAgent() {
        return Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.getDefault()) +
                Build.MANUFACTURER.substring(1).toLowerCase(Locale.getDefault()) + " " + Build.MODEL;
    }

    private void parseAndLoginFromWebView(String dataString) {
        String prefix = "nc://login/";
        LoginUrlInfo loginUrlInfo = parseLoginDataUrl(prefix, dataString);

        if (loginUrlInfo != null) {
            String url = normalizeUrlSuffix(loginUrlInfo.serverAddress);

            new LoginValidatorAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, loginUrlInfo.username,
                    loginUrlInfo.password);
        }
    }

    /**
     * parses a URI string and returns a login data object with the information from the URI string.
     *
     * @param prefix     URI beginning, e.g. cloud://login/
     * @param dataString the complete URI
     * @return login data
     * @throws IllegalArgumentException when
     */
    private LoginUrlInfo parseLoginDataUrl(String prefix, String dataString) throws IllegalArgumentException {
        if (dataString.length() < prefix.length()) {
            throw new IllegalArgumentException("Invalid login URL detected");
        }
        LoginUrlInfo loginUrlInfo = new LoginUrlInfo();

        // format is basically xxx://login/server:xxx&user:xxx&password while all variables are optional
        String data = dataString.substring(prefix.length());

        // parse data
        String[] values = data.split("&");

        if (values.length < 1 || values.length > 3) {
            // error illegal number of URL elements detected
            throw new IllegalArgumentException("Illegal number of login URL elements detected: " + values.length);
        }

        for (String value : values) {
            if (value.startsWith("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginUrlInfo.username = URLDecoder.decode(
                        value.substring(("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length()));
            } else if (value.startsWith("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginUrlInfo.password = URLDecoder.decode(
                        value.substring(("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length()));
            } else if (value.startsWith("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginUrlInfo.serverAddress = URLDecoder.decode(
                        value.substring(("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length()));
            } else {
                // error illegal URL element detected
                throw new IllegalArgumentException("Illegal magic login URL element detected: " + value);
            }
        }

        return loginUrlInfo;
    }

    private String normalizeUrlSuffix(String url) {
        if (url.toLowerCase(Locale.ROOT).endsWith(WEBDAV_PATH_4_0_AND_LATER)) {
            return url.substring(0, url.length() - WEBDAV_PATH_4_0_AND_LATER.length());
        }

        if (!url.endsWith("/")) {
            return url + "/";
        }

        return url;
    }

    private void initLegacyLogin(String oldUrl) {
        useWebLogin = false;

        webView.setVisibility(View.INVISIBLE);
        setContentView(R.layout.activity_settings);

        //ButterKnife.bind(this);
        setupListener();

        field_url.setText(oldUrl);
        username_wrapper.setVisibility(View.VISIBLE);
        password_wrapper.setVisibility(View.VISIBLE);
    }

    private void handleSubmitButtonEnabled() {
        // drawable[2] is not null if url is valid, see URLValidatorAsyncTask::onPostExecute
        if (field_url.getCompoundDrawables()[2] != null && (username_wrapper.getVisibility() == View.GONE ||
                (username_wrapper.getVisibility() == View.VISIBLE && field_username.getText().length() > 0))) {
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
            btn_submit.setEnabled(false);
            field_url.setCompoundDrawables(null, null, null, null);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            CustomCertManager ccm = MoneyBusterServerSyncHelper.getInstance(MoneyBusterSQLiteOpenHelper.getInstance(getApplicationContext())).getCustomCertManager();
            return CospendClientUtil.isValidURL(ccm, params[0]);
        }

        @Override
        protected void onPostExecute(Boolean o) {
            if (o) {
                Drawable actionDoneDark = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_check_grey600_24dp);
                actionDoneDark.setBounds(0, 0, actionDoneDark.getIntrinsicWidth(), actionDoneDark.getIntrinsicHeight());
                field_url.setCompoundDrawables(null, null, actionDoneDark, null);
            } else {
                field_url.setCompoundDrawables(null, null, null, null);
            }
            handleSubmitButtonEnabled();
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
            CustomCertManager ccm = MoneyBusterServerSyncHelper.getInstance(MoneyBusterSQLiteOpenHelper.getInstance(getApplicationContext())).getCustomCertManager();
            return CospendClientUtil.isValidLogin(ccm, url, username, password);
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
                Log.e("MoneyBuster", "invalid login");
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
    /**
     * Data object holding the login url fields.
     */
    public class LoginUrlInfo {
        String serverAddress;
        String username;
        String password;
    }

    public void onAccountChoose(SingleSignOnAccount account) {
        getSupportFragmentManager().beginTransaction().remove(loginDialogFragment).commit();
        //Snackbar.make(, "Account URL: "+account.url, Snackbar.LENGTH_LONG).show();

        url_wrapper.setVisibility(View.INVISIBLE);
        urlWarnHttp.setVisibility(View.GONE);
        btn_submit.setVisibility(View.INVISIBLE);

        SingleAccountHelper.setCurrentAccount(this, account.name);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SETTINGS_USE_SSO, true);
        editor.putString(SETTINGS_SSO_URL, account.url+"/");
        editor.putString(SETTINGS_SSO_USERNAME, account.name);
        editor.apply();

        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.CREDENTIALS_CHANGED, CREDENTIALS_CHANGED);
        setResult(RESULT_OK, data);
        finish();

        //SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context);
        //NextcloudAPI nextcloudAPI = new NextcloudAPI(context, ssoAccount, new GsonBuilder().create(), callback);

    }
}
