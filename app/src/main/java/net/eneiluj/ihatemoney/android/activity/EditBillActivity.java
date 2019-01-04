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
import net.eneiluj.ihatemoney.android.fragment.EditBillFragment;
import net.eneiluj.ihatemoney.model.DBBill;
import net.eneiluj.ihatemoney.model.DBLogjob;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class EditBillActivity extends AppCompatActivity implements EditBillFragment.BillFragmentListener {

    public static final String PARAM_BILL_ID = "billId";
    public static final String PARAM_PROJECT_ID = "projectId";
    public static final String PARAM_MEMBERS_BALANCE = "membersBalance";

    protected EditBillFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchLogjobFragment();
        } else {
            fragment = (EditBillFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_BILL_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchLogjobFragment();
    }

    protected long getBillId() {
        return getIntent().getLongExtra(PARAM_BILL_ID, 0);
    }

    protected long getProjectId() {
        return getIntent().getLongExtra(PARAM_PROJECT_ID, 0);
    }

    protected HashMap<Long, Double> getMembersBalance() {
        return (HashMap<Long, Double>)getIntent().getSerializableExtra(PARAM_MEMBERS_BALANCE);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchLogjobFragment() {
        long billId = getBillId();
        if (billId > 0) {
            launchExistingBill(billId);
        } else {
            launchNewBill(getProjectId());
        }
    }

    /**
     * Starts a {@link EditBillFragment} for an existing logjob.
     *
     * @param billId ID of the existing logjob.
     */

    protected void launchExistingBill(long billId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditBillFragment.newInstance(billId, getMembersBalance());
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.simple_edit_bill);
    }

    /**
     * Starts the {@link EditBillFragment} with a new logjob.
     *
     */

    protected void launchNewBill(long projectId) {
        Intent intent = getIntent();

        //DBLogjob newLogjob = new DBLogjob(0, "",  "https://yourserver.org/page.php?lat=%LAT", "", "", 60, 5, 50, false, false, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String newDate = sdf.format(new Date());
        DBBill newBill = new DBBill(0, 0, projectId, 0, 0, newDate, "", DBBill.STATE_ADDED);

        fragment = EditBillFragment.newInstanceWithNewBill(newBill, getMembersBalance());
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.simple_new_bill);
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
        fragment.onCloseBill();
        finish();
    }

    public void closeOnDelete(long billId) {
        fragment.onCloseBill();
        final Intent data = new Intent();
        data.putExtra(BillsListViewActivity.DELETED_BILL, billId);
        setResult(RESULT_OK, data);
        finish();
    }

    public void onBillUpdated(DBBill bill) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //actionBar.setTitle();
            actionBar.setSubtitle("[" + bill.getDate() + "] " + bill.getAmount() + " (" + bill.getWhat() + ")");
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}