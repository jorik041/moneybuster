package net.eneiluj.ihatemoney.android.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import android.widget.Toast;

import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.fragment.EditCustomLogjobFragment;
import net.eneiluj.ihatemoney.android.fragment.EditLogjobFragment;
import net.eneiluj.ihatemoney.android.fragment.EditProjectFragment;
import net.eneiluj.ihatemoney.android.fragment.NewProjectFragment;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.DBProject;

public class NewProjectActivity extends AppCompatActivity implements NewProjectFragment.NewProjectFragmentListener {

    //public static final String PARAM_PROJECT_ID = "projectId";

    protected NewProjectFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchNewProjectFragment();
        } else {
            fragment = (NewProjectFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
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
        return getIntent().getStringExtra(NewProjectFragment.PARAM_DEFAULT_URL);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchNewProjectFragment() {
        String defaultIhmUrl = getDefaultIhmUrl();
        fragment = NewProjectFragment.newInstance(defaultIhmUrl);
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