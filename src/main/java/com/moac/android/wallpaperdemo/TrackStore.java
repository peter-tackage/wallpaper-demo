package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.moac.android.wallpaperdemo.api.CancelableCallback;
import com.moac.android.wallpaperdemo.api.CancelableTarget;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.*;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * This class makes trade-offs between initial bandwidth usage & battery usage.
 *
 * This implementation is biased in favour of preserving
 * battery life by attempting to fetch all returned images in
 * one sequence, so the bandwidth used it only limited by the API's
 * response paging (number of tracks) and the size of the images.
 *
 * Of course, this assumes that the user actually likes the wallpaper and
 * doesn't remove it shortly after that and by doing so undo all the
 * hard work performed to save their precious battery.
 *
 * I visually under-sample the bitmap in the TrackDrawer - there's no need for high quality images.
 *
 * TODO Build requests into smaller batches to balance tradeoffs.
 * TODO Find out if I can request low quality waveform images
 * TODO Add methods to change query (must reinit before).
 * TODO Image transform should not be on the main thread.
 * TODO What happens when this fail to retrieve anything???
 * TODO Check about removing pending requests.
 *
 * FIXME Remove usage of Google Guava's pseudo functional approach
 */
public class TrackStore {

    //    // Define connectivity first.
//    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//    boolean isDataAllowed = connMgr.getBackgroundDataSetting();
//    // ICS will only ever return true for getBackgroundDataSetting().
//    // Apparently uses getActiveNetworkInfo
//    boolean ICSDataAllowed = connMgr.getActiveNetworkInfo() != null
//      && connMgr.getActiveNetworkInfo().isAvailable();

    private static final String TAG = TrackStore.class.getSimpleName();

    public interface InitListener {
        public void isReady();
    }

    private final SoundCloudApi mApi;
    private final Context mContext;
    private InitListener mListener;
    private List<Track> mTracks;
    private Map<String, CancelableTarget> mIncompleteRequests;

    public TrackStore(SoundCloudApi api, Context _context,
                      InitListener _listener) {
        mApi = api;
        mContext = _context;
        mListener = _listener;

        // Move this to somewhere better
        mIncompleteRequests = new HashMap<String, CancelableTarget>();
        mTracks = new ArrayList<Track>();

        initTrackModel();
    }

    /**
     * Invoked on start up and modification of key parameters.
     */
    private void initTrackModel() {
        Log.i(TAG, "initTrackModel()");

        // Cancel requests and reset state.
        cancelPendingRequests();
        mTracks.clear();

        logTrackState("initTrackModel()");

        mApi.getTracks("electronic", 10, new CancelableCallback<List<Track>>(new Callback<List<Track>>() {
            @Override
            public void success(List<Track> tracks, Response response) {
                Log.i(TAG, "Successfully retrieved tracks: " + tracks.size());

                // Only add those that appear to have usuable data
                for(Track track : tracks) {
                    Log.i(TAG, "success() - will get waveform URL: " + track.getWaveformUrl());
                    if(!isNullOrEmpty(track.getWaveformUrl())) {
                        // Note: Keep strong reference to Target so it doesn't GC
                        // See - https://github.com/square/picasso/issues/38
                        CancelableTarget target = new CancelableTarget(new TrackTarget(track, new WaveformProcessor()));
                        mIncompleteRequests.put(track.getWaveformUrl(), target);
                        Picasso.with(mContext).load(track.getWaveformUrl()).into(target);
                    } else {
                        Log.w(TAG, "NULL OR EMPTY WAVEFORM!!!!");
                    }
                }
                logTrackState("success() post");
            }

            @Override
            public void failure(RetrofitError error) {
                Log.w(TAG, "Failed to retrieve tracks", error);
                mTracks = new ArrayList<Track>();
            }
        }));
    }

    public Track getTrack() {
        logTrackState("getTrack()");

        Iterable<Track> readyTracks = Iterables.filter(mTracks, new Predicate<Track>() {
            @Override
            public boolean apply(Track track) {
                return track.getWaveformData() != null;
            }
        });
        Track[] tracks = Iterables.toArray(readyTracks, Track.class);
        if(tracks.length == 0)
            return null;
        return NumberUtils.getRandomElement(mTracks);
    }

    private class TrackTarget implements Target {

        private final Track mWaveformTrack;
        private final BitmapProcessor mProcessor;

        public TrackTarget(Track _track, BitmapProcessor _processor) {
            mWaveformTrack = _track;
            mProcessor = _processor;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            Log.i(TAG, "onBitmapLoaded() - extracting waveform data");
            mIncompleteRequests.remove(mWaveformTrack.getWaveformUrl());

            // Assign the transformed waveform data
            float[] waveformData = mProcessor.transform(bitmap);
            mWaveformTrack.setWaveformData(waveformData);  // FIXME Memory constraints?
            mTracks.add(mWaveformTrack);
            mListener.isReady();

            logTrackState("onBitmapLoaded() post");
        }

        @Override
        public void onBitmapFailed(Drawable drawable) {
            Log.w(TAG, "onError()");
            mIncompleteRequests.remove(mWaveformTrack.getWaveformUrl());
            mTracks.remove(mWaveformTrack);
            logTrackState("onError() post");
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // We don't care.
        }
    }

    public void onDestroy() {
        cancelPendingRequests();
    }

    private void cancelPendingRequests() {
        logTrackState("cancelPendingRequests");
        for(CancelableTarget target : mIncompleteRequests.values()) {
            target.cancel();
        }
        mIncompleteRequests.clear();
    }

    private void logTrackState(String at) {

        Iterable<Track> readyTracks = Iterables.filter(mTracks, new Predicate<Track>() {
            @Override
            public boolean apply(Track track) {
                return track.getWaveformData() != null;
            }
        });

        Log.i(TAG, at + " - pending requests: " + (mIncompleteRequests == null ? 0 : mIncompleteRequests.size()));
        Log.i(TAG, at + " - ready tracks: " + (readyTracks == null ? 0 : Iterables.size(readyTracks)));
        Log.i(TAG, at + " - total tracks: " + (mTracks == null ? 0 : mTracks.size()));
    }
}
