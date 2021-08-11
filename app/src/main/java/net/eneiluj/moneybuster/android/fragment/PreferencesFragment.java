package net.eneiluj.moneybuster.android.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.service.SyncService;
import net.eneiluj.moneybuster.util.MoneyBuster;

import java.util.ArrayList;
import java.util.List;

import at.bitfire.cert4android.CustomCertManager;


public class PreferencesFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback{

    public final static String STOP_SYNC_SERVICE = "net.eneiluj.moneybuster.STOP_SYNC_SERVICE";
    public final static String CHANGE_SYNC_INTERVAL = "net.eneiluj.moneybuster.CHANGE_SYNC_INTERVAL";

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

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Preference resetTrust = findPreference(getString(R.string.pref_key_reset_trust));
        resetTrust.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CustomCertManager.Companion.resetCertificates(getActivity());
                Toast.makeText(getActivity(), getString(R.string.settings_cert_reset_toast), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        final CheckBoxPreference useServerColorPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_use_server_color));

        Boolean useServerColor = sp.getBoolean(getString(R.string.pref_key_use_server_color), false);
        if (useServerColor) {
            findPreference(getString(R.string.pref_key_color)).setVisible(false);
        }

        useServerColorPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean useServerColor = (Boolean) newValue;
                if (useServerColor) {
                    findPreference(getString(R.string.pref_key_color)).setVisible(false);
                }
                else {
                    findPreference(getString(R.string.pref_key_color)).setVisible(true);
                }
                if (getActivity() != null) {
                    getActivity().recreate();
                }
                return true;
            }
        });

        // night mode
        final Preference nightModePref = findPreference(getString(R.string.pref_key_night_mode));
        ListPreference nightModeListPref = (ListPreference) nightModePref;
        List<String> nightModeList = new ArrayList<>();
        nightModeList.add(getString(R.string.pref_value_theme_light));
        nightModeList.add(getString(R.string.pref_value_theme_dark));
        nightModeList.add(getString(R.string.pref_value_theme_system));
        CharSequence[] providerEntries = nightModeList.toArray(new CharSequence[nightModeList.size()]);
        nightModeListPref.setEntries(providerEntries);

        String nightModeValue = sp.getString(getString(R.string.pref_key_night_mode), getString(R.string.pref_value_night_mode_system));
        setNightModeSummary(nightModePref, nightModeValue);
        setNightModePreferenceIcon(nightModePref, nightModeValue);

        nightModePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int nightMode = Integer.parseInt((String) newValue);
                MoneyBuster.setAppTheme(nightMode);
                setNightModeSummary(nightModePref, (String) newValue);
                setNightModePreferenceIcon(nightModePref, (String) newValue);
                // no need to recreate the activity
                // this is done since AppCompat v1.1.0 according to https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#change-themes
                /*if (getActivity() != null) {
                    getActivity().recreate();
                }*/
                return true;
            }
        });

        findPreference(getString(R.string.pref_key_color)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showColorDialog(preference);
                return true;
            }
        });

        final EditTextPreference syncIntervalPref = (EditTextPreference) findPreference(getString(R.string.pref_key_sync_interval));
        String interval = sp.getString(getString(R.string.pref_key_sync_interval), "15");
        syncIntervalPref.setSummary(interval);
        syncIntervalPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                String newValueString = (String) newValue;
                long newInterval;
                try {
                    newInterval = Long.valueOf(newValueString);
                }
                catch (Exception e) {
                    showToast(getString(R.string.error_invalid_sync_interval), Toast.LENGTH_LONG);
                    return false;
                }
                if (newInterval > 1440 || newInterval < 5) {
                    showToast(getString(R.string.error_invalid_sync_interval), Toast.LENGTH_LONG);
                    return false;
                }
                else {
                    preference.setSummary((CharSequence) newValue);
                    Intent intent = new Intent(getContext(), SyncService.class);
                    intent.putExtra(CHANGE_SYNC_INTERVAL, newInterval);
                    getContext().startService(intent);
                    return true;
                }
            }

        });

        final CheckBoxPreference notifyNewPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_new));
        final CheckBoxPreference notifyUpdatedPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_updated));
        final CheckBoxPreference notifyDeletedPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_notify_deleted));

        final SwitchPreferenceCompat autostartPref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_autostart));

        final SwitchPreferenceCompat periodicalSyncPref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_periodical_sync));
        periodicalSyncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newPeriodicalSync = (Boolean) newValue;
                Log.d("preference", "PERIOSYNC "+newPeriodicalSync);
                if (newPeriodicalSync) {
                    syncIntervalPref.setVisible(true);
                    notifyNewPref.setVisible(true);
                    notifyUpdatedPref.setVisible(true);
                    notifyDeletedPref.setVisible(true);
                    autostartPref.setVisible(true);
                    // launch service
                    if (!SyncService.isRunning()) {
                        Log.d("preference", "not running => launch");
                        Intent intent = new Intent(getContext(), SyncService.class);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            getContext().startService(intent);
                        } else {
                            getContext().startForegroundService(intent);
                        }
                    }
                }
                else {
                    syncIntervalPref.setVisible(false);
                    notifyNewPref.setVisible(false);
                    notifyUpdatedPref.setVisible(false);
                    notifyDeletedPref.setVisible(false);
                    autostartPref.setVisible(false);
                    autostartPref.setChecked(false);
                    if (SyncService.isRunning()) {
                        Log.d("preference", "running => stop");
                        Intent intent = new Intent(getContext(), SyncService.class);
                        intent.putExtra(STOP_SYNC_SERVICE, true);
                        getContext().startService(intent);
                    }
                }
                return true;
            }
        });

        if (!periodicalSyncPref.isChecked()) {
            syncIntervalPref.setVisible(false);
            notifyNewPref.setVisible(false);
            notifyUpdatedPref.setVisible(false);
            notifyDeletedPref.setVisible(false);
            autostartPref.setVisible(false);
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void setNightModeSummary(Preference nightModePref, String nightModeValue) {
        if (nightModeValue.equals(getString(R.string.pref_value_night_mode_system))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_system));
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_no))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_light));
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_yes))) {
            nightModePref.setSummary(getString(R.string.pref_value_theme_dark));
        }
    }

    private void setNightModePreferenceIcon(Preference preference, String nightModeValue) {
        if (nightModeValue.equals(getString(R.string.pref_value_night_mode_system))) {
            preference.setIcon(R.drawable.ic_settings_grey600_24dp);
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_no))) {
            preference.setIcon(R.drawable.ic_sunny_grey_24dp);
        } else if (nightModeValue.equals(getString(R.string.pref_value_night_mode_yes))) {
            preference.setIcon(R.drawable.ic_brightness_2_grey_24dp);
        }
    }

    private void showColorDialog(final Preference preference) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View colorView = inflater.inflate(R.layout.dialog_color, null);

        int color = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getInt(getString(R.string.pref_key_color), Color.BLUE);
        final LobsterPicker lobsterPicker = colorView.findViewById(R.id.lobsterPicker);
        LobsterShadeSlider shadeSlider = colorView.findViewById(R.id.shadeSlider);

        lobsterPicker.addDecorator(shadeSlider);
        lobsterPicker.setColorHistoryEnabled(true);
        lobsterPicker.setHistory(color);
        lobsterPicker.setColor(color);

        new AlertDialog.Builder(getActivity())
                .setView(colorView)
                .setTitle(getString(R.string.settings_colorpicker_title))
                .setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ((ColorPreferenceCompat) preference).setValue(lobsterPicker.getColor());
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.simple_cancel), null)
                .show();
    }

}
