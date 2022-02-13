package net.eneiluj.moneybuster.android.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.NewProjectFragment;
import net.eneiluj.moneybuster.model.ProjectType;

public class NewProjectActivity extends AppCompatActivity implements NewProjectFragment.NewProjectFragmentListener {

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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
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
        ProjectType defaultProjectType = ProjectType.LOCAL;

        Boolean shouldCloseActivity = false;

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri data = getIntent().getData();
            if (data == null) {
                showToast(getString(R.string.import_no_data), Toast.LENGTH_LONG);
                shouldCloseActivity = true;
            } else if (data.getScheme().equals("cospend") && data.getPathSegments().size() >= 1) {
                if (data.getPath().endsWith("/")) {
                    defaultProjectPassword = "";
                    defaultProjectId = data.getLastPathSegment();
                } else {
                    defaultProjectPassword = data.getLastPathSegment();
                    defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                }
                defaultNcUrl = "https://" +
                        data.getHost() + data.getPath().replaceAll("/"+defaultProjectId+"/" + defaultProjectPassword + "$", "");
                defaultProjectType = ProjectType.COSPEND;
            } else if (data.getScheme().equals("ihatemoney") && data.getPathSegments().size() >= 1) {
                // invitation link
                if (data.getPathSegments().size() >= 3
                    && "join".equals(data.getPathSegments().get(data.getPathSegments().size() - 2))) {
                    defaultIhmUrl = "https://" +
                            data.getHost() + data.getPath();
                } else {
                    if (data.getPath().endsWith("/")) {
                        defaultProjectPassword = "";
                        defaultProjectId = data.getLastPathSegment();
                    } else {
                        defaultProjectPassword = data.getLastPathSegment();
                        defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                    }
                    defaultIhmUrl = "https://" +
                            data.getHost() + data.getPath().replaceAll("/" + defaultProjectId + "/" + defaultProjectPassword + "$", "");
                }
                defaultProjectType = ProjectType.IHATEMONEY;
            } else if (data.getHost().equals("net.eneiluj.moneybuster.cospend") && data.getPathSegments().size() >= 2) {
                if (data.getPath().endsWith("/")) {
                    defaultProjectPassword = "";
                    defaultProjectId = data.getLastPathSegment();
                } else {
                    defaultProjectPassword = data.getLastPathSegment();
                    defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                }
                defaultNcUrl = "https:/" +
                        data.getPath().replaceAll("/"+defaultProjectId+"/" + defaultProjectPassword + "$", "");
                defaultProjectType = ProjectType.COSPEND;
            } else if (data.getHost().equals("net.eneiluj.moneybuster.ihatemoney") && data.getPathSegments().size() >= 2) {
                if (data.getPath().endsWith("/")) {
                    defaultProjectPassword = "";
                    defaultProjectId = data.getLastPathSegment();
                } else {
                    defaultProjectPassword = data.getLastPathSegment();
                    defaultProjectId = data.getPathSegments().get(data.getPathSegments().size() - 2);
                }
                defaultIhmUrl = "https:/" +
                        data.getPath().replaceAll("/"+defaultProjectId+"/" + defaultProjectPassword + "$", "");
                defaultProjectType = ProjectType.IHATEMONEY;
            } else {
                showToast(getString(R.string.import_bad_url), Toast.LENGTH_LONG);
                shouldCloseActivity = true;
            }
        }
        fragment = NewProjectFragment.newInstance(
                defaultIhmUrl, defaultNcUrl,
                defaultProjectId, defaultProjectPassword, defaultProjectType,
                (Intent.ACTION_VIEW.equals(getIntent().getAction()))
        );

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        if (shouldCloseActivity) {
            close(0, false);
        }
    }

    @Override
    public void onBackPressed() {
        close(0, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close(0, false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Send result and closes the Activity
     */
    public void close(long pid, boolean justAdded) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        if (justAdded) {
            data.putExtra(BillsListViewActivity.ADDED_PROJECT, pid);
        } else {
            data.putExtra(BillsListViewActivity.CREATED_PROJECT, pid);
        }
        setResult(RESULT_OK, data);
        finish();
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}