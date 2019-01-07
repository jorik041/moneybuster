package net.eneiluj.moneybuster.model;

import android.graphics.Color;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.moneybuster.R;

public class NavigationAdapter extends RecyclerView.Adapter<NavigationAdapter.ViewHolder> {

    public static class NavigationItem {
        @NonNull
        public String id;
        @NonNull
        public String label;
        @DrawableRes
        public int icon;
        @Nullable
        public Integer count;
        public boolean activated;

        public NavigationItem(@NonNull String id, @NonNull String label, @Nullable Integer count, @DrawableRes int icon, boolean activated) {
            this.id = id;
            this.label = label;
            this.count = count;
            this.icon = icon;
            this.activated = activated;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final View view;

        @BindView(R.id.navigationItemLabel)
        TextView name;

        @BindView(R.id.navigationItemCount)
        TextView count;

        @BindView(R.id.navigationItemIcon)
        ImageView icon;

        private NavigationItem currentItem;

        ViewHolder(@NonNull View itemView, @NonNull final ClickListener clickListener) {
            super(itemView);
            view = itemView;
            ButterKnife.bind(this, view);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickListener.onIconClick(currentItem);
                }
            });
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickListener.onItemClick(currentItem);
                }
            });
        }

        void assignItem(@NonNull NavigationItem item) {
            currentItem = item;
            boolean isSelected = item.id.equals(selectedItem);
            //name.setText(item.label);
            count.setVisibility((item.count == null) ? View.GONE : View.VISIBLE);
            count.setText(String.valueOf(item.count));
            if (item.icon > 0) {
                if (item.activated) {
                    icon.setImageDrawable(
                            ContextCompat.getDrawable(icon.getContext(), item.icon)
                    );
                }
                else {
                    icon.setImageDrawable(
                            ContextCompat.getDrawable(icon.getContext(), R.drawable.ic_lock_grey_24dp)
                    );
                }
                icon.setVisibility(View.VISIBLE);

            } else {
                icon.setVisibility(View.GONE);
            }
            //view.setBackgroundColor(isSelected ? view.getResources().getColor(R.color.bg_highlighted) : Color.TRANSPARENT);
            view.setBackgroundColor(isSelected ? ContextCompat.getColor(view.getContext(), R.color.bg_highlighted) : Color.TRANSPARENT);
            //int textColor = view.getResources().getColor(isSelected ? R.color.primary_dark : R.color.fg_default);
            int textColor = ContextCompat.getColor(view.getContext(), isSelected ? R.color.primary : R.color.fg_default);

            SpannableString spannableString = new SpannableString(item.label);
            Matcher matcher = Pattern.compile("(\\+\\d*\\.?\\d*)", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(view.getContext(), R.color.green)
                        ),
                        matcher.start(), matcher.end(), 0);
            }
            matcher = Pattern.compile("(-\\d*\\.?\\d*)", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(view.getContext(), R.color.red)
                        ),
                        matcher.start(), matcher.end(), 0);
            }
            name.setText(spannableString, TextView.BufferType.SPANNABLE);

            name.setTextColor(textColor);
            count.setTextColor(textColor);
            icon.setColorFilter(isSelected ? textColor : 0);
        }
    }

    public interface ClickListener {
        void onItemClick(NavigationItem item);

        void onIconClick(NavigationItem item);
    }

    @NonNull
    private List<NavigationItem> items = new ArrayList<>();
    private String selectedItem = null;
    @NonNull
    private ClickListener clickListener;

    public NavigationAdapter(@NonNull ClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_navigation, parent, false);
        return new ViewHolder(v, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.assignItem(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(@NonNull List<NavigationItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setSelectedItem(String id) {
        selectedItem = id;
        notifyDataSetChanged();
    }

    public String getSelectedItem() {
        return selectedItem;
    }
}
