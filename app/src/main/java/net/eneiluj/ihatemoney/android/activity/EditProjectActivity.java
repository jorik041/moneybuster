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
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.DBProject;

public class EditProjectActivity extends AppCompatActivity implements EditProjectFragment.ProjectFragmentListener {

    public static final String PARAM_PROJECT_ID = "projectId";

    protected EditProjectFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchProjectFragment();
        } else {
            fragment = (EditProjectFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_PROJECT_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchProjectFragment();
    }

    protected long getProjectId() {
        return getIntent().getLongExtra(PARAM_PROJECT_ID, 0);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchProjectFragment() {
        long projectId = getProjectId();
        if (projectId > 0) {
            launchExistingProject(projectId);
        } else {
            launchNewProject();
        }
    }

    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    protected void launchExistingProject(long projectId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditProjectFragment.newInstance(projectId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    protected void launchNewProject() {
        Intent intent = getIntent();

        DBProject newProject = new DBProject("name", "url", "","pass");

        fragment = EditProjectFragment.newInstanceWithNewProject(newProject);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_logjob_activity, menu);
        //return super.onCreateOptionsMenu(menu);
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Send result and closes the Activity
     */
    public void close() {
        fragment.onCloseProject();
        finish();
    }

    public void onProjectUpdated(DBLogjob logjob) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(logjob.getTitle());
            actionBar.setSubtitle(logjob.getDeviceName());
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}