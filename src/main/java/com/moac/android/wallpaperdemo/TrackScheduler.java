package com.moac.android.wallpaperdemo;

import android.util.Log;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class TrackScheduler extends PeriodicExecutorScheduler {

    private static final String TAG = TrackScheduler.class.getSimpleName();

    private final SoundCloudApi mApi;
    private final TrackListener mTrackListener;
    private List<Track> mTracks;
    private String mGenre;

    public TrackScheduler(SoundCloudApi _api, TrackListener _listener, long _interval, TimeUnit _units) {
        super(_interval, _units);
        mTracks = Collections.emptyList();
        mApi = _api;
        mTrackListener = _listener;
    }

    @Override
    protected void performTask() {
        Log.i(TAG, "performTask() - start");

        //    // Define connectivity first.
//    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//    boolean isDataAllowed = connMgr.getBackgroundDataSetting();
//    // ICS will only ever return true for getBackgroundDataSetting().
//    // Apparently uses getActiveNetworkInfo
//    boolean ICSDataAllowed = connMgr.getActiveNetworkInfo() != null
//      && connMgr.getActiveNetworkInfo().isAvailable();

        if(mTracks.isEmpty()) {
            Log.i(TAG, "performTask() - initialising model");
            initTrackModel(mGenre, 0);
        } else {
            notifyNextImage();
        }
    }

    public void setGenre(String _genre) {
        mGenre = _genre;
    }

    private void notifyNextImage() {
        Log.i(TAG, "notifyNextImage() - start");
        Track track = getRandomTrack();
        if(track != null) {
            Log.i(TAG, "fetchNextImage() - waveform URL: " + track.getWaveformUrl());
            WallpaperApplication.getInstance().getImageLoader().get(track.getWaveformUrl(),
              new WaveformResponseListener(new WaveformProcessor(), track));
        }
    }

    private Track getRandomTrack() {
        if(mTracks.isEmpty())
            return null;
        Random random = new Random();
        int index = random.nextInt(mTracks.size() - 1);
        return mTracks.get(index);
    }

    private class WaveformResponseListener implements ImageLoader.ImageListener {

        BitmapProcessor mTransformer;
        Track mWaveformTrack;

        public WaveformResponseListener(BitmapProcessor _transformer, Track _waveformTrack) {
            mTransformer = _transformer;
            mWaveformTrack = _waveformTrack;
        }

        @Override
        public void onResponse(ImageLoader.ImageContainer _imageContainer, boolean _isImmediate) {
            Log.i(TAG, "WaveformResponseListener onResponse() - isImmediate: " + _isImmediate);
            if(_imageContainer.getBitmap() != null) {
                // FIXME This transform is on the main thread.
                float[] waveformData = mTransformer.transform(_imageContainer.getBitmap());
                mWaveformTrack.setWaveformData(waveformData);    // FIXME Memory constraints?
                mTrackListener.onScheduleEvent(mWaveformTrack);
            }
        }

        @Override
        public void onErrorResponse(VolleyError _volleyError) {
            // Ignore for now
            Log.w(TAG, "WaveformResponseListener Error fetching waveform image", _volleyError.getCause());
        }
    }

    private void initTrackModel(String _genre, int _offset) {
        Log.i(TAG, "initTrackModel() genre: " + _genre);
        mApi.getTracks(_genre, _genre, _offset, new Callback<List<Track>>() {
            @Override
            public void success(List<Track> tracks, Response response) {
                Log.i(TAG, "Successfully retrieved tracks: " + tracks.size());
                mTracks = tracks;
                notifyNextImage(); // report new track.
            }

            @Override
            public void failure(RetrofitError error) {
                Log.w(TAG, "Failed to retrieve tracks", error);
                mTracks = Collections.emptyList();
            }
        });
    }
}
