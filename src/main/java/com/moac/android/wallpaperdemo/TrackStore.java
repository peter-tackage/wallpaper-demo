package com.moac.android.wallpaperdemo;

import android.util.Log;
import com.android.volley.NetworkError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.*;

/**
 * This class makes tradeoffs between initial bandwidth usage & battery usage.
 *
 * This naive implementation is biased in favour of preserving
 * battery life by attempting to fetch all returned images in
 * one sequence, so the bandwidth used it only limited by the API's
 * response paging (number of tracks) and the size of the images.
 *
 * Also with the post-processing of the waveform images, large batches
 * of images being processed will no doubt have a negative image
 * on performance. Also, perhaps the resultant data could be cache to reduce
 * CPU drain.
 *
 * The cache used by Volley is reasonably large and will probably
 * eliminate bandwidth usage once these initial requests have completed.
 *
 * I visually undersample the bitmap in the TrackDrawer - there's no need for high quality images.
 *
 * TODO Build requests into smaller batches to balance tradeoffs.
 * TODO Find out if I can request low quality waveform images
 * TODO Add methods to change query (must reinit before).
 * TODO Make body of Callback cancelable (extend with cancel() method)
 * TODO Image transform should not be on the main thread.
 * TODO What happens when this fail to retrieve anything???
 *
 * FIXME Remove usage of Google Guava's pseudo functional approach
 */
public class TrackStore {

    private static final String TAG = TrackStore.class.getSimpleName();

    public interface InitListener {
        public void isReady();
    }

    private final SoundCloudApi mApi;

    private final ImageLoader mImageLoader;
    private Map<String, String> mFilter;
    private InitListener mListener;
    private List<Track> mTracks;
    private Map<String, ImageLoader.ImageContainer> mPendingRequests;

    public TrackStore(SoundCloudApi api, ImageLoader _imageLoader,
                      InitListener listener, Map<String, String> _filter) {
        mApi = api;
        mImageLoader = _imageLoader;
        mFilter = _filter;
        mListener = listener;

        // Move this to somewhere better
        mPendingRequests = new HashMap<String, ImageLoader.ImageContainer>();
        mTracks = new ArrayList<Track>();

        initTrackModel();
    }

    /**
     * Invoked on start up and modification of key parameters.
     */
    private void initTrackModel() {
        Log.i(TAG, "initTrackModel() genre: " + mFilter);

        // Cancel requests and reset state.
        cancelPendingRequests();
        mTracks.clear();

        logTrackState("initTrackModel()");

        mApi.getTracks("electronic", "electronic", 0, new Callback<List<Track>>() {
            @Override
            public void success(List<Track> tracks, Response response) {
                Log.i(TAG, "Successfully retrieved tracks: " + tracks.size());
                mTracks = tracks;
                for(Track track : mTracks) {
                    Log.i(TAG, "success() - will get waveform URL: " + track.getWaveformUrl());
                    mPendingRequests.put(track.getWaveformUrl(), mImageLoader.get(track.getWaveformUrl(),
                      new WaveformResponseListener(new WaveformProcessor(), track)));
                }
                logTrackState("success()");
            }

            @Override
            public void failure(RetrofitError error) {
                Log.w(TAG, "Failed to retrieve tracks", error);
                mTracks = new ArrayList<Track>();
            }
        });
    }

    public boolean isReady() {
        Iterable<Track> readyTracks = Iterables.filter(mTracks, new Predicate<Track>() {
            @Override
            public boolean apply(Track track) {
                return track.getWaveformData() != null;
            }
        });
        return Iterables.size(readyTracks) > 0;
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
        if(tracks.length == 1) // FIXME Really?
            return tracks[0];

        Random random = new Random();
        int index = random.nextInt(tracks.length - 1);
        return tracks[index];
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
            Log.i(TAG, "onResponse() - isImmediate: " + _isImmediate);
            if(!_isImmediate) {
                Log.i(TAG, "onResponse() - ##### HIT THE NETWORK ####");
            }

            if(_imageContainer.getBitmap() != null) {
                Log.i(TAG, "onResponse() - extracting waveform data");
                // Is now finished
                mPendingRequests.remove(mWaveformTrack.getWaveformUrl());
                float[] waveformData = mTransformer.transform(_imageContainer.getBitmap());
                mWaveformTrack.setWaveformData(waveformData);  // FIXME Memory constraints?
                mListener.isReady();
            }
            logTrackState("onResponse()");
        }

        @Override
        public void onErrorResponse(VolleyError _volleyError) {
            Log.w(TAG, "WaveformResponseListener Error fetching waveform image", _volleyError.getCause());

            /**
             * Remove non-transient failures from the pending list, permanent failures
             * are removed from the pending list, so they are not re-requested.
             */

            // No idea why a TimeoutError is not considered a NetworkError!
            if(!(_volleyError instanceof NetworkError || _volleyError instanceof TimeoutError)) {
                Log.w(TAG, "Removing waveform request from list due to permanent error: "
                  + mWaveformTrack.getWaveformUrl(), _volleyError);
                mPendingRequests.remove(mWaveformTrack.getWaveformUrl());
                // FIXME This can result in concurrentmodiicationexception
                mTracks.remove(mWaveformTrack);
                Log.i(TAG, "onErrorResponse() - selecting from: " + mTracks.size() + " tracks");
            }
            logTrackState("onErrorResponse()");
        }
    }

    public void onDestroy() {
        cancelPendingRequests();
    }

    private void cancelPendingRequests() {
        logTrackState("cancelPendingRequests");
        for(ImageLoader.ImageContainer request : mPendingRequests.values()) {
            request.cancelRequest();
        }
        mPendingRequests.clear();
    }

    private void logTrackState(String at) {

        Iterable<Track> readyTracks = Iterables.filter(mTracks, new Predicate<Track>() {
            @Override
            public boolean apply(Track track) {
                return track.getWaveformData() != null;
            }
        });

        Log.i(TAG, at + " - pending requests: " + (mPendingRequests == null ? 0 : mPendingRequests.size()));
        Log.i(TAG, at + " - ready tracks: " + (readyTracks == null ? 0 : Iterables.size(readyTracks)));
        Log.i(TAG, at + " - total tracks: " + (mTracks == null ? 0 : mTracks.size()));
    }

}
