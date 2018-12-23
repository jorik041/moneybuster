package net.eneiluj.ihatemoney.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;

import android.preference.MultiSelectListPreference;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
//import android.preference.PreferenceFragment;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.model.DBBill;
import net.eneiluj.ihatemoney.model.DBBillOwer;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.DBMember;
import net.eneiluj.ihatemoney.model.DBSession;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;
import net.eneiluj.ihatemoney.util.DatePreference;
import net.eneiluj.ihatemoney.util.ICallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EditBillFragment extends PreferenceFragmentCompat {

    public interface BillFragmentListener {
        void close();

        void onBillUpdated(DBBill bill);
    }

    public static final String PARAM_BILL_ID = "billId";
    public static final String PARAM_NEWBILL = "newBill";
    private static final String SAVEDKEY_BILL = "bill";
    private static final String SAVEDKEY_ORIGINAL_BILL = "original_bill";

    protected DBBill bill;

    protected IHateMoneySQLiteOpenHelper db;
    protected BillFragmentListener listener;

    private Handler handler;

    protected EditTextPreference editWhat;
    protected EditTextPreference editDate;
    protected ListPreference editPayer;
    protected EditTextPreference editAmount;
    protected MultiSelectListPreference editOwers;

    private List<DBMember> memberList;
    private List<String> memberNameList;
    private List<String> memberRemoteIdList;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootkey) {

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_BILL_ID);
            if (id > 0) {
                bill = db.getBill(id);
            } else {
                DBBill newBill = (DBBill) getArguments().getSerializable(PARAM_NEWBILL);
                if (newBill == null) {
                    throw new IllegalArgumentException(PARAM_BILL_ID + " is not given and argument " + PARAM_NEWBILL + " is missing.");
                }
                //bill = db.getBill(db.addBill(cloudBill));
                bill = newBill;
            }
        } else {
            bill = (DBBill) savedInstanceState.getSerializable(SAVEDKEY_BILL);
        }
        setHasOptionsMenu(true);
        System.out.println("BILL on create : " + bill);

        ///////////////
        addPreferencesFromResource(R.xml.activity_edit_bill);

        Preference whatPref = findPreference("what");
        whatPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("what");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference amountPref = findPreference("amount");
        amountPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("amount");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference datePref = findPreference("date");
        datePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("date");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference payerPref = findPreference("payer");
        payerPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                preference.setSummary((CharSequence) newValue);
                return true;
            }

        });
        Preference owersPref = findPreference("owers");
        owersPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("owers");
                pref.setSummary((CharSequence) newValue);
                return true;
            }

        });

        // delete confirmation
        deleteDialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked
                        db.deleteBill(bill.getId());
                        listener.close();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder.setMessage("Are you sure?").setPositiveButton("Yes", deleteDialogClickListener)
                .setNegativeButton("No", deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (BillFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + BillFragmentListener.class);
        }
        db = IHateMoneySQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        listener.onBillUpdated(bill);
    }

    @Override
    public void onPause() {
        super.onPause();
        //saveBill(null);
        //notifyLoggerService(logjob.getId());
    }

    private void notifyLoggerService(long jobId) {
        /*Intent intent = new Intent(getActivity(), LoggerService.class);
        intent.putExtra(BillsListViewActivity.UPDATED_LOGJOBS, true);
        intent.putExtra(BillsListViewActivity.UPDATED_LOGJOB_ID, jobId);
        getActivity().startService(intent);*/
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //saveBill(null);
        outState.putSerializable(SAVEDKEY_BILL, bill);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_bill_fragment, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_cancel:
                listener.close();
                return true;
            case R.id.menu_save:
                if (getWhat().isEmpty()) {

                }
                else if (getDate().isEmpty()) {

                }
                else if (getAmount() == 0) {

                }
                else if (getOwersRemoteIds().size() == 0) {

                }
                else {
                    saveBill(null);
                    listener.close();
                }
                return true;
            case R.id.menu_delete:
                confirmDeleteAlertBuilder.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCloseBill() {

        Log.d(getClass().getSimpleName(), "onCLOSE()");
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */

    protected void saveBill(@Nullable ICallback callback) {
        Log.d(getClass().getSimpleName(), "CUSTOM saveData()");
        String newWhat = getWhat();
        String newDate = getDate();
        double newAmount = getAmount();
        long newPayerRemoteId = getPayerRemoteId();

        List<Long> newOwersRemoteIds = getOwersRemoteIds();

        // check if owers have changed
        boolean owersChanged = false;
        List<Long> billOwersRemoteIds = bill.getBillOwersRemoteIds();
        if (newOwersRemoteIds.size() != billOwersRemoteIds.size()) {
            owersChanged = true;
        }
        else {
            if (!newOwersRemoteIds.containsAll(billOwersRemoteIds)) {
                owersChanged = true;
            }
            if (!billOwersRemoteIds.containsAll(newOwersRemoteIds)) {
                owersChanged = true;
            }
        }

        // if this is an existing bill
        if (bill.getId() != 0) {
            if (bill.getWhat().equals(newWhat) &&
                    bill.getDate().equals(newDate) &&
                    bill.getAmount() == newAmount &&
                    bill.getPayerRemoteId() == newPayerRemoteId &&
                    !owersChanged
                    ) {
                Log.v(getClass().getSimpleName(), "... not saving bill, since nothing has changed");
            } else {
                System.out.println("====== update bill");
                db.updateBillAndSync(bill, newPayerRemoteId, newAmount, newDate, newWhat, newOwersRemoteIds);
                //System.out.println("AFFFFFFTTTTTTEEERRRRR : "+logjob);
                //listener.onBillUpdated(bill);
                //listener.close();
            }
        }
        // this is a new bill
        else {
            // add the bill
            DBBill newBill = new DBBill(0, 0, bill.getProjectId(), newPayerRemoteId, newAmount, newDate, newWhat, DBBill.STATE_ADDED);
            long newBillId = db.addBill(newBill);
            for (long newOwerRemoteid : newOwersRemoteIds) {
                db.addBillower(newBillId, new DBBillOwer(0, newBillId, newOwerRemoteid));
            }
            db.getIhateMoneyServerSyncHelper().scheduleSync(true, bill.getProjectId());
        }
    }

    public static EditBillFragment newInstance(long billId) {
        EditBillFragment f = new EditBillFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_BILL_ID, billId);
        f.setArguments(b);
        return f;
    }

    public static EditBillFragment newInstanceWithNewBill(DBBill newBill) {
        EditBillFragment f = new EditBillFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWBILL, newBill);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("ACT EDIT BILL CREATEDDDDDDD");
        ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // manage member list
        memberList = db.getMembersOfProject(bill.getProjectId());
        memberNameList = new ArrayList<>();
        memberRemoteIdList = new ArrayList<>();
        for (DBMember member : memberList) {
            memberNameList.add(member.getName());
            memberRemoteIdList.add(String.valueOf(member.getRemoteId()));
        }

        // manage payer and owers

        if (memberNameList.size() > 0) {
            CharSequence[] memberNameArray = memberNameList.toArray(new CharSequence[memberNameList.size()]);
            CharSequence[] memberRemoteIdArray = memberRemoteIdList.toArray(new CharSequence[memberNameList.size()]);

            editPayer.setEntries(memberNameArray);
            editPayer.setEntryValues(memberRemoteIdArray);

            editOwers.setEntries(memberNameArray);
            editOwers.setEntryValues(memberRemoteIdArray);
        }

        editWhat = (EditTextPreference) this.findPreference("what");
        editWhat.setText(bill.getWhat());
        if (bill.getWhat().isEmpty()) {
            editWhat.setSummary(getString(R.string.mandatory));
        }
        else {
            editWhat.setSummary(bill.getWhat());
        }
        editDate = (EditTextPreference) this.findPreference("date");
        editDate.setText(bill.getDate());
        editDate.setSummary(bill.getDate());

        editAmount = (EditTextPreference) this.findPreference("amount");
        editAmount.setText(String.valueOf(bill.getAmount()));
        editAmount.setSummary(String.valueOf(bill.getAmount()));
    }

    protected String getWhat() {
        return editWhat.getText();
    }
    protected String getDate() {
        return editDate.getText();
    }
    protected double getAmount() {
        return Double.valueOf(editAmount.getText());
    }
    protected long getPayerRemoteId() {
        return Long.valueOf(editPayer.getValue());
    }
    protected List<Long> getOwersRemoteIds() {
        Set<String> strValues =  editOwers.getValues();
        List<Long> owersRemoteIds = new ArrayList<>();
        for (String strValue : strValues) {
            owersRemoteIds.add(Long.valueOf(strValue));
        }
        return owersRemoteIds;
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
