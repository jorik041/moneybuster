package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.support.v4.app.Fragment;
import android.content.Intent;

import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditPhoneTrackLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

public class EditPhoneTrackLogjobActivity extends EditLogjobActivity {


    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    protected void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditPhoneTrackLogjobFragment.newInstance(logjobId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    protected void launchNewLogjob() {
        Intent intent = getIntent();

        DBLogjob newLogjob = new DBLogjob(0, "",  "https://yournextcloud.org", "supersessiontoken", "mydevname", 60, 5, 50, false,false, 0);

        String url;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!newLogjob.setAttrFromLoggingUrl(url)) {
                showToast(getString(R.string.error_invalid_pt_url), Toast.LENGTH_LONG);
            }
        }

        fragment = EditPhoneTrackLogjobFragment.newInstanceWithNewLogjob(newLogjob);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }
}