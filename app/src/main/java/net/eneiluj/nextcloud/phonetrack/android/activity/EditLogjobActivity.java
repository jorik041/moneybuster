package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.android.fragment.EditLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

public abstract class EditLogjobActivity extends AppCompatActivity implements EditLogjobFragment.LogjobFragmentListener {

    public static final String PARAM_LOGJOB_ID = "logjobId";

    protected EditLogjobFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchLogjobFragment();
        } else {
            fragment = (EditLogjobFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_LOGJOB_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchLogjobFragment();
    }

    protected long getLogjobId() {
        return getIntent().getLongExtra(PARAM_LOGJOB_ID, 0);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchLogjobFragment() {
        long logjobId = getLogjobId();
        if (logjobId > 0) {
            launchExistingLogjob(logjobId);
        } else {
            launchNewLogjob();
        }
    }

    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    protected abstract void launchExistingLogjob(long logjobId);

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    protected abstract void launchNewLogjob();

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
        fragment.onCloseLogjob();
        finish();
    }

    @Override
    public void onLogjobUpdated(DBLogjob logjob) {
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