package net.eneiluj.moneybuster.persistence;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.Category;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.Item;

public class LoadBillsListTask extends AsyncTask<Void, Void, List<Item>> {

    private final Context context;
    private final BillsLoadedListener callback;
    private final Category category;
    private final CharSequence searchQuery;
    private final long projectId;
    public LoadBillsListTask(@NonNull Context context, @NonNull BillsLoadedListener callback, @NonNull Category category, @Nullable CharSequence searchQuery, @NonNull Long projectId) {
        this.context = context;
        this.callback = callback;
        this.category = category;
        this.searchQuery = searchQuery;
        this.projectId = projectId;
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
        /*if (category.category == null) {
            return fillListByTime(logjobList);
        } else {
            return fillListByCategory(logjobList);
        }*/
    }

    private DBBill colorTheBill(DBBill dbBill) {
        if (!TextUtils.isEmpty(searchQuery)) {
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

            spannableString = new SpannableString(dbBill.getDate());
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
