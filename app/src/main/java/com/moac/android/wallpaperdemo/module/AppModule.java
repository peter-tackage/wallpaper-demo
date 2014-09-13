package com.moac.android.wallpaperdemo.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.moac.android.wallpaperdemo.R;
import com.moac.android.wallpaperdemo.WallpaperApplication;
import com.moac.android.wallpaperdemo.WallpaperDemoService;
import com.moac.android.wallpaperdemo.api.ScRequestInterceptor;
import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.util.Properties;

import javax.inject.Qualifier;
import javax.inject.Singleton;

import dagger.Provides;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

import static com.moac.android.wallpaperdemo.util.Preconditions.checkNotNull;
import static com.moac.android.wallpaperdemo.util.Streams.closeQuietly;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
        final String filename = "soundcloud.properties";
        InputStream inputStream = null;
        try {
            inputStream = application.getAssets().open(filename);
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            Log.e(TAG,
                    String.format("Failed to read SoundCloud API properties file: %s. Have you forgotten to add the file to your assets dir?", filename), e);
            throw new IllegalArgumentException(e);
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
                .setLogLevel(RestAdapter.LogLevel.BASIC)
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
        return Picasso.with(application);
    }

    @Provides
    @Singleton
    @ForApplication
    Context provideApplicationContext() {
        return application.getApplicationContext();
    }

    @Provides
    @Singleton
    SharedPreferences provideWallpaperSharedPreferences() {
        return application.getSharedPreferences(application.getString(R.string.wallpaper_settings_key), 0);
    }

    @Qualifier
    @Retention(RUNTIME)
    public static @interface ForApplication {
    }

    @Qualifier
    @Retention(RUNTIME)
    private static @interface ApiProperties {
    }
}
