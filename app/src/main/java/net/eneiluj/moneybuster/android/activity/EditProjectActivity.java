package net.eneiluj.moneybuster.android.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.EditProjectFragment;
import net.eneiluj.moneybuster.model.DBProject;

public class EditProjectActivity extends AppCompatActivity implements EditProjectFragment.EditProjectFragmentListener {

    public static final String PARAM_PROJECT_ID = "projectId";

    protected EditProjectFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchEditProjectFragment();
        } else {
            fragment = (EditProjectFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
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
        launchEditProjectFragment();
    }

    protected long getProjectId() {
        return getIntent().getLongExtra(PARAM_PROJECT_ID, 0);
    }

    private void launchEditProjectFragment() {
        long projectId = getProjectId();
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditProjectFragment.newInstance(projectId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        //getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        View view = LayoutInflater.from(this).inflate(R.layout.activity_edit_project, null);
        setContentView(view);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        close();
    }

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

    public void closeOnDelete(long pid) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.DELETED_PROJECT, pid);
        setResult(RESULT_OK, data);
        Log.d(getClass().getSimpleName(), "setresult pid : "+pid);
        finish();
    }

    public void closeOnEdit(long pid) {
        fragment.onCloseProject();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.EDITED_PROJECT, pid);
        setResult(RESULT_OK, data);
        finish();
    }

    public void onProjectUpdated(DBProject project) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(project.getName());
            actionBar.setSubtitle(project.getServerUrl());
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}