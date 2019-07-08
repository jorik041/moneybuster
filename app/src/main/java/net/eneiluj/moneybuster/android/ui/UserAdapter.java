package net.eneiluj.moneybuster.android.ui;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.eneiluj.moneybuster.R;

import java.security.NoSuchAlgorithmException;
import java.util.List;


public class UserAdapter extends ArrayAdapter<UserItem> {
    private static final String TAG = UserAdapter.class.getSimpleName();
    private float mAccountAvatarRadiusDimension;
    private final Activity mContext;
    private List<UserItem> mValues;

    public UserAdapter(Activity context, List<UserItem> values) {
        super(context, R.layout.user_item, values);
        this.mContext = context;
        this.mValues = values;
        this.mAccountAvatarRadiusDimension = context.getResources().getDimension(R.dimen.user_item_avatar_icon_radius);
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        UserViewHolderItem viewHolder;
        View view = convertView;

        if (view == null) {
            LayoutInflater inflater = mContext.getLayoutInflater();
            view = inflater.inflate(R.layout.user_item, parent, false);

            viewHolder = new UserViewHolderItem();
            viewHolder.avatar = view.findViewById(R.id.avatar);
            viewHolder.name = view.findViewById(R.id.name);

            view.setTag(viewHolder);
        } else {
            viewHolder = (UserViewHolderItem) view.getTag();
        }

        UserItem user = mValues.get(position);

        if (user != null) {
            viewHolder.name.setText(user.getName());
            try {
                viewHolder.avatar.setImageDrawable(TextDrawable.createNamedAvatar(user.getName(), mAccountAvatarRadiusDimension));
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "error creating avatar", e);
                viewHolder.avatar.setImageDrawable(null);
            }
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        UserViewHolderItem viewHolder;
        View view = convertView;

        if (view == null) {
            LayoutInflater inflater = mContext.getLayoutInflater();
            view = inflater.inflate(R.layout.user_item, parent, false);

            viewHolder = new UserViewHolderItem();
            viewHolder.avatar = view.findViewById(R.id.avatar);
            viewHolder.name = view.findViewById(R.id.name);

            view.setTag(viewHolder);
        } else {
            viewHolder = (UserViewHolderItem) view.getTag();
        }

        UserItem user = mValues.get(position);

        if (user != null) {
            viewHolder.name.setText(user.getName());
            try {
                viewHolder.avatar.setImageDrawable(TextDrawable.createNamedAvatar(user.getName(), mAccountAvatarRadiusDimension));
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "error creating avatar", e);
                viewHolder.avatar.setImageDrawable(null);
            }
        }

        return view;
    }

    /**
     * User ViewHolderItem to get smooth rendering.
     */
    private static class UserViewHolderItem {
        private ImageView avatar;
        private TextView name;
    }
}
