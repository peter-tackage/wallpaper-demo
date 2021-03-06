package com.moac.android.wallpaperdemo;

import android.app.Application;
import android.app.Service;
import android.util.Log;

import com.moac.android.wallpaperdemo.module.AppModule;

import dagger.ObjectGraph;

public class WallpaperApplication extends Application {

    private static final String TAG = WallpaperApplication.class.getSimpleName();

    private ObjectGraph objectGraph;

    public static WallpaperApplication from(Service service) {
        return (WallpaperApplication) service.getApplication();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate() - start");
        Object prodModule = new AppModule(this);
        objectGraph = ObjectGraph.create(prodModule);
    }

    public void inject(Object object) {
        objectGraph.inject(object);
    }
}
