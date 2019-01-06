package net.eneiluj.moneybuster.android.fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.moneybuster.R;


import net.eneiluj.moneybuster.util.MoneyBuster;

public class PreferencesFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback{

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        caller.setPreferenceScreen(pref);
        return true;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootkey) {

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        Preference resetTrust = findPreference(getString(R.string.pref_key_reset_trust));
        resetTrust.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CustomCertManager.Companion.resetCertificates(getActivity());
                Toast.makeText(getActivity(), getString(R.string.settings_cert_reset_toast), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        final SwitchPreferenceCompat themePref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_theme));
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        Boolean darkTheme = sp.getBoolean(getString(R.string.pref_key_theme), false);

        setThemePreferenceSummary(themePref, darkTheme);
        themePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean darkTheme = (Boolean) newValue;
                MoneyBuster.setAppTheme(darkTheme);
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
                //System.out.println("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTHHH "+darkTheme);

                return true;
            }
        });

    }

    private void setThemePreferenceSummary(SwitchPreferenceCompat themePref, Boolean darkTheme) {
        if (darkTheme) {
            themePref.setSummary(getString(R.string.pref_value_theme_dark));
        } else {
            themePref.setSummary(getString(R.string.pref_value_theme_light));
        }
    }

}
