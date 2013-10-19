package com.moac.android.wallpaperdemo;

import android.util.Log;
import com.android.volley.NetworkError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.*;

/**
 * TODO Build requests into smaller batches to balance tradeoffs.
 *
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
 * TODO Find out if I can request low quality waveform images
 *
 * I average a lot of the data by massively undersampling the bitmap;
 * there's no need for high quality images.
 *
 * TODO Rather than have two lists, can we filter using Guava Predicates?
 * TODO Add methods to change query (must reinit before).
 * TODO Make body of Callback cancelable (extend with cancel() method)
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
    private List<Track> mPendingTracks; // This is to allow batching, later.
    private Map<String, ImageLoader.ImageContainer> mPendingRequests;

    public TrackStore(SoundCloudApi api, ImageLoader _imageLoader,
                      InitListener listener, Map<String, String> _filter) {
        mApi = api;
        mImageLoader = _imageLoader;
        mFilter = _filter;
        mListener = listener;

        // Move this to somewhere better
        mPendingRequests = new HashMap<String, ImageLoader.ImageContainer>();
        mPendingTracks = new ArrayList<Track>();
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
        mPendingRequests = new HashMap<String, ImageLoader.ImageContainer>();
        mPendingTracks = new ArrayList<Track>();
        mTracks = new ArrayList<Track>();

        logTrackState("initTrackModel()");

        mApi.getTracks("electronic", "electronic", 0, new Callback<List<Track>>() {
            @Override
            public void success(List<Track> tracks, Response response) {
                Log.i(TAG, "Successfully retrieved tracks: " + tracks.size());
                mPendingTracks = tracks;
                for(Track track : mPendingTracks) {
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

    public List<Track> getTracks() {
        return mTracks;
    }

    public Track getRandomTrack() {
        logTrackState("getRandomTrack()");
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

            // FIXME Image transform is on the main thread.

            // Is finished
            mPendingRequests.remove(mWaveformTrack.getWaveformUrl());
            mPendingTracks.remove(mWaveformTrack);

            if(_imageContainer.getBitmap() != null) {
                float[] waveformData = mTransformer.transform(_imageContainer.getBitmap());
                mWaveformTrack.setWaveformData(waveformData);    // FIXME Memory constraints?
                // Remove from the pending list, add to the ready list.
                mTracks.add(mWaveformTrack); // is now ready
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
                mPendingTracks.remove(mWaveformTrack);
                Log.i(TAG, "onErrorResponse() - selecting from: " + mTracks.size() + " tracks");
            }
            logTrackState("onErrorResponse()");
        }
    }

    private void cancelPendingRequests() {
        // TODO Cancel SC API Track JSON requests.
        logTrackState("cancelPendingRequests");
        for(ImageLoader.ImageContainer request : mPendingRequests.values()) {
            request.cancelRequest();
        }
    }

    private void logTrackState(String at) {
        Log.i(TAG, at + " - pending tracks: " + (mPendingTracks == null ? 0 : mPendingTracks.size()));
        Log.i(TAG, at + " - pending requests: " + (mPendingRequests == null ? 0 : mPendingRequests.size()));
        Log.i(TAG, at + " - ready tracks: " + (mTracks == null ? 0 : mTracks.size()));
    }
}
