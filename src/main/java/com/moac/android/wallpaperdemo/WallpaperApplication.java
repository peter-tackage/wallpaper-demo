package com.moac.android.wallpaperdemo;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.moac.android.wallpaperdemo.api.ScRequestInterceptor;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.util.BitmapLruCache;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class WallpaperApplication extends Application {

    private static final String TAG = WallpaperApplication.class.getSimpleName();

    private static WallpaperApplication sInstance;

    private ImageLoader mImageLoader;
    private SoundCloudApi mSoundCloudApi;

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
        mImageLoader = createImageLoader();
        mSoundCloudApi = createApiClient();
    }

    public static WallpaperApplication getInstance() { return sInstance; }

    public SoundCloudApi getSoundCloudApi() { return mSoundCloudApi; }

    public ImageLoader getImageLoader() { return mImageLoader; }

    private ImageLoader createImageLoader() {
        Log.i(TAG, "createImageLoader() - start");
        RequestQueue rq = Volley.newRequestQueue(getApplicationContext());
        return new ImageLoader(rq, new BitmapLruCache());
    }

    private SoundCloudApi createApiClient() {
        Log.i(TAG, "createApiClient() - start");

        InputStream inputStream = null;
        try {

            // Define SoundCloud API properties
            inputStream = getAssets().open("soundcloud.properties");
            Properties properties = new Properties();
            properties.load(inputStream);

            // Get and validate required API properties
            String apiScheme = getProperty(properties, "host.scheme");
            String apiDomain = getProperty(properties, "host.domain");
            String clientId = getProperty(properties, "client.id");
            String format = getProperty(properties, "format");

            Log.i(TAG, "createApiClient() - creating with format: " + format + " clientId: " + clientId + " API: " + apiScheme + "://" + apiDomain);

            String apiUrl = new Uri.Builder().scheme(apiScheme).authority(apiDomain).toString();

            // Add client id and format to query string for every request.
            RequestInterceptor ri = new ScRequestInterceptor(clientId, format);

            // Uses GSON JSON mapping by default.
            RestAdapter restAdapter = new RestAdapter.Builder()
              .setServer(apiUrl)
              .setRequestInterceptor(ri)
              .setLogLevel(RestAdapter.LogLevel.BASIC)
              .build();

            // Create an instance of the SoundCloud API interface
            return restAdapter.create(SoundCloudApi.class);
        } catch(IOException e) {
            Log.e(TAG, "Failed to initialise SoundCloud API Client", e);
            throw new RuntimeException("Unable to initialise SoundCloud API");
        } finally {
            closeQuietly(inputStream);
        }
    }

    private void closeQuietly(InputStream _stream) {
        if(_stream != null) {
            try {
                _stream.close();
            } catch(IOException e) {
                // ignore
            }
        }
    }

    private static String getProperty(Properties _properties, String _name) {
        String prop = _properties.getProperty(_name);
        validateNotNullorEmpty(prop, _name);
        return prop;
    }

    private static void validateNotNullorEmpty(String _value, String _name) {
        if(_value == null || _value.isEmpty())
            throw new IllegalArgumentException("Required parameter does not exist: " + _name);
    }
}
