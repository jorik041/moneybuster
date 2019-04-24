package net.eneiluj.moneybuster.android.fragment;

import androidx.appcompat.app.AlertDialog;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditBillFragment extends Fragment {

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

    protected EditText editWhat;
    protected EditText editDate;
    protected Spinner editPayer;
    protected EditText editAmount;
    protected LinearLayout owersLayout;

    private Calendar calendar;
    private DatePickerDialog datePickerDialog;

    private List<DBMember> memberList;
    private List<String> memberNameList;
    private List<String> memberIdList;

    private Map<Long, CheckBox> owerCheckboxes;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    private SimpleDateFormat sdf;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_edit_bill_form, container, false);
        editWhat = view.findViewById(R.id.editWhat);
        editAmount = view.findViewById(R.id.editAmount);
        editDate = view.findViewById(R.id.editDate);
        editDate.setFocusable(false);
        editPayer = view.findViewById(R.id.editPayerSpinner);
        owersLayout = view.findViewById(R.id.owerListLayout);

        Button bAll = view.findViewById(R.id.owerAllButton);
        bAll.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                for (Map.Entry<Long, CheckBox> entry : owerCheckboxes.entrySet()) {
                    entry.getValue().setChecked(true);
                }
            }
        });
        Button bNone = view.findViewById(R.id.owerNoneButton);
        bNone.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                for (Map.Entry<Long, CheckBox> entry : owerCheckboxes.entrySet()) {
                    entry.getValue().setChecked(false);
                }
            }
        });

        calendar = Calendar.getInstance();

        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {

            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear,
                                  int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, monthOfYear);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateLabel();

            }

        };

        datePickerDialog = new DatePickerDialog(EditBillFragment.this.getContext(), date, calendar
                .get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)) {

            @Override
            public void onDateChanged(DatePicker view,
                                      int year,
                                      int month,
                                      int dayOfMonth) {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateLabel();
                datePickerDialog.dismiss();
            }
        };


        editDate.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                datePickerDialog.show();
            }
        });

        return view;
    }

    private void updateLabel() {
        editDate.setText(sdf.format(calendar.getTime()));
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

            // update last payer id
            db.updateProject(bill.getProjectId(), null, null, null, newPayerId);

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

        // build ower list
        owerCheckboxes = new HashMap<>();
        for (DBMember member : memberList) {
            if (member.isActivated()) {
                View row = LayoutInflater.from(getContext()).inflate(R.layout.ower_row, null);

                CheckBox cb = row.findViewById(R.id.owerBox);
                int owerIndex = bill.getBillOwersIds().indexOf(member.getId());
                if (bill.getId() == 0 || owerIndex != -1) {
                    cb.setChecked(true);
                }
                cb.setText(member.getName());
                cb.setTextColor(ContextCompat.getColor(owersLayout.getContext(), R.color.fg_default));
                owerCheckboxes.put(member.getId(), cb);

                owersLayout.addView(row);
            }
        }

        // build payer list
        if (memberNameList.size() > 0) {
            String[] memberNameArray = memberNameList.toArray(new String[memberNameList.size()]);
            String[] memberIdArray = memberIdList.toArray(new String[memberNameList.size()]);

            ArrayList<Map<String, String>> data = new ArrayList<>();
            for (int i=0; i < memberNameList.size(); i++) {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("name", memberNameList.get(i));
                hashMap.put("id", memberIdList.get(i));
                data.add(hashMap);
            }
            String[] from = {"name", "id"};
            int[] to = new int[] { android.R.id.text1 };
            SimpleAdapter simpleAdapter = new SimpleAdapter(this.getContext(), data, android.R.layout.simple_spinner_item, from, to);
            simpleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            editPayer.setAdapter(simpleAdapter);
            editPayer.getSelectedItemPosition();

            // set selected value for payer
            if (bill.getPayerId() != 0) {
                String payerId = String.valueOf(bill.getPayerId());
                // if the id is not found, it means the user is disabled
                int payerIndex = memberIdList.indexOf(payerId);
                if (payerIndex != -1) {
                    editPayer.setSelection(payerIndex);
                }
            }
            else {
                // this is a new bill, we try to put last payer id
                long lastPayerId = db.getProject(bill.getProjectId()).getLastPayerId();
                int payerIndex = memberIdList.indexOf(String.valueOf(lastPayerId));
                if (payerIndex != -1) {
                    editPayer.setSelection(payerIndex);
                }
            }
        }


        editWhat.setText(bill.getWhat());


        try {
            Date d = sdf.parse(bill.getDate());
            calendar.setTime(d);
            updateLabel();
            datePickerDialog.getDatePicker().updateDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
        }
        catch (ParseException e) {
            Log.d(getClass().getSimpleName(), "bad date "+bill.getDate());
        }

        editAmount.setText(String.valueOf(bill.getAmount()));
    }

    protected String getWhat() {
        return editWhat.getText().toString();
    }
    protected String getDate() {
        return editDate.getText().toString();
    }
    protected double getAmount() {
        String amount = editAmount.getText().toString();
        if (amount == null || amount.equals("")) {
            return 0.0;
        }
        try {
            return Double.valueOf(amount.replace(',', '.'));
        }
        catch (Exception e) {
            return 0.0;
        }
    }
    protected long getPayerId() {
        int i = editPayer.getSelectedItemPosition();
        if (i < 0) {
            return 0;
        }
        else {
            Map<String, String> item = (Map<String, String>) editPayer.getSelectedItem();
            return Long.valueOf(item.get("id"));
        }
    }
    protected List<Long> getOwersIds() {
        List<Long> owersIds = new ArrayList<>();
        for (Map.Entry<Long, CheckBox> entry : owerCheckboxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                owersIds.add(entry.getKey());
            }
            System.out.println("Key : " + entry.getKey() + " Value : " + entry.getValue().isChecked());
        }
        return owersIds;
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
