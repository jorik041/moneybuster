package net.eneiluj.moneybuster.android.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import android.view.Window;
import android.widget.Toast;

import net.eneiluj.moneybuster.android.fragment.NewProjectFragment;
import net.eneiluj.moneybuster.util.ThemeUtils;

import static net.eneiluj.moneybuster.android.fragment.NewProjectFragment.TYPE_IHATEMONEY;
import static net.eneiluj.moneybuster.android.fragment.NewProjectFragment.TYPE_LOCAL;
import static net.eneiluj.moneybuster.android.fragment.NewProjectFragment.TYPE_NEXTCLOUD_COSPEND;

public class NewProjectActivity extends AppCompatActivity implements NewProjectFragment.NewProjectFragmentListener {

    //public static final String PARAM_PROJECT_ID = "projectId";

    protected NewProjectFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreate: ");
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchNewProjectFragment();
        } else {
            fragment = (NewProjectFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            int color = ThemeUtils.primaryColor(this);
            actionBar.setBackgroundDrawable(new ColorDrawable(color));
        }

        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int colorDark = ThemeUtils.primaryDarkColor(this);
                window.setStatusBarColor(colorDark);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_PROJECT_ID, 0));
        Log.d(getClass().getSimpleName(), "onNewIntent: ");
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchNewProjectFragment();
    }

    protected String getDefaultIhmUrl() {
        return getIntent().getStringExtra(NewProjectFragment.PARAM_DEFAULT_IHM_URL);
    }

    protected String getDefaultNcUrl() {
        return getIntent().getStringExtra(NewProjectFragment.PARAM_DEFAULT_NC_URL);
    }

    private void launchNewProjectFragment() {
        String defaultIhmUrl = getDefaultIhmUrl();
        String defaultNcUrl = getDefaultNcUrl();
        String defaultProjectId = null;
        String defaultProjectPassword = null;
        String defaultProjectType = TYPE_LOCAL;

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data.getHost().equals("net.eneiluj.moneybuster.cospend") && data.getPathSegments().size() >= 3) {
                defaultProjectPassword = data.getLastPathSegment();
                defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                defaultNcUrl = "https:/" +
                        data.getPath().replaceAll("/"+defaultProjectId+"/" + defaultProjectPassword + "$", "");
                defaultProjectType = TYPE_NEXTCLOUD_COSPEND;
            }
            else if (data.getHost().equals("net.eneiluj.moneybuster.ihatemoney") && data.getPathSegments().size() >= 3) {
                defaultProjectPassword = data.getLastPathSegment();
                defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                defaultIhmUrl = "https:/" +
                        data.getPath().replaceAll("/"+defaultProjectId+"/" + defaultProjectPassword + "$", "");
                defaultProjectType = TYPE_IHATEMONEY;
            }
        }
        fragment = NewProjectFragment.newInstance(
                defaultIhmUrl, defaultNcUrl,
                defaultProjectId, defaultProjectPassword, defaultProjectType
        );
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        close(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close(0);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Send result and closes the Activity
     */
    public void close(long pid) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.CREATED_PROJECT, pid);
        setResult(RESULT_OK, data);
        finish();
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}