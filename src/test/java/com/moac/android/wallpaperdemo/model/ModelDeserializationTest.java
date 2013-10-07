package com.moac.android.wallpaperdemo.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class ModelDeserializationTest {

    Gson gson;

    @Before
    public void setup() {
        // This assumes default Gson is used by model classes.
        gson = new GsonBuilder().create();
    }

    @After
    public void tearDown() {
        gson = null;
    }

    @Test
    public void testTracksJsonDeserialisation() throws IOException {
        String json = readTestDataFile("tracks.json");
        Type collectionType = new TypeToken<List<Track>>() {}.getType();
        List<Track> tracks = gson.fromJson(json, collectionType);
        assertNotNull(tracks);
        assertEquals(4, tracks.size());
    }

    @Test
    public void testTrackSingleJsonDeserialisation() throws IOException {
        String json = readTestDataFile("track_single.json");
        Type trackType = new TypeToken<Track>() {}.getType();

        Track track = gson.fromJson(json, trackType);
        assertNotNull(track);
        assertEquals("99801677", track.getId());
        assertEquals("Dj Niko Force", track.getTitle());
        assertEquals("https://w1.sndcdn.com/sPNv4LFoR9b7_m.png", track.getWaveformUrl());
        assertEquals("http://soundcloud.com/niko-nikiosdj/dj-niko-force", track.getPermalinkUrl());

        User user = track.getUser();
        assertNotNull(user);
        assertEquals("18402377", user.getId());
        assertEquals("NIV DJ [official]", user.getUsername());
        assertEquals("https://api.soundcloud.com/users/18402377", user.getUri());
    }

    private static String readTestDataFile(String _filename) {
        InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(_filename);

        if(inputStream == null)
            throw new IllegalArgumentException("Test data file not found on classpath: " + _filename);

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch(IOException ex) {
            throw new RuntimeException("Test data file could not be read: " + _filename, ex);
        }
    }
}
