package net.eneiluj.moneybuster.android.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
//import android.support.v4.app.Fragment;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import android.view.Window;
import android.widget.Toast;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.EditBillFragment;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.util.ThemeUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class EditBillActivity extends AppCompatActivity implements EditBillFragment.BillFragmentListener {

    public static final String PARAM_BILL_ID = "billId";
    public static final String PARAM_PROJECT_ID = "projectId";

    protected EditBillFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchBillFragment();
        } else {
            fragment = (EditBillFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
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
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_BILL_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchBillFragment();
    }

    protected long getBillId() {
        return getIntent().getLongExtra(PARAM_BILL_ID, 0);
    }

    protected long getProjectId() {
        return getIntent().getLongExtra(PARAM_PROJECT_ID, 0);
    }

    /**
     * Starts the EditBillFragment for an existing bill or a new bill
     */
    private void launchBillFragment() {
        long billId = getBillId();
        if (billId > 0) {
            launchExistingBill(billId);
        } else {
            launchNewBill(getProjectId());
        }
    }

    /**
     * Starts a {@link EditBillFragment} for an existing bill.
     *
     * @param billId ID of the existing bill.
     */

    protected void launchExistingBill(long billId) {
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditBillFragment.newInstance(billId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.simple_edit_bill);
    }

    /**
     * Starts the {@link EditBillFragment} with a new bill.
     *
     */

    protected void launchNewBill(long projectId) {
        Intent intent = getIntent();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String newDate = sdf.format(new Date());
        DBBill newBill = new DBBill(0, 0, projectId, 0, 0, newDate, "", DBBill.STATE_ADDED);

        fragment = EditBillFragment.newInstanceWithNewBill(newBill);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.simple_new_bill);
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