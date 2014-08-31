package com.moac.android.wallpaperdemo.api.model;

import android.content.Context;
import android.test.AndroidTestCase;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import static com.google.common.io.Closeables.closeQuietly;
import static org.assertj.core.api.Assertions.assertThat;

// Sadly not using JUnit 4
public class ModelDeserializationTest extends AndroidTestCase {

    Gson gson;

    @Override
    public void setUp() {
        // This assumes default Gson is used by model classes.
        gson = new GsonBuilder().create();
    }

    @Override
    public void tearDown() {
        gson = null;
    }

    public void test_tracksJsonDeserialisation() {
        String json = readTestDataFile(getContext(), "tracks.json");
        Type collectionType = new TypeToken<List<Track>>() {
        }.getType();
        List<Track> tracks = gson.fromJson(json, collectionType);
        assertThat(tracks).isNotNull();
        assertThat(tracks).isNotEmpty();
        assertThat(tracks).hasSize(4);
    }

    public void test_trackSingleJsonDeserialisation() {
        String json = readTestDataFile(getContext(), "track_single.json");
        Type trackType = new TypeToken<Track>() {
        }.getType();

        Track track = gson.fromJson(json, trackType);
        assertThat(track).isNotNull();
        assertThat(track.getId()).isEqualTo(99801677l);
        assertThat(track.getTitle()).isEqualTo("Dj Niko Force");
        assertThat(track.getWaveformUrl()).isEqualTo("https://w1.sndcdn.com/sPNv4LFoR9b7_m.png");
        assertThat(track.getPermalinkUrl()).isEqualTo("http://soundcloud.com/niko-nikiosdj/dj-niko-force");

        User user = track.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo("18402377");
        assertThat(user.getUsername()).isEqualTo("NIV DJ [official]");
        assertThat(user.getUri()).isEqualTo("https://api.soundcloud.com/users/18402377");
    }

    private static String readTestDataFile(Context context, String filename) {
        InputStream inputStream = context.getClassLoader().getResourceAsStream(filename);

        if (inputStream == null)
            throw new IllegalArgumentException("Test data file not found on classpath: " + filename);

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Test data file could not be read: " + filename, ex);
        } finally {
            closeQuietly(inputStream);
        }
    }
}