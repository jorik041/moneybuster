package net.eneiluj.ihatemoney.android.activity;

import android.support.v4.app.Fragment;
import android.content.Intent;

import android.widget.Toast;

import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.fragment.EditCustomLogjobFragment;
import net.eneiluj.ihatemoney.android.fragment.EditLogjobFragment;
import net.eneiluj.ihatemoney.model.DBLogjob;

public class EditCustomLogjobActivity extends EditLogjobActivity {

    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    @Override
    protected void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditCustomLogjobFragment.newInstance(logjobId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    @Override
    protected void launchNewLogjob() {
        Intent intent = getIntent();

        DBLogjob newLogjob = new DBLogjob(0, "",  "https://yourserver.org/page.php?lat=%LAT", "", "", 60, 5, 50, false, false, 0);

        String url;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!url.startsWith("http")) {
                showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
            }
            else {
                newLogjob.setUrl(url);
            }
        }

        fragment = EditCustomLogjobFragment.newInstanceWithNewLogjob(newLogjob);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }
}