package net.eneiluj.moneybuster.android.fragment;

//import android.app.AlertDialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;

//import android.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
//import android.support.v7.preference.PreferenceFragmentCompat;
import com.takisoft.fix.support.v7.preference.DatePickerPreference;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.util.ICallback;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class EditBillFragment extends PreferenceFragmentCompat {

    public interface BillFragmentListener {
        void close();

        void closeOnDelete(long billId);

        void onBillUpdated(DBBill bill);
    }

    public static final String PARAM_BILL_ID = "billId";
    public static final String PARAM_NEWBILL = "newBill";
    private static final String SAVEDKEY_BILL = "bill";
    private static final String SAVEDKEY_ORIGINAL_BILL = "original_bill";

    protected DBBill bill;

    protected MoneyBusterSQLiteOpenHelper db;
    protected BillFragmentListener listener;

    private Handler handler;

    protected EditTextPreference editWhat;
    protected DatePickerPreference editDate;
    protected ListPreference editPayer;
    protected EditTextPreference editAmount;
    protected MultiSelectListPreference editOwers;

    private List<DBMember> memberList;
    private List<String> memberNameList;
    private List<String> memberIdList;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    private SimpleDateFormat sdf;

    @Override
    public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
        //setPreferencesFromResource(R.xml.settings, rootKey);
        addPreferencesFromResource(R.xml.activity_edit_bill);
        // additional setup
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

        sdf = new SimpleDateFormat("yyyy-MM-dd");

        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_BILL_ID);
            if (id > 0) {
                bill = db.getBill(id);
                listener.onBillUpdated(bill);
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
        //addPreferencesFromResource(R.xml.activity_edit_bill);

        Preference whatPref = findPreference("what");
        whatPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("what");
                pref.setSummary((CharSequence) newValue);
                //bill.setWhat((String)newValue);
                //listener.onBillUpdated(bill);
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
                //bill.setAmount(Double.valueOf((String) newValue));
                //listener.onBillUpdated(bill);
                return true;
            }

        });
        Preference datePref = findPreference("date");
        datePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                DatePickerPreference pref = (DatePickerPreference) findPreference("date");
                DatePickerPreference.DateWrapper dw = (DatePickerPreference.DateWrapper) newValue;

                //pref.setSummary(dw.year+"-"+dw.month+"-"+dw.day);
                //bill.setDate(dw.year+"-"+(dw.month+1)+"-"+dw.day);
                //listener.onBillUpdated(bill);
                return true;
            }

        });
        Preference payerPref = findPreference("payer");
        payerPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                ListPreference pref = (ListPreference) findPreference("payer");
                int index = pref.findIndexOfValue((String)newValue);
                preference.setSummary(pref.getEntries()[index]);
                return true;
            }

        });
        Preference owersPref = findPreference("owers");
        owersPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                MultiSelectListPreference pref = (MultiSelectListPreference) findPreference("owers");
                //CharSequence[] selectedValuesArray = pref.getEntryValues();
                @SuppressWarnings("unchecked")
                List<String> selectedValuesList = new ArrayList<>((HashSet<String>)newValue);
                CharSequence[] allEntriesArray = pref.getEntries();
                String summary = "";
                //for (int i=0; i < selectedValuesArray.length; i++) {
                for (String selectedValue : selectedValuesList) {
                    int owerIndex = pref.findIndexOfValue(selectedValue);
                    summary += allEntriesArray[owerIndex] + ", ";
                }
                pref.setSummary(summary.replaceAll(", $", ""));
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
                        if (db.getBill(bill.getId()).getState() == DBBill.STATE_ADDED) {
                            db.deleteBill(bill.getId());
                        }
                        else {
                            db.setBillState(bill.getId(), DBBill.STATE_DELETED);
                        }
                        listener.closeOnDelete(bill.getId());
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        confirmDeleteAlertBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.AppThemeDialog));
        confirmDeleteAlertBuilder.setMessage(getString(R.string.confirm_delete_bill_dialog_title))
                .setPositiveButton(getString(R.string.simple_yes), deleteDialogClickListener)
                .setNegativeButton(getString(R.string.simple_no), deleteDialogClickListener);

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
        db = MoneyBusterSQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        //listener.onBillUpdated(bill);
    }

    @Override
    public void onPause() {
        super.onPause();
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
                if (getWhat() == null || getWhat().equals("")) {
                    showToast(getString(R.string.error_invalid_bill_what), Toast.LENGTH_LONG);
                }
                else if (getDate() == null || getDate().equals("")) {
                    showToast(getString(R.string.error_invalid_bill_date), Toast.LENGTH_LONG);
                }
                else if (getAmount() == 0.0) {
                    showToast(getString(R.string.error_invalid_bill_amount), Toast.LENGTH_LONG);
                }
                else if (getPayerId() == 0) {
                    showToast(getString(R.string.error_invalid_bill_payerid), Toast.LENGTH_LONG);
                }
                else if (getOwersIds().size() == 0) {
                    showToast(getString(R.string.error_invalid_bill_owers), Toast.LENGTH_LONG);
                }
                else {
                    saveBill(null);
                    listener.close();
                }
                return true;
            case R.id.menu_delete:
                if (bill.getId() != 0) {
                    confirmDeleteAlertBuilder.show();
                }
                else {
                    listener.close();
                }
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
        long newPayerId = getPayerId();

        List<Long> newOwersIds = getOwersIds();

        // check if owers have changed
        boolean owersChanged = false;
        List<Long> billOwersIds = bill.getBillOwersIds();
        if (newOwersIds.size() != billOwersIds.size()) {
            owersChanged = true;
        }
        else {
            if (!newOwersIds.containsAll(billOwersIds)) {
                owersChanged = true;
            }
            if (!billOwersIds.containsAll(newOwersIds)) {
                owersChanged = true;
            }
        }

        // if this is an existing bill
        if (bill.getId() != 0) {
            if (bill.getWhat().equals(newWhat) &&
                    bill.getDate().equals(newDate) &&
                    bill.getAmount() == newAmount &&
                    bill.getPayerId() == newPayerId &&
                    !owersChanged
                    ) {
                Log.v(getClass().getSimpleName(), "... not saving bill, since nothing has changed "+bill.getWhat()+" "+newWhat);
            } else {
                System.out.println("====== update bill");
                db.updateBillAndSync(bill, newPayerId, newAmount, newDate, newWhat, newOwersIds);
                //listener.onBillUpdated(bill);
                //listener.close();
            }
        }
        // this is a new bill
        else {
            // add the bill
            DBBill newBill = new DBBill(0, 0, bill.getProjectId(), newPayerId, newAmount, newDate, newWhat, DBBill.STATE_ADDED);
            for (long newOwerId : newOwersIds) {
                newBill.getBillOwers().add(new DBBillOwer(0, 0, newOwerId));
            }
            long newBillId = db.addBill(newBill);

            // normally sync should be done when we get back to bill list
            // so next line can be commented
            // db.getMoneyBusterServerSyncHelper().scheduleSync(true, bill.getProjectId());
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
        //ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // manage member list
        memberList = db.getMembersOfProject(bill.getProjectId(), null);
        memberNameList = new ArrayList<>();
        memberIdList = new ArrayList<>();
        for (DBMember member : memberList) {
            if (member.isActivated()) {
                memberNameList.add(member.getName());
                memberIdList.add(String.valueOf(member.getId()));
            }
        }

        // manage payer and owers

        editPayer = (ListPreference) this.findPreference("payer");
        editOwers = (MultiSelectListPreference) this.findPreference("owers");

        if (memberNameList.size() > 0) {
            CharSequence[] memberNameArray = memberNameList.toArray(new CharSequence[memberNameList.size()]);
            CharSequence[] memberIdArray = memberIdList.toArray(new CharSequence[memberNameList.size()]);

            editPayer.setEntries(memberNameArray);
            editPayer.setEntryValues(memberIdArray);

            editOwers.setEntries(memberNameArray);
            editOwers.setEntryValues(memberIdArray);

            // set selected value for payer
            if (bill.getPayerId() != 0) {
                String payerId = String.valueOf(bill.getPayerId());
                // if the id is not found, it means the user is disabled
                int payerIndex = memberIdList.indexOf(payerId);
                if (payerIndex != -1) {
                    editPayer.setValue(payerId);
                    editPayer.setSummary(memberNameList.get(payerIndex));
                }
            }

            // set selected values for owers
            if (bill.getId() != 0) {
                if (bill.getBillOwersIds().size() > 0) {
                    Set<String> owerIdSet = new HashSet<String>();
                    List<String> selectedNames = new ArrayList<>();
                    for (long owerId : bill.getBillOwersIds()) {
                        int owerIndex = memberIdList.indexOf(String.valueOf(owerId));
                        if (owerIndex != -1) {
                            owerIdSet.add(String.valueOf(owerId));
                            selectedNames.add(memberNameList.get(owerIndex));
                        }
                    }
                    editOwers.setValues(owerIdSet);
                    editOwers.setSummary(TextUtils.join(", ", selectedNames));
                }
            }
            // new bill
            else {
                Set<String> owerIdSet = new HashSet<String>();
                List<String> selectedNames = new ArrayList<>();
                for (DBMember m : memberList) {
                    if (m.isActivated()) {
                        owerIdSet.add(String.valueOf(m.getId()));
                        selectedNames.add(m.getName());
                    }
                }
                editOwers.setValues(owerIdSet);
                editOwers.setSummary(TextUtils.join(", ", selectedNames));
            }
        }

        editWhat = (EditTextPreference) this.findPreference("what");
        editWhat.setText(bill.getWhat());
        if (bill.getWhat().isEmpty()) {
            editWhat.setSummary(getString(R.string.mandatory));
        }
        else {
            editWhat.setSummary(bill.getWhat());
        }
        editDate = (DatePickerPreference) this.findPreference("date");
        try {
            editDate.setDate(
                    sdf.parse(bill.getDate())
            );
        }
        catch (ParseException e) {
            Log.d(getClass().getSimpleName(), "bad date "+bill.getDate());
        }
        //editDate.setSummary(bill.getDate());

        editAmount = (EditTextPreference) this.findPreference("amount");
        editAmount.setText(String.valueOf(bill.getAmount()));
        editAmount.setSummary(String.valueOf(bill.getAmount()));
    }

    protected String getWhat() {
        return editWhat.getText();
    }
    protected String getDate() {
        return sdf.format(editDate.getDate());
    }
    protected double getAmount() {
        if (editAmount.getText() == null || editAmount.getText().equals("")) {
            return 0.0;
        }
        return Double.valueOf(editAmount.getText());
    }
    protected long getPayerId() {
        if (editPayer.getValue() == null) {
            return 0;
        }
        return Long.valueOf(editPayer.getValue());
    }
    protected List<Long> getOwersIds() {
        Set<String> strValues =  editOwers.getValues();
        List<Long> owersIds = new ArrayList<>();
        for (String strValue : strValues) {
            owersIds.add(Long.valueOf(strValue));
        }
        return owersIds;
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
