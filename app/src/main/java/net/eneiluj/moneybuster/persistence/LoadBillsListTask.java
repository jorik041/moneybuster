package net.eneiluj.moneybuster.persistence;

import android.content.Context;
import android.os.AsyncTask;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.Category;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.Item;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadBillsListTask extends AsyncTask<Void, Void, List<Item>> {

    private final Context context;
    private final BillsLoadedListener callback;
    private final Category category;
    private final CharSequence searchQuery;
    private final long projectId;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private java.text.DateFormat dateFormat;

    public LoadBillsListTask(@NonNull Context context, @NonNull BillsLoadedListener callback, @NonNull Category category, @Nullable CharSequence searchQuery, @NonNull Long projectId) {
        this.context = context;
        this.callback = callback;
        this.category = category;
        this.searchQuery = searchQuery;
        this.projectId = projectId;

        dateFormat = android.text.format.DateFormat.getDateFormat(context);
    }

    @Override
    protected List<Item> doInBackground(Void... voids) {
        List<DBBill> billList;
        MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(context);

        if (projectId != 0) {
            billList = db.searchBills(searchQuery, projectId);
        }
        else {
            billList = new ArrayList<>();
        }

        return fillListTitle(billList);
    }

    private DBBill colorTheBill(DBBill dbBill) {
        if (!TextUtils.isEmpty(searchQuery) && !searchQuery.toString().startsWith("+")
            &&  !searchQuery.toString().startsWith("-") && !searchQuery.toString().startsWith("@")
        ) {
            SpannableString spannableString = new SpannableString(dbBill.getWhat());
            Matcher matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(context, R.color.bg_attention)
                        ),
                        matcher.start(), matcher.end(), 0);
            }

            dbBill.setWhat(Html.toHtml(spannableString));

            String formattedDate;
            try {
                Date date = sdf.parse(dbBill.getDate());
                formattedDate = dateFormat.format(date);
            } catch (Exception e) {
                formattedDate = dbBill.getDate();
            }
            spannableString = new SpannableString(formattedDate);
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(context, R.color.bg_attention)
                        ),
                        matcher.start(), matcher.end(), 0);
            }

            dbBill.setDate(Html.toHtml(spannableString));

        }

        return dbBill;
    }

    @NonNull
    @WorkerThread
    private List<Item> fillListTitle(@NonNull List<DBBill> billList) {
        List<Item> itemList = new ArrayList<>();
        for (DBBill bill : billList) {
            if (category.memberName == null || category.memberId.equals(bill.getPayerId())) {
                itemList.add(colorTheBill(bill));
            }
        }
        return itemList;
    }

    @Override
    protected void onPostExecute(List<Item> ljItems) {
        callback.onBillsLoaded(ljItems, category.memberName == null);
    }

    public interface BillsLoadedListener {
        void onBillsLoaded(List<Item> ljItems, boolean showCategory);
    }
}
