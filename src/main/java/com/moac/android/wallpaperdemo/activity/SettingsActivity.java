package com.moac.android.wallpaperdemo.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import com.moac.android.wallpaperdemo.R;

// TODO Make a HC+ version too.
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(
          getString(R.string.wallpaper_settings));
        addPreferencesFromResource(R.xml.settings);
        getPreferenceManager().getSharedPreferences()
          .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences()
          .unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {}
}
