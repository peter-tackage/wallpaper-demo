package com.moac.android.wallpaperdemo;

import android.net.Uri;
import android.util.Log;
import com.moac.android.wallpaperdemo.api.ScRequestInterceptor;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import dagger.Provides;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.Closeables.closeQuietly;

@dagger.Module(injects = { WallpaperDemoService.class })
public class AppModule {

    private static final String TAG = AppModule.class.getSimpleName();
    private final WallpaperApplication application;

    public AppModule(WallpaperApplication application) {
        this.application = application;
    }

    @Provides
    RestAdapter provideRestAdapter() {
        RestAdapter.Builder builder = new RestAdapter.Builder();
        InputStream inputStream = null;
        try {
            inputStream = application.getAssets().open("soundcloud.properties");
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
            return new RestAdapter.Builder()
              .setServer(apiUrl)
              .setRequestInterceptor(ri)
              .setLogLevel(RestAdapter.LogLevel.FULL)
              .build();
        } catch(IOException e) {
            Log.e(TAG, "Failed to read SoundCloud API properties file");
            throw new RuntimeException(e);
        } finally {
            closeQuietly(inputStream);
        }
    }

    @Provides
    SoundCloudApi provideSoundCloudClient(RestAdapter restAdapter) {
        return restAdapter.create(SoundCloudApi.class);
    }
}