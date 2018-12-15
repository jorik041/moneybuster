package net.eneiluj.nextcloud.phonetrack.android.fragment.about;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.BuildConfig;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

public class AboutFragmentCreditsTab extends Fragment {

    @BindView(R.id.about_version)
    TextView aboutVersion;
    @BindView(R.id.about_maintainer)
    TextView aboutMaintainer;
    @BindView(R.id.about_translators)
    TextView aboutTranslators;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about_credits_tab, container, false);
        ButterKnife.bind(this, v);
        SupportUtil.setHtml(aboutVersion, R.string.about_version, "v" + BuildConfig.VERSION_NAME);
        SupportUtil.setHtml(aboutMaintainer, R.string.about_maintainer);
        SupportUtil.setHtml(aboutTranslators, R.string.about_translators_crowdin, getString(R.string.url_translations));
        return v;
    }
}