package net.eneiluj.moneybuster.android.fragment.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import net.eneiluj.moneybuster.BuildConfig;
import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.util.SupportUtil;

//import android.support.v4.app.Fragment;

public class AboutFragmentCreditsTab extends Fragment {

    TextView aboutVersion;
    TextView aboutMaintainer;
    TextView aboutTranslators;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about_credits_tab, container, false);
        aboutVersion = v.findViewById(R.id.about_version);
        aboutMaintainer = v.findViewById(R.id.about_maintainer);
        aboutTranslators = v.findViewById(R.id.about_translators);
        //ButterKnife.bind(this, v);
        SupportUtil.setHtml(aboutVersion, R.string.about_version, "v" + BuildConfig.VERSION_NAME);
        SupportUtil.setHtml(aboutMaintainer, R.string.about_maintainer);
        SupportUtil.setHtml(aboutTranslators, R.string.about_translators_crowdin, getString(R.string.url_translations));
        return v;
    }
}