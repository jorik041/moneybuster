package net.eneiluj.moneybuster.android.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import java.util.List;


public class ProjectAdapter extends ArrayAdapter<DBProject> {
    private final Activity mContext;
    private final List<DBProject> mValues;
    private final int checkedItem;

    public ProjectAdapter(Activity context, List<DBProject> values, int checkedItem) {
        super(context, R.layout.user_item, values);
        this.mContext = context;
        this.mValues = values;
        this.checkedItem = checkedItem;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        ProjectViewHolderItem viewHolder;
        View view = convertView;

        if (view == null) {
            LayoutInflater inflater = mContext.getLayoutInflater();
            view = inflater.inflate(R.layout.project_item, parent, false);

            viewHolder = new ProjectViewHolderItem();
            viewHolder.layout = view.findViewById(R.id.projectItemLayout);
            viewHolder.icon = view.findViewById(R.id.icon);
            viewHolder.name = view.findViewById(R.id.name);

            view.setTag(viewHolder);
        } else {
            viewHolder = (ProjectViewHolderItem) view.getTag();
        }

        if (position == checkedItem) {
            viewHolder.layout.setBackgroundColor(mContext.getResources().getColor(R.color.bg_highlighted));
        }

        DBProject project = mValues.get(position);

        if (project != null) {
            if (project.getName() == null || project.getServerUrl() == null || project.isLocal()) {
                viewHolder.name.setText(project.getRemoteId());
            } else {
                String text = project.getName()
                    + "\n(" + project.getRemoteId() + "@"
                    + project.getServerUrl()
                    .replace("https://", "")
                    .replace("http://", "")
                    .replace("/index.php/apps/cospend", "")
                    + ")";
                viewHolder.name.setText(text);
            }
            if (project.isLocal()) {
                viewHolder.icon.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_phone_android_grey_24dp));
            } else if (ProjectType.COSPEND.equals(project.getType())) {
                viewHolder.icon.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_cospend_grey_24dp));
            } else if (ProjectType.IHATEMONEY.equals(project.getType())) {
                viewHolder.icon.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_ihm_grey_24dp));
            }
        }

        return view;
    }

    /**
     * User ViewHolderItem to get smooth rendering.
     */
    private static class ProjectViewHolderItem {
        private ImageView icon;
        private TextView name;
        private LinearLayout layout;
    }
}
