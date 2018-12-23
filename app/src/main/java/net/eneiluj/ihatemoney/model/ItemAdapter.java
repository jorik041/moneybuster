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
import android.widget.Space;
import android.widget.Switch;
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
    private static final int logjob_type = 1;
    private final LogjobClickListener logjobClickListener;
    private List<Item> itemList;
    private boolean showCategory = true;
    private List<Integer> selected;
    private IHateMoneySQLiteOpenHelper db;
    private SharedPreferences prefs;

    public ItemAdapter(@NonNull LogjobClickListener logjobClickListener, IHateMoneySQLiteOpenHelper db) {
        this.itemList = new ArrayList<>();
        this.selected = new ArrayList<>();
        this.logjobClickListener = logjobClickListener;
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
     * Adds the given logjob to the top of the list.
     *
     * @param logjob log job that should be added.
     */
    public void add(@NonNull DBLogjob logjob) {
        itemList.add(0, logjob);
        notifyItemInserted(0);
        notifyItemChanged(0);
    }

    /**
     * Replaces a logjob with an updated version
     *
     * @param logjob     log job with the changes.
     * @param position position in the list of the node
     */
    public void replace(@NonNull DBLogjob logjob, int position) {
        itemList.set(position, logjob);
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
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_logjobs_list_section_item, parent, false);
            return new SectionViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_logjobs_list_logjob_item, parent, false);
            return new LogjobViewHolder(v);
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
            final DBLogjob logjob = (DBLogjob) item;
            final LogjobViewHolder nvHolder = ((LogjobViewHolder) holder);
            nvHolder.billSwipeable.setAlpha(1.0f);
            nvHolder.billTitle.setText(Html.fromHtml(logjob.getTitle(), Html.FROM_HTML_MODE_COMPACT));
            if (!logjob.getDeviceName().isEmpty()) {
                nvHolder.billSubtitle.setText(Html.fromHtml(
                        logjob.getDeviceName() + " => " + logjob.getUrl(), Html.FROM_HTML_MODE_COMPACT)
                );
            }
            else {
                nvHolder.billSubtitle.setText(Html.fromHtml(logjob.getUrl(), Html.FROM_HTML_MODE_COMPACT));
            }

            /*nvHolder.logjobEnabled.setChecked(logjob.isEnabled());
            nvHolder.logjobEnabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobEnabledClick(holder.getAdapterPosition(), view);
                }
            });*/

            nvHolder.infoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobInfoButtonClick(holder.getAdapterPosition(), view);
                }
            });

            //int nb = db.getLogjobLocationCount(logjob.getId());
            // TODO show "needsync" if needed

            /*if (prefs.getBoolean(db.getContext().getString(R.string.pref_key_shownbsynced), false)) {
                int nbSent = db.getNbSync(logjob.getId());
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

    public Item getItem(int logjobPosition) {
        if (logjobPosition >= 0 && logjobPosition < itemList.size()) {
            if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[GETITEM " + logjobPosition + "/"+itemList.size()+"]"); }
            return itemList.get(logjobPosition);
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
        return getItem(position).isSection() ? section_type : logjob_type;
    }

    public interface LogjobClickListener {
        void onLogjobClick(int position, View v);

        void onLogjobEnabledClick(int position, View v);

        void onLogjobInfoButtonClick(int position, View v);

        boolean onLogjobLongClick(int position, View v);
    }

    public class LogjobViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener, View.OnClickListener {
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

        private LogjobViewHolder(View v) {
            super(v);
            this.billSwipeFrame = v.findViewById(R.id.billSwipeFrame);
            this.billSwipeable = v.findViewById(R.id.billSwipeable);
            this.billTextToggleLeft = v.findViewById(R.id.billTextToggleLeft);
            this.billDeleteRight = v.findViewById(R.id.billDeleteRight);
            this.billTitle = v.findViewById(R.id.billTitle);
            this.billSubtitle = v.findViewById(R.id.billExcerpt);
            this.infoButton = v.findViewById(R.id.infoButton);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            final int adapterPosition = getAdapterPosition();
            if (adapterPosition != NO_POSITION) {
                logjobClickListener.onLogjobClick(adapterPosition, v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            return logjobClickListener.onLogjobLongClick(getAdapterPosition(), v);
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