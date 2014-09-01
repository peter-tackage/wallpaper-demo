package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.util.Log;

import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.api.model.Track;
import com.moac.android.wallpaperdemo.observable.TrackObservables;
import com.squareup.picasso.Picasso;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.CompositeSubscription;

import static com.moac.android.wallpaperdemo.util.DeviceUtils.isNetworkAvailable;

// TODO We should use a Subject for the mTrackList and let rx operators create the infinite sequence
public class TrackProvider {

    private static final String TAG = TrackProvider.class.getSimpleName();

    private final Context mContext;
    private final Picasso mPicasso;
    private final SoundCloudClient mApi;
    private LinkedList<Track> mTrackList;
    private Lock mLock;
    private Condition mTracksExist;

    public TrackProvider(Context context, SoundCloudClient api, Picasso picasso) {
        mContext = context;
        mApi = api;
        mPicasso = picasso;
        mTrackList = new LinkedList<Track>();
        mLock = new ReentrantLock();
        mTracksExist = mLock.newCondition();
    }

    public Subscription loadTracksPeriodically(final String searchTerm, final long limit, long reloadPeriodSec) {
        final CompositeSubscription subscription = new CompositeSubscription();
        subscription.add(AndroidSchedulers.mainThread().createWorker().schedulePeriodically(new Action0() {
            @Override
            public void call() {
                if (!isNetworkAvailable(mContext)) {
                    Log.i(TAG, "loadTracksPeriodically() - network unavailable");
                    return;
                }
                Log.i(TAG, "loadTracksPeriodically() - ### POTENTIAL NETWORK CALL ###");

                // Fetch a new set of track & waveforms from the API - observed in io thread
                subscription.add(TrackObservables.from(mApi.getTracks(searchTerm, limit), mPicasso).subscribe(
                        new Observer<Track>() {

                            @Override
                            public void onNext(Track response) {
                                Log.i(TAG, "loadTracksPeriodically() - Track received: " + response.getTitle());

                                // Keep some tracks in the list, let new ones slowly take their place.
                                // You get a mixture of tracks when result set size < limit.
                                if (mTrackList.size() >= limit) {
                                    mTrackList.removeFirst();
                                }
                                mTrackList.addLast(response);
                                mLock.lock();
                                try {
                                    mTracksExist.signalAll();
                                } finally {
                                    mLock.unlock();
                                }
                            }

                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.w(TAG, "loadTracksPeriodically() onError()", e);
                                // TODO Display message if nothing else to show.
                                // Note: There may still be tracks in mTracks
                            }
                        }
                ));

            }
        }, 0, reloadPeriodSec, TimeUnit.SECONDS)); // um, TimeUnit.MINUTES enum didn't exist until API Level 9!
        return subscription;
    }

    public void waitUntilReady() throws InterruptedException {
        Log.i(TAG, "waitUntilReady() - consumer");
        mLock.lock();
        try {
            while (mTrackList == null || mTrackList.isEmpty()) {
                mTracksExist.await();
            }
        } finally {
            mLock.unlock();
        }
    }

    public Track getNextTrack() {
        if (mTrackList == null || mTrackList.isEmpty())
            return null;

        // Quick little hack to make a pseudo-circular queue
        if (mTrackList.size() == 1)
            return mTrackList.getFirst();

        Track first = mTrackList.removeFirst();
        mTrackList.addLast(first);
        return mTrackList.getFirst();
    }

}
