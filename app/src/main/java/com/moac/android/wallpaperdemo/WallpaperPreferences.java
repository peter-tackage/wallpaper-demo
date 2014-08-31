package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.content.SharedPreferences;

import com.moac.android.wallpaperdemo.module.ForApplication;

import javax.inject.Inject;

public class WallpaperPreferences {

    private static final String SEARCH_TERM_PREFERENCE = "search_term_preference";
    private static final String CHANGE_RATE_PREFERENCE = "change_rate_preference";
    private static final String RELOAD_RATE_PREFERENCE = "reload_rate_preference";
    private static final String PREFETCH_PREFERENCE = "prefetch_preference";

    private final Context appContext;
    private final SharedPreferences sharedPreferences;

    @Inject
    public WallpaperPreferences(@ForApplication Context appContext, SharedPreferences sharedPreferences) {
        this.appContext = appContext;
        this.sharedPreferences = sharedPreferences;
    }

    // integer-arrays don't work:  http://code.google.com/p/android/issues/detail?id=2096

    public String getSearchTerm() {
        return sharedPreferences.getString(SEARCH_TERM_PREFERENCE, appContext.getString(R.string.default_search_term));
    }

    public int getDrawRateInSeconds() {
        return Integer.parseInt(sharedPreferences.getString(CHANGE_RATE_PREFERENCE, appContext.getString(R.string.default_change_rate)));
    }

    public int getReloadRateInSeconds() {
        return Integer.parseInt(sharedPreferences.getString(RELOAD_RATE_PREFERENCE, appContext.getString(R.string.default_reload_rate)));
    }

    public int getPrefetchCount() {
        return Integer.parseInt(sharedPreferences.getString(PREFETCH_PREFERENCE, appContext.getString(R.string.default_prefetch)));
    }

    public void addChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void removeChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

}
