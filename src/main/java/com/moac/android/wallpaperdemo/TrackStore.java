package com.moac.android.wallpaperdemo;

import android.util.Log;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TrackStore {

    private static final String TAG = TrackStore.class.getSimpleName();

    private SoundCloudApi mApi;
    private List<Track> mTracks;
    private Random mRandom;

    private final Object LOCK = new Object();

    public TrackStore(SoundCloudApi _api) {
        mApi = _api;
        mTracks = Collections.emptyList();
        mRandom = new Random();
        buildTrackModel("electronic", 0);
    }

    public Track getRandomTrack() {
        synchronized(LOCK) {
            if(mTracks.isEmpty())
                return null;
            int index = mRandom.nextInt(mTracks.size() - 1);
            return mTracks.get(index);
        }
    }

    private void buildTrackModel(String _genre, int _offset) {
        Log.i(TAG, "buildTrackModel() genre: " + _genre);
        mApi.getTracks(_genre, _genre, _offset, new Callback<List<Track>>() {
            @Override
            public void success(List<Track> tracks, Response response) {
                Log.i(TAG, "Successfully retrieved tracks: " + tracks.size());
                synchronized(LOCK) {
                    mTracks = tracks;
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.w(TAG, "Failed to retrieve track", error);
                synchronized(LOCK) {
                    mTracks = Collections.emptyList();
                }
            }
        });
    }
}
