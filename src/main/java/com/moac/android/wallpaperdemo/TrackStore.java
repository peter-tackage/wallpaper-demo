package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import com.squareup.picasso.Picasso;
import rx.Observable;
import rx.Subscription;
import rx.android.concurrency.AndroidSchedulers;
import rx.concurrency.Schedulers;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Action1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class makes trade-offs between initial bandwidth usage & battery usage.
 *
 * This implementation is biased in favour of preserving
 * battery life by attempting to fetch all available waveforms in
 * one sequence, rather than each waveform periodically. This batching of
 * waveform requests preserves battery in the long term by only powering the
 * radio once.
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
 * TODO What happens when this fail to retrieve anything???
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
    private InitListener mInitListener;
    private List<Track> mTracks;

    private Subscription mTrackSubscription;
    private String mGenre;
    private long mLimit;
    private Observable<Track> mTracksObservable = rx.Observable.create(new rx.Observable.OnSubscribeFunc<Track>() {
        @Override
        public Subscription onSubscribe(rx.Observer<? super Track> observer) {
            try {
                List<Track> tracks = mApi.getTracks(mGenre, mLimit);
                // FIXME Even if the unsubscribe, it will loop over all tracks: consider splitting up
                for(Track track : tracks) {
                    try {
                        Bitmap bitmap = Picasso.with(mContext).load(track.getWaveformUrl()).get();
                        float[] waveformData = new WaveformProcessor().transform(bitmap);
                        track.setWaveformData(waveformData);
                        observer.onNext(track);
                    } catch(IOException e) {
                        Log.w(TAG, "Failed to get Bitmap for track: " + track.getTitle());
                        // Don't bother telling observer, it's just one track that's failed.
                    }
                }
                // TODO Perhaps notify observer if we have no tracks.
                observer.onCompleted();
            } catch(Exception e) {
                observer.onError(e);
            }

            return Subscriptions.empty();
        }
    });

    public TrackStore(SoundCloudApi api, Context _context,
                      InitListener _listener) {
        mApi = api;
        mContext = _context;
        mInitListener = _listener;
        mTracks = new ArrayList<Track>();

        initTrackModel();
    }

    /**
     * Invoked on start up and modification of key parameters.
     */
    private void initTrackModel() {
        Log.i(TAG, "initTrackModel()");

        // Cancel any existing subscription and reset state.
        unsubscribe();
        mTracks.clear();

        mTrackSubscription = mTracksObservable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Action1<Track>() {
                         @Override
                         public void call(Track response) {
                             Log.i(TAG, "Emitted Track: " + response.getTitle());
                             mTracks.add(response);
                             mInitListener.isReady();
                         }
                     }, new Action1<Throwable>() {
                         @Override
                         public void call(Throwable error) {
                             Log.w(TAG, "Failed to fetch tracks");
                             // Have failed to initialise.
                             // TODO Depending on the error, start task to retry.
                         }
                     }
          );

        logTrackState("initTrackModel()");
    }

    public Track getTrack() {
        logTrackState("getTrack()");
        if(mTracks.size() == 0)
            return null;
        return NumberUtils.getRandomElement(mTracks);
    }

    public void onDestroy() {
        unsubscribe();
    }

    private void unsubscribe() {
        logTrackState("unsubscribe");
        if(mTrackSubscription != null) {
            mTrackSubscription.unsubscribe();
            mTrackSubscription = null;
        }
    }

    private void logTrackState(String at) {
        Log.i(TAG, at + " - total tracks: " + (mTracks == null ? 0 : mTracks.size()));
    }
}
