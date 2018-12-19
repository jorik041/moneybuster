package net.eneiluj.ihatemoney.persistence;

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

import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.activity.BillsListViewActivity;
import net.eneiluj.ihatemoney.model.Category;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.Item;

public class LoadLogjobsListTask extends AsyncTask<Void, Void, List<Item>> {

    private final Context context;
    private final LogjobsLoadedListener callback;
    private final Category category;
    private final CharSequence searchQuery;
    public LoadLogjobsListTask(@NonNull Context context, @NonNull LogjobsLoadedListener callback, @NonNull Category category, @Nullable CharSequence searchQuery) {
        this.context = context;
        this.callback = callback;
        this.category = category;
        this.searchQuery = searchQuery;
    }

    @Override
    protected List<Item> doInBackground(Void... voids) {
        List<DBLogjob> logjobList;
        IHateMoneySQLiteOpenHelper db = IHateMoneySQLiteOpenHelper.getInstance(context);
        logjobList = db.searchLogjobs(searchQuery, null);

        return fillListTitle(logjobList);
        /*if (category.category == null) {
            return fillListByTime(logjobList);
        } else {
            return fillListByCategory(logjobList);
        }*/
    }

    private DBLogjob colorTheLogjob(DBLogjob dbLogjob) {
        if (!TextUtils.isEmpty(searchQuery)) {
            SpannableString spannableString = new SpannableString(dbLogjob.getTitle());
            Matcher matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(context, R.color.bg_attention)
                        ),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setTitle(Html.toHtml(spannableString, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));
            // TODO search by sub title
            /*spannableString = new SpannableString(dbLogjob.getCategory());
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.primary_dark)),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setCategory(Html.toHtml(spannableString));
            */
            spannableString = new SpannableString(dbLogjob.getUrl());
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(context, R.color.bg_attention)
                        ),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setUrl(Html.toHtml(spannableString, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));

            spannableString = new SpannableString(dbLogjob.getDeviceName());
            matcher = Pattern.compile("(" + searchQuery + ")", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(context, R.color.bg_attention)
                        ),
                        matcher.start(), matcher.end(), 0);
            }

            dbLogjob.setDeviceName(Html.toHtml(spannableString, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));
        }

        return dbLogjob;
    }

    @NonNull
    @WorkerThread
    private List<Item> fillListTitle(@NonNull List<DBLogjob> logjobList) {
        List<Item> itemList = new ArrayList<>();
        for (DBLogjob logjob : logjobList) {
            if (category.favorite != null && category.favorite && logjob.isEnabled()) {
                itemList.add(colorTheLogjob(logjob));
            }
            else if (category.category == BillsListViewActivity.CATEGORY_PHONETRACK && !logjob.getToken().isEmpty() && !logjob.getDeviceName().isEmpty()) {
                itemList.add(colorTheLogjob(logjob));
            }
            else if (category.category == BillsListViewActivity.CATEGORY_CUSTOM && logjob.getToken().isEmpty() && logjob.getDeviceName().isEmpty()) {
                itemList.add(colorTheLogjob(logjob));
            }
            else if (category.favorite == null && category.category == null) {
                itemList.add(colorTheLogjob(logjob));
            }
        }
        return itemList;
    }

    @Override
    protected void onPostExecute(List<Item> ljItems) {
        callback.onLogjobsLoaded(ljItems, category.category == null);
    }

    public interface LogjobsLoadedListener {
        void onLogjobsLoaded(List<Item> ljItems, boolean showCategory);
    }
}
