package com.moac.android.wallpaperdemo;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.moac.android.wallpaperdemo.api.ScRequestInterceptor;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.Closeables.closeQuietly;

public class WallpaperApplication extends Application {

    private static final String TAG = WallpaperApplication.class.getSimpleName();

    private static WallpaperApplication sInstance;

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
        //   applicationGraph = ObjectGraph.create(getModules().toArray());
        mSoundCloudApi = createApiClient();
    }

    private SoundCloudApi createApiClient() {
        InputStream inputStream = null;
        try {
            inputStream = getApplicationContext().getAssets().open("soundcloud.properties");
            Properties properties = new Properties();
            properties.load(inputStream);

            // Get and validate required API properties
            String scheme = checkNotNull(properties.getProperty("host.scheme"));
            String domain = checkNotNull(properties.getProperty("host.domain"));
            String clientId = checkNotNull(properties.getProperty("client.id"));
            String format = checkNotNull(properties.getProperty("format"));

            String apiUrl = new Uri.Builder().scheme(scheme).authority(domain).toString();

            // Add client id and format to query string for every request.
            RequestInterceptor ri = new ScRequestInterceptor(clientId, format);

            // Uses GSON mapping by default.
            RestAdapter restAdapter = new RestAdapter.Builder()
              .setServer(apiUrl)
              .setRequestInterceptor(ri)
              .setLogLevel(RestAdapter.LogLevel.FULL)
              .build();

            // Create an instance of the SoundCloud API interface
            return restAdapter.create(SoundCloudApi.class);
        } catch(IOException e) {
            Log.e(TAG, "Failed to read application properties");
            throw new RuntimeException(e);
        } finally {
            closeQuietly(inputStream);
        }
    }

    public static WallpaperApplication getInstance() { return sInstance; }

    public SoundCloudApi getSoundCloudApi() { return mSoundCloudApi; }

//    protected List<Object> getModules() {
//        return Arrays.<Object>asList(new AndroidModule(this), new ApiModule());
//    }

    //  ObjectGraph getApplicationGraph() {
    //      return applicationGraph;
    //  }
}
