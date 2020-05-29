package net.eneiluj.moneybuster.model;

import android.graphics.Color;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.ui.TextDrawable;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.util.ThemeUtils;

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
        public boolean isMember;

        public NavigationItem(@NonNull String id, @NonNull String label, @Nullable Integer count,
                              @DrawableRes int icon, boolean isMember) {
            this.id = id;
            this.label = label;
            this.count = count;
            this.icon = icon;
            this.isMember = isMember;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final View view;

        TextView name;
        TextView count;
        ImageView icon;

        private NavigationItem currentItem;

        ViewHolder(@NonNull View itemView, @NonNull final ClickListener clickListener) {
            super(itemView);
            view = itemView;
            name = view.findViewById(R.id.navigationItemLabel);
            count = view.findViewById(R.id.navigationItemCount);
            icon = view.findViewById(R.id.navigationItemIcon);
            //ButterKnife.bind(this, view);
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
            MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(icon.getContext());
            boolean isSelected = item.id.equals(selectedItem);
            //name.setText(item.label);
            count.setVisibility((item.count == null) ? View.GONE : View.VISIBLE);
            count.setText(String.valueOf(item.count));
            if (item.icon > 0) {
                if (item.isMember) {
                    try {
                        int width = ContextCompat.getDrawable(icon.getContext(), item.icon).getMinimumWidth();
                        int height = ContextCompat.getDrawable(icon.getContext(), item.icon).getMinimumHeight();
                        DBMember member = db.getMember(Long.valueOf(currentItem.id));
                        Drawable td;
                        if (member.getAvatar() != null && !member.getAvatar().equals("")) {
                            // TODO adapt to be able to set size of bitmap (independently from loaded image size)
                            td = ThemeUtils.getMemberAvatarDrawable(
                                    db.getContext(), member.getAvatar(), !member.isActivated()
                            );
                            icon.setPadding(width / 4, height / 4, width / 8, 0);
                        } else {
                            td = TextDrawable.createNamedAvatar(
                                    member.getName(), width / 2,
                                    member.getR(), member.getG(), member.getB(),
                                    !member.isActivated()
                            );
                            icon.setPadding(width / 4, height / 2, 0, 0);
                        }
                        icon.setImageDrawable(td);
                    } catch (NoSuchAlgorithmException e) {
                        Log.v(getClass().getSimpleName(), "error creating avatar", e);
                        icon.setImageDrawable(
                                ContextCompat.getDrawable(icon.getContext(), item.icon)
                        );
                    }
                } else {
                    icon.setImageDrawable(
                            ContextCompat.getDrawable(icon.getContext(), item.icon)
                    );
                    icon.setPadding(0, 0, 0, 0);
                }

                icon.setVisibility(View.VISIBLE);

            } else {
                icon.setVisibility(View.INVISIBLE);
            }
            //view.setBackgroundColor(isSelected ? view.getResources().getColor(R.color.bg_highlighted) : Color.TRANSPARENT);
            view.setBackgroundColor(isSelected ? ContextCompat.getColor(view.getContext(), R.color.bg_highlighted) : Color.TRANSPARENT);
            //int textColor = view.getResources().getColor(isSelected ? R.color.primary_dark : R.color.fg_default);
            //int textColor = ContextCompat.getColor(view.getContext(), isSelected ? R.color.primary : R.color.fg_default);

            SpannableString spannableString = new SpannableString(item.label);
            Matcher matcher = Pattern.compile("\\((\\+\\d*\\.?\\d*)\\)", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(view.getContext(), R.color.green)
                        ),
                        matcher.start()+1, matcher.end()-1, 0);
            }
            matcher = Pattern.compile("\\((-\\d*\\.?\\d*)\\)", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(view.getContext(), R.color.red)
                        ),
                        matcher.start()+1, matcher.end()-1, 0);
            }
            matcher = Pattern.compile("\\((0\\.00)\\)", Pattern.CASE_INSENSITIVE).matcher(spannableString);
            while (matcher.find()) {
                spannableString.setSpan(
                        new ForegroundColorSpan(
                                //context.getResources().getColor(R.color.primary_dark)
                                ContextCompat.getColor(view.getContext(), R.color.primary_light)
                        ),
                        matcher.start()+1, matcher.end()-1, 0);
            }
            name.setText(spannableString, TextView.BufferType.SPANNABLE);

            int textColor = ContextCompat.getColor(view.getContext(), R.color.fg_default);
            name.setTextColor(textColor);
            count.setTextColor(textColor);
            if (!item.isMember) {
                icon.setColorFilter(isSelected ? textColor : Color.GRAY);
            }

            if (isSelected) {
                name.setPaintFlags(Paint.FAKE_BOLD_TEXT_FLAG);
                count.setPaintFlags(Paint.FAKE_BOLD_TEXT_FLAG);
            }
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
