package net.eneiluj.ihatemoney.model;

import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.android.activity.BillsListViewActivity;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = ItemAdapter.class.getSimpleName();

    private static final int section_type = 0;
    private static final int bill_type = 1;
    private final BillClickListener billClickListener;
    private List<Item> itemList;
    private boolean showCategory = true;
    private List<Integer> selected;
    private IHateMoneySQLiteOpenHelper db;
    private SharedPreferences prefs;

    public ItemAdapter(@NonNull BillClickListener billClickListener, IHateMoneySQLiteOpenHelper db) {
        this.itemList = new ArrayList<>();
        this.selected = new ArrayList<>();
        this.billClickListener = billClickListener;
        this.db = db;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(db.getContext());
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
     * @param bill log job that should be added.
     */
    public void add(@NonNull DBBill bill) {
        itemList.add(0, bill);
        notifyItemInserted(0);
        notifyItemChanged(0);
    }

    /**
     * Replaces a bill with an updated version
     *
     * @param bill     log job with the changes.
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
            String title = "[" + bill.getDate() + "]";
            title += " " + bill.getAmount();
            title += " (" + bill.getWhat() + ")";
            nvHolder.billTitle.setText(Html.fromHtml(title, Html.FROM_HTML_MODE_COMPACT));

            Log.d(TAG, "[get member of project " + bill.getProjectId() + " with remoteid : "+bill.getPayerId()+"]");
            String subtitle = db.getMember(bill.getPayerId(), bill.getProjectId()).getName();
            subtitle += " => ";
            for (long boRId : bill.getBillOwersIds()) {
                String name = db.getMember(boRId, bill.getProjectId()).getName();
                subtitle += name + ", ";
            }
            subtitle = subtitle.replaceAll(", $", "");

            nvHolder.billSubtitle.setText(Html.fromHtml(
                        subtitle, Html.FROM_HTML_MODE_COMPACT)
            );

            /*nvHolder.logjobEnabled.setChecked(bill.isEnabled());
            nvHolder.logjobEnabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    billClickListener.onLogjobEnabledClick(holder.getAdapterPosition(), view);
                }
            });*/

            nvHolder.infoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    billClickListener.onBillInfoButtonClick(holder.getAdapterPosition(), view);
                }
            });

            nvHolder.syncIcon.setVisibility(bill.getState() == DBBill.STATE_OK ? View.INVISIBLE : View.VISIBLE);

            //int nb = db.getLogjobLocationCount(bill.getId());
            // TODO show "needsync" if needed

            /*if (prefs.getBoolean(db.getContext().getString(R.string.pref_key_shownbsynced), false)) {
                int nbSent = db.getNbSync(bill.getId());
                nbTxt = (nbSent == 0) ? "" : String.valueOf(nbSent);
                if (BillsListViewActivity.DEBUG) {
                    Log.d(TAG, "[onBind : " + nbSent + " nbSync]");
                }
                nvHolder.nbSync.setText(nbTxt);
                visible = (nbSent == 0) ? View.INVISIBLE : View.VISIBLE;
                nvHolder.syncIcon.setVisibility(visible);
                nvHolder.nbSync.setVisibility(visible);
                //nvHolder.syncSpacer.setVisibility(View.VISIBLE);
            }
            else {
                nvHolder.syncIcon.setVisibility(View.GONE);
                nvHolder.nbSync.setVisibility(View.GONE);
                //nvHolder.syncSpacer.setVisibility(View.GONE);
            }*/
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

    public void setShowCategory(boolean showCategory) {
        this.showCategory = showCategory;
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

        //void onLogjobEnabledClick(int position, View v);

        void onBillInfoButtonClick(int position, View v);

        boolean onBillLongClick(int position, View v);
    }

    public class BillViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
        @BindView(R.id.billSwipeable)
        public View billSwipeable;
        View billSwipeFrame;
        TextView billTextToggleLeft;
        ImageView billDeleteRight;
        TextView billTitle;
        @BindView(R.id.billExcerpt)
        TextView billSubtitle;
        @BindView(R.id.infoButton)
        ImageButton infoButton;
        ImageView syncIcon;

        private BillViewHolder(View v) {
            super(v);
            this.billSwipeFrame = v.findViewById(R.id.billSwipeFrame);
            this.billSwipeable = v.findViewById(R.id.billSwipeable);
            this.billTextToggleLeft = v.findViewById(R.id.billTextToggleLeft);
            this.billDeleteRight = v.findViewById(R.id.billDeleteRight);
            this.billTitle = v.findViewById(R.id.billTitle);
            this.billSubtitle = v.findViewById(R.id.billExcerpt);
            this.infoButton = v.findViewById(R.id.infoButton);
            this.syncIcon = v.findViewById(R.id.syncIcon);
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
        @BindView(R.id.sectionTitle)
        TextView sectionTitle;

        private SectionViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}