package com.moac.android.wallpaperdemo;

import android.util.Log;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.moac.android.wallpaperdemo.api.ApiRequest;
import com.moac.android.wallpaperdemo.api.TracksEndPoint;
import com.moac.android.wallpaperdemo.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrackStore {

    private static final String TAG = TrackStore.class.getSimpleName();

    private static Random sRNG = new Random();
    private List<Track> mTracks;

    public TrackStore() {
        // FIXME Remove hardcoding of genre
        buildTrackModel("electronic", 0);
    }

    public Track getRandomTrack() {
        if(mTracks.isEmpty())
            return null;
        int index = sRNG.nextInt(mTracks.size() - 1);
        return mTracks.get(index);
    }

    public void buildTrackModel(String _genre, int _offset) {
        Log.i(TAG, "buildTrackModel() genre: " + _genre);

        mTracks = new ArrayList<Track>();  // clear!
        ApiRequest<List<Track>> request = TracksEndPoint.getTracksByGenre(_genre, _offset);
        WallpaperApplication.getInstance().getApiClient().execute(request, new TracksResponseListener(), new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Log.w(TAG, "onErrorResponse() - " + volleyError.getMessage());
                Log.w(TAG, "onErrorResponse() - " + volleyError.networkResponse);
            }
        });
    }

    private class TracksResponseListener implements Response.Listener<List<Track>> {
        @Override
        public void onResponse(List<Track> tracks) {
            Log.i(TAG, "onResponse() got tracks count: " + tracks.size());
            mTracks = tracks;
        }
    }
}
