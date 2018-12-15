package net.eneiluj.nextcloud.phonetrack.model;

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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = ItemAdapter.class.getSimpleName();

    private static final int section_type = 0;
    private static final int logjob_type = 1;
    private final LogjobClickListener logjobClickListener;
    private List<Item> itemList;
    private boolean showCategory = true;
    private List<Integer> selected;
    private PhoneTrackSQLiteOpenHelper db;
    private SharedPreferences prefs;

    public ItemAdapter(@NonNull LogjobClickListener logjobClickListener, PhoneTrackSQLiteOpenHelper db) {
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
            nvHolder.logjobSwipeable.setAlpha(1.0f);
            nvHolder.logjobTitle.setText(Html.fromHtml(logjob.getTitle(), Html.FROM_HTML_MODE_COMPACT));
            if (!logjob.getDeviceName().isEmpty()) {
                nvHolder.logjobSubtitle.setText(Html.fromHtml(
                        logjob.getDeviceName() + " => " + logjob.getUrl(), Html.FROM_HTML_MODE_COMPACT)
                );
            }
            else {
                nvHolder.logjobSubtitle.setText(Html.fromHtml(logjob.getUrl(), Html.FROM_HTML_MODE_COMPACT));
            }

            nvHolder.logjobEnabled.setChecked(logjob.isEnabled());
            nvHolder.logjobEnabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobEnabledClick(holder.getAdapterPosition(), view);
                }
            });

            nvHolder.infoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    logjobClickListener.onLogjobInfoButtonClick(holder.getAdapterPosition(), view);
                }
            });

            int nb = db.getLogjobLocationCount(logjob.getId());
            String nbTxt = (nb == 0) ? "" : String.valueOf(nb);
            nvHolder.nbNotSync.setText(nbTxt);
            int visible = (nb == 0) ? View.INVISIBLE : View.VISIBLE;
            nvHolder.nosyncIcon.setVisibility(visible);

            if (prefs.getBoolean(db.getContext().getString(R.string.pref_key_shownbsynced), false)) {
                int nbSent = db.getNbSync(logjob.getId());
                nbTxt = (nbSent == 0) ? "" : String.valueOf(nbSent);
                if (LoggerService.DEBUG) {
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
            }
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
            if (LoggerService.DEBUG) { Log.d(TAG, "[GETITEM " + logjobPosition + "/"+itemList.size()+"]"); }
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
        @BindView(R.id.logjobSwipeable)
        public View logjobSwipeable;
        View logjobSwipeFrame;
        TextView logjobTextToggleLeft;
        ImageView logjobDeleteRight;
        TextView logjobTitle;
        @BindView(R.id.logjobExcerpt)
        TextView logjobSubtitle;
        @BindView(R.id.nosyncIcon)
        ImageView nosyncIcon;
        @BindView(R.id.syncIcon)
        ImageView syncIcon;
        @BindView(R.id.logjobEnabled)
        Switch logjobEnabled;
        @BindView(R.id.nbNotSync)
        TextView nbNotSync;
        @BindView(R.id.nbSync)
        TextView nbSync;
        @BindView(R.id.syncSpacer)
        Space syncSpacer;
        @BindView(R.id.infoButton)
        ImageButton infoButton;

        private LogjobViewHolder(View v) {
            super(v);
            this.logjobSwipeFrame = v.findViewById(R.id.logjobSwipeFrame);
            this.logjobSwipeable = v.findViewById(R.id.logjobSwipeable);
            this.logjobTextToggleLeft = v.findViewById(R.id.logjobTextToggleLeft);
            this.logjobDeleteRight = v.findViewById(R.id.logjobDeleteRight);
            this.logjobTitle = v.findViewById(R.id.logjobTitle);
            this.logjobSubtitle = v.findViewById(R.id.logjobExcerpt);
            this.nosyncIcon = v.findViewById(R.id.nosyncIcon);
            this.syncIcon = v.findViewById(R.id.syncIcon);
            this.logjobEnabled = v.findViewById(R.id.logjobEnabled);
            this.nbNotSync = v.findViewById(R.id.nbNotSync);
            this.nbSync = v.findViewById(R.id.nbSync);
            this.syncSpacer = v.findViewById(R.id.syncSpacer);
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
            logjobTextToggleLeft.setVisibility(left ? View.VISIBLE : View.INVISIBLE);
            logjobDeleteRight.setVisibility(left ? View.INVISIBLE : View.VISIBLE);
            logjobSwipeFrame.setBackgroundResource(left ? R.color.bg_warning : R.color.bg_attention);
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