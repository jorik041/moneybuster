package net.eneiluj.moneybuster.model;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.activity.BillsListViewActivity;
import net.eneiluj.moneybuster.android.ui.TextDrawable;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;


public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = ItemAdapter.class.getSimpleName();

    private static final int section_type = 0;
    private static final int bill_type = 1;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    private final BillClickListener billClickListener;
    private List<Item> itemList;
    private List<Integer> selected;
    private MoneyBusterSQLiteOpenHelper db;
    private float avatarRadius;
    private SharedPreferences prefs;
    private boolean isProjectLocal;

    public ItemAdapter(@NonNull BillClickListener billClickListener, MoneyBusterSQLiteOpenHelper db) {
        this.itemList = new ArrayList<>();
        this.selected = new ArrayList<>();
        this.billClickListener = billClickListener;
        this.db = db;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(db.getContext());
        this.avatarRadius = db.getContext().getResources().getDimension(R.dimen.avatar_radius);
    }

    public void setProjectLocal(boolean loc) {
        this.isProjectLocal = loc;
    }

    /**
     * Updates the item list and notifies respective view to update.
     *
     * @param itemList List of items to be set
     */
    public void setItemList(@NonNull List<Item> itemList) {
        this.itemList = itemList;
        notifyDataSetChanged();
    }

    /**
     * Adds the given bill to the top of the list.
     *
     * @param bill that should be added.
     */
    public void add(@NonNull DBBill bill) {
        itemList.add(0, bill);
        notifyItemInserted(0);
        notifyItemChanged(0);
    }

    /**
     * Replaces a bill with an updated version
     *
     * @param bill with the changes.
     * @param position position in the list of the node
     */
    public void replace(@NonNull DBBill bill, int position) {
        itemList.set(position, bill);
        notifyItemChanged(position);
    }

    /**
     * Removes all items from the adapter.
     */
    public void removeAll() {
        itemList.clear();
        notifyDataSetChanged();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        if (viewType == section_type) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_bills_list_section_item, parent, false);
            return new SectionViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_bills_list_bill_item, parent, false);
            return new BillViewHolder(v);
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Item item = itemList.get(position);
        if (item.isSection()) {
            SectionItem section = (SectionItem) item;
            ((SectionViewHolder) holder).sectionTitle.setText(section.geTitle());
        } else {
            final DBBill bill = (DBBill) item;
            final BillViewHolder nvHolder = ((BillViewHolder) holder);
            nvHolder.billSwipeable.setAlpha(1.0f);
            String whatPrefix = "";
            if ("c".equals(bill.getPaymentMode())) {
                whatPrefix += "\uD83D\uDCB3 ";
            }
            else if ("b".equals(bill.getPaymentMode())) {
                whatPrefix += "💵 ";
            }
            else if ("f".equals(bill.getPaymentMode())) {
                whatPrefix += "🎫 ";
            }
            if (bill.getCategoryId() == DBBill.CATEGORY_GROCERIES) {
                whatPrefix += "\uD83D\uDED2 ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_LEISURE) {
                whatPrefix += "\uD83C\uDF89 ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_RENT) {
                whatPrefix += "🏠 ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_BILLS) {
                whatPrefix += "\uD83C\uDF29 ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_CULTURE) {
                whatPrefix += "\uD83D\uDDFD ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_HEALTH) {
                whatPrefix += "\uD83D\uDC9A ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_TOOLS) {
                whatPrefix += "\uD83D\uDD28 ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_MULTIMEDIA) {
                whatPrefix += "\uD83D\uDCBB ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_CLOTHES) {
                whatPrefix += "\uD83D\uDC5A ";
            }
            else if (bill.getCategoryId() == DBBill.CATEGORY_REIMBURSEMENT) {
                whatPrefix += "\uD83D\uDCB0 ";
            }
            nvHolder.billTitle.setText(Html.fromHtml(whatPrefix + bill.getWhat()));

            if (selected.contains(position)) {
                nvHolder.avatar.setImageDrawable(ContextCompat.getDrawable(db.getContext(), R.drawable.ic_check_circle_gray_24dp));
            }
            else {
                try {
                    DBMember m = db.getMember(bill.getPayerId());
                    nvHolder.avatar.setImageDrawable(
                            TextDrawable.createNamedAvatar(
                                    m.getName(), avatarRadius,
                                    m.getR(), m.getG(), m.getB()
                            )
                    );
                } catch (NoSuchAlgorithmException e) {
                    nvHolder.avatar.setImageDrawable(null);
                }
            }

            setFormattedDate(nvHolder.billDate, bill.getDate());

            Log.d(TAG, "[get member of project " + bill.getProjectId() + " with remoteid : "+bill.getPayerId()+"]");
            double rAmount = Math.round(bill.getAmount() * 100.0 ) / 100.0;
            String subtitle = String.valueOf(rAmount);
            subtitle += " (" + db.getMember(bill.getPayerId()).getName();
            subtitle += " → ";
            for (long boRId : bill.getBillOwersIds()) {
                String name = db.getMember(boRId).getName();
                subtitle += name + ", ";
            }
            subtitle = subtitle.replaceAll(", $", "");
            subtitle += ")";

            nvHolder.billSubtitle.setText(Html.fromHtml(subtitle));

            nvHolder.syncIcon.setVisibility((isProjectLocal || bill.getState() == DBBill.STATE_OK) ? View.INVISIBLE : View.VISIBLE);

            String repeat = bill.getRepeat() == null ? DBBill.NON_REPEATED : bill.getRepeat();
            nvHolder.repeatIcon.setVisibility(DBBill.NON_REPEATED.equals(repeat) ? View.GONE : View.VISIBLE);

            if (selected.contains(position)) {
                nvHolder.billSwipeable.setBackgroundResource(R.color.bg_highlighted);
            }
            else {
                nvHolder.billSwipeable.setBackgroundResource(R.color.bg_normal);
            }
        }
    }

    private void setFormattedDate(TextView billDate, String stringDate) {
        try {
            Date date = sdf.parse(stringDate);
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(db.getContext());
            billDate.setText(Html.fromHtml(dateFormat.format(date)));
        } catch (Exception e) {
            billDate.setText(Html.fromHtml(stringDate));
        }
    }

    public boolean select(Integer position) {
        return !selected.contains(position) && selected.add(position);
    }

    public void clearSelection() {
        selected.clear();
    }

    @NonNull
    public List<Integer> getSelected() {
        return selected;
    }

    public boolean deselect(Integer position) {
        for (int i = 0; i < selected.size(); i++) {
            if (selected.get(i).equals(position)) {
                //position was selected and removed
                selected.remove(i);
                return true;
            }
        }
        // position was not selected
        return false;
    }

    public Item getItem(int billPosition) {
        if (billPosition >= 0 && billPosition < itemList.size()) {
            if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[GETITEM " + billPosition + "/"+itemList.size()+"]"); }
            return itemList.get(billPosition);
        }
        else {
            return null;
        }
    }

    public void remove(@NonNull Item item) {
        itemList.remove(item);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isSection() ? section_type : bill_type;
    }

    public interface BillClickListener {
        void onBillClick(int position, View v);

        boolean onBillLongClick(int position, View v);
    }

    public class BillViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
        public View billSwipeable;
        View billSwipeFrame;
        ImageView avatar;
        TextView billTextToggleLeft;
        ImageView billDeleteRight;
        TextView billTitle;
        TextView billDate;
        TextView billSubtitle;
        ImageView syncIcon;
        ImageView repeatIcon;

        private BillViewHolder(View v) {
            super(v);
            this.billSwipeFrame = v.findViewById(R.id.billSwipeFrame);
            this.billSwipeable = v.findViewById(R.id.billSwipeable);
            this.billTextToggleLeft = v.findViewById(R.id.billTextToggleLeft);
            this.billDeleteRight = v.findViewById(R.id.billDeleteRight);
            this.avatar = v.findViewById(R.id.avatar);
            this.billTitle = v.findViewById(R.id.billTitle);
            this.billDate = v.findViewById(R.id.billDate);
            this.billSubtitle = v.findViewById(R.id.billExcerpt);
            this.syncIcon = v.findViewById(R.id.syncIcon);
            this.repeatIcon = v.findViewById(R.id.repeatIcon);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            final int adapterPosition = getAdapterPosition();
            if (adapterPosition != NO_POSITION) {
                billClickListener.onBillClick(adapterPosition, v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            return billClickListener.onBillLongClick(getAdapterPosition(), v);
        }

        public void showSwipe(boolean left) {
            billTextToggleLeft.setVisibility(left ? View.VISIBLE : View.INVISIBLE);
            billDeleteRight.setVisibility(left ? View.INVISIBLE : View.VISIBLE);
            billSwipeFrame.setBackgroundResource(left ? R.color.bg_warning : R.color.bg_attention);
        }
    }

    public static class SectionViewHolder extends RecyclerView.ViewHolder {
        TextView sectionTitle;

        private SectionViewHolder(View view) {
            super(view);
            sectionTitle = view.findViewById(R.id.sectionTitle);
            //ButterKnife.bind(this, view);
        }
    }
}