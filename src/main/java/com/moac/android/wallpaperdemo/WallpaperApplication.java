package com.moac.android.wallpaperdemo;

import android.app.Application;
import android.util.Log;
import com.android.volley.RequestQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moac.android.wallpaperdemo.api.ApiClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class WallpaperApplication extends Application {

    private static final String TAG = WallpaperApplication.class.getSimpleName();

    private static WallpaperApplication sInstance;

    private SimpleVolley mVolley;
    private ApiClient mApiClient;
    private Gson mGson = new GsonBuilder().create();

    public WallpaperApplication() {
        super();
        Log.d(TAG, "Constructing WallpaperApplication");
        if(sInstance == null)
            sInstance = this; // init self singleton.
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate() - start");
        super.onCreate();
        mVolley = initVolley();
        mApiClient = initApiClient(mVolley.getRequestQueue(), mGson);
    }

    public static WallpaperApplication getInstance() { return sInstance; }
    public ApiClient getApiClient() { return mApiClient; }
    public SimpleVolley getVolley() { return mVolley; }

    private SimpleVolley initVolley() {
        Log.i(TAG, "initVolley() - start");
        return new SimpleVolley(getApplicationContext());
    }

    private ApiClient initApiClient(RequestQueue _requestQueue, Gson _gson) {
        Log.i(TAG, "initApiClient() - start");

        InputStream inputStream = null;
        try {
            inputStream = getAssets().open("soundcloud.properties");
            Properties properties = new Properties();
            properties.load(inputStream);

            String apiScheme = properties.getProperty("host.scheme");
            String apiDomain = properties.getProperty("host.domain");
            String clientId = properties.getProperty("client.id");
            Log.i(TAG, "initApiClient() - creating with clientId: " + clientId + " API: " + apiScheme + apiDomain);

            return new ApiClient(_requestQueue, _gson, apiScheme, apiDomain, clientId);
        } catch(IOException e) {
            Log.e(TAG, "Failed to initialise SoundCloud API Client", e);
            throw new RuntimeException("Unable to initialise SoundCloud API Client");
        } finally {
            closeQuietly(inputStream);
        }
    }

    private void closeQuietly(InputStream _stream) {
        if(_stream != null) {
            try {
                _stream.close();
            } catch(IOException e) { } // ignore
        }
    }
}
