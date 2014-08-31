package com.moac.android.wallpaperdemo.module;

import android.net.Uri;
import android.util.Log;

import com.moac.android.wallpaperdemo.WallpaperApplication;
import com.moac.android.wallpaperdemo.WallpaperDemoService;
import com.moac.android.wallpaperdemo.api.ScRequestInterceptor;
import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.inject.Singleton;

import dagger.Provides;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.Closeables.closeQuietly;

@dagger.Module(injects = {WallpaperDemoService.class})
public class AppModule {

    private static final String TAG = AppModule.class.getSimpleName();
    private final WallpaperApplication application;

    public AppModule(WallpaperApplication application) {
        this.application = application;
    }

    @Provides
    @ApiProperties
    Properties provideApiProperties() {
        InputStream inputStream = null;
        try {
            inputStream = application.getAssets().open("soundcloud.properties");
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read SoundCloud API properties file");
            throw new RuntimeException(e);
        } finally {
            closeQuietly(inputStream);
        }
    }

    @Provides
    @Singleton
    RestAdapter provideRestAdapter(@ApiProperties Properties apiProperties) {
        Log.i(TAG, "Providing Rest Adapter");

        // Get and validate required API properties
        String scheme = checkNotNull(apiProperties.getProperty("host.scheme"));
        String domain = checkNotNull(apiProperties.getProperty("host.domain"));
        String clientId = checkNotNull(apiProperties.getProperty("client.id"));

        String apiUrl = new Uri.Builder().scheme(scheme).authority(domain).toString();

        // Add client id and JSON format to query string for every request.
        RequestInterceptor ri = new ScRequestInterceptor(clientId, "json");

        // Uses GSON mapping by default.
        return new RestAdapter.Builder()
                .setEndpoint(apiUrl)
                .setRequestInterceptor(ri)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .build();
    }

    @Provides
    @Singleton
    SoundCloudClient provideSoundCloudClient(RestAdapter restAdapter) {
        Log.i(TAG, "Providing SoundCloud Client");
        return restAdapter.create(SoundCloudClient.class);
    }

    @Provides
    @Singleton
    Picasso providePicasso() {
        Log.i(TAG, "Providing Picasso");
        Picasso picasso = Picasso.with(application);
        picasso.setLoggingEnabled(true);
        return picasso;
    }

    private static @interface ApiProperties {
    }
}