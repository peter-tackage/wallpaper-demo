package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.api.model.Track;
import com.moac.android.wallpaperdemo.gfx.TrackDrawer;
import com.moac.android.wallpaperdemo.gfx.WaveformProcessor;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.moac.android.wallpaperdemo.util.DeviceUtils.isNetworkAvailable;

/**
 * This is a demo of a live wallpaper using data retrieved from the SoundCloud API.
 * It provides a rolling set of waveforms based on search query parameters, which are
 * periodically refreshed via an API query.
 * <p/>
 * It uses various strategies to find a balance between providing a vaguely interesting
 * user experience and not placing too much of a drain on device resources, such
 * as battery and data.
 * <p/>
 * The wallpaper operates by acquiring waveform and metadata from
 * the API via a period "producer" subscription which fetches a user configurable batch
 * of between 5-25 tracks.
 * <p/>
 * This data is then processed and stored as an in-memory model. At this point
 * there is no need to access the network until the next invocation. Until then, the
 * current set of images are displayed cyclically via a "consumer" subscription, keeping
 * the user entertained with the waveforms overlaying a range of handpicked background
 * colours, *most* of which don't look like day-old mustard spilt down the front
 * of an olive green jumper.
 * <p/>
 * The pre-fetching strategy, rather than more frequent periodic retrieval of
 * smaller amounts of data provides a battery saving in the long term. Refer to
 * http://developer.android.com/training/efficient-downloads/index.html
 * <p/>
 * In our case, the amount of data retrieved is relatively small by most standards
 * so there's no good reason not to prefetch as much as sensible.
 * <p/>
 * Live wallpaper guidelines strongly suggest not to use CPU when the wallpaper
 * is not visible. This definitely makes sense when the wallpaper is a fancy,
 * animated wallpaper, but less sense in our case. If we limited the wallpaper
 * to only updating when it was visible, it would be pretty boring. If we change
 * the image while the background is not visible, it arguably makes for a better
 * user experience; they get a new waveform more often! Naturally, this comes at
 * the cost of battery drain (although very minimal, it's a short, in-memory interaction).
 * <p/>
 * As a middle ground, we attempt to avoid repeated API calls when the wallpaper
 * is not visible. Once the wallpaper loses visibility, a deadline timer is
 * initiated which halts all wallpaper API calls when it expires. If visibility is
 * restored before the expiry, the timer is cancelled. If visibility is restored
 * after the expiry, the periodic fetching is restarted. The deadline time is
 * the period of the periodic fetching task. The cyclic displaying of images is
 * not affect by this as it is seen as lightweight.
 * <p/>
 * Things that aren't supported just yet -
 * <p/>
 * 1. Persistent caching other than that provided by the HTTP layer and Picasso's
 * own cache. This doesn't affect the wallpaper unless the user restarts their
 * phone in an area without a connection, in which they've no doubt got bigger
 * worries.
 * <p/>
 * 2. Listening to Android Network Status Broadcasts to determine if the wallpaper
 * can attempt to initialise the track list following a fetch failure. If the user starts
 * the wallpaper when they don't have an internet connection, it will fail to fetch
 * and won't retry until the next poll. There's no error message. Ideally, there would
 * be a stylized picture.
 * <p/>
 * 3. A set of constantly changing *new* tracks! It seems that without using the
 * search "offset" parameter users tend to get the same tracks for a given
 * query. This means that frequent refreshing of the track list if mostly pointless.
 * Perhaps the parameter's use could be introduced to provide a better "discover"
 * experience.
 * <p/>
 * Proper known issues:
 * <p/>
 * 1. I've have noticed that sometimes the periodic subscriptions aren't actually
 * unsubscribed via the onDestroy call. This can be seen in the logs -
 * <p/>
 * 10-31 00:49:58.538: DEBUG/WallpaperDemoService(4487): onDestroy()com.moac.android.wallpaperdemo.WallpaperDemoService$WallpaperEngine@b517e350
 * ...
 * 10-31 00:52:50.790: INFO/WallpaperDemoService(4487): draw() - start: com.moac.android.wallpaperdemo.WallpaperDemoService$WallpaperEngine@b517e350 on: Thread[main,5,main]
 * repeat...
 * <p/>
 * This causes repeated canvas lock errors in draw() as the two Engines fight for control.
 * I'll have to look into whether this is an RxJava issue, or perhaps I'm just not doing
 * it right.
 * <p/>
 * Someone else reported a similar issue - https://github.com/Netflix/RxJava/issues/431
 */
public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Inject
    SoundCloudClient mApi;
    @Inject
    Picasso mPicasso;
    @Inject
    WallpaperPreferences mWallpaperPreferences;

    @Override
    public Engine onCreateEngine() {
        WallpaperApplication app = WallpaperApplication.from(this);
        app.inject(this);
        return new WallpaperEngine();
    }

    // Dagger can't see this class, must DI into the Service instead
    protected class WallpaperEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

        private Subscription mProducerSubscription;
        private Subscription mWorkerSubscription;
        private Subscription mConsumerSubscription;
        private TrackDrawer mTrackDrawer;
        private LinkedList<Track> mTracks;
        private Track mCurrentTrack;

        final private Lock mLock;
        final private Condition mTracksExist;
        final private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

        // Shutdown when inactive
        private Runnable mDeadlineRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Unsubscribing from API due to inactivity");
                unsubscribeSafely(mProducerSubscription);
                mProducerSubscription = null;
            }
        };
        private Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Executing Draw Runnable");
                mCurrentTrack = getTrack();
                mTrackDrawer.setColor(NumberUtils.getRandomElement(mPrettyColors));
                draw(mTrackDrawer, mCurrentTrack);
            }
        };

        private boolean mIsDoubleTap;
        private final Runnable mDoubleTapTimeout = new Runnable() {
            @Override
            public void run() {
                mIsDoubleTap = false;
            }
        };

        private void validateDoubleTap() {
            mMainThreadHandler.removeCallbacks(mDoubleTapTimeout);
            mMainThreadHandler.postDelayed(mDoubleTapTimeout, 500);
        }

        private GestureDetector mDoubleTapDetector = new GestureDetector(WallpaperDemoService.this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        mIsDoubleTap = true;
                        validateDoubleTap();
                        return true;
                    }
                });

        // Get more at http://dribbble.com/colors/<value>
        private final Integer[] mPrettyColors =
                {0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
                        0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68, 0xFFFF6600,
                        0xFF669900, 0xFF66CCCC};

        // Debug info
        private int mDebugApiCalls = 0;
        private int mDebugBlockedApiCalls = 0;
        private int mDebugFailedApiCalls = 0;

        public WallpaperEngine() {
            mLock = new ReentrantLock();
            mTracksExist = mLock.newCondition();
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.i(TAG, "onCreate() - Creating new WallpaperEngine instance: " + this);
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);

            mTrackDrawer = new TrackDrawer(10, 10);
            mTracks = new LinkedList<Track>();
            mWallpaperPreferences.addChangeListener(this);

            // Start drawing
            startFromPreferences();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.d(TAG, "onSharedPreferenceChanged()");
            startFromPreferences();
        }

        private void startFromPreferences() {
            String searchTerms = mWallpaperPreferences.getSearchTerms();
            int drawRate = mWallpaperPreferences.getDrawRateInSeconds();
            int reloadRate = mWallpaperPreferences.getReloadRateInSeconds();
            int prefetchCount = mWallpaperPreferences.getPrefetchCount();
            start(searchTerms, drawRate, reloadRate, prefetchCount);
        }

        private void start(String searchTerms, int drawRate, int reloadRate, int prefetchCount) {
            String values = String.format("Search: %s, Draw Rate: %s, Reload Rate: %s, Prefetch: %s",
                    searchTerms, drawRate, reloadRate, prefetchCount);
            Log.i(TAG, "Starting Engine with configuration: " + values);

            // TODO We could be smart here - only restart the relevant components
            unsubscribeAll();
            mConsumerSubscription = getConsumerSubscription(drawRate);
            mProducerSubscription = getProducerSubscription(mApi, reloadRate, prefetchCount, searchTerms);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "onDestroy() - " + this);
            unsubscribeAll();
            mWallpaperPreferences.removeChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.v(TAG, "onSurfaceChanged() Current surface size: " + width + "," + height);
            // Redraw canvas. Called on orientation change.
            draw(mTrackDrawer, mCurrentTrack);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            mDoubleTapDetector.onTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z,
                                Bundle extras, boolean resultRequested) {
            if (action.equals(WallpaperManager.COMMAND_TAP) && mIsDoubleTap) {
                // Double tap => view track
                openUrl(mCurrentTrack.getPermalinkUrl());
                // We've handled the double tap, so reset flag
                mIsDoubleTap = false;
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            super.onVisibilityChanged(isVisible);
            /*
             * If the canvas is not visible, set a deadline for visibility that
             * if reached will cancel the subscription: don't download data that
             * is not likely to be seen
             */
            Log.d(TAG, "onVisibilityChanged() isVisible: " + isVisible);
            if (!isVisible) {
                Log.i(TAG, "Preparing API subscription for possible sleep");
                mMainThreadHandler.postDelayed(mDeadlineRunnable, TimeUnit.MILLISECONDS.
                        convert(mWallpaperPreferences.getReloadRateInSeconds(), TimeUnit.SECONDS));
            } else {
                // And we're back! Cancel the deadline, resubscribe if it expired.
                Log.i(TAG, "Cancelling API subscription sleep deadline");
                mMainThreadHandler.removeCallbacks(mDeadlineRunnable);
                if (mProducerSubscription == null) {
                    Log.i(TAG, "Restarting API subscription sleep deadline");
                    mProducerSubscription =
                            getProducerSubscription(mApi,
                                    mWallpaperPreferences.getReloadRateInSeconds()
                                    , mWallpaperPreferences.getPrefetchCount()
                                    , mWallpaperPreferences.getSearchTerms());
                }
            }
        }

        /*
         * Creates a periodic "producer" Subscription to get Tracks from the SoundCloud API
         */
        private Subscription getProducerSubscription(final SoundCloudClient api, int reloadRate,
                                                     final int limit, final String search) {
            Log.i(TAG, "getProducerSubscription() - reload: " + reloadRate + ", limit: " + limit + ", search: " + search);
            return AndroidSchedulers.mainThread().createWorker().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    if (!isNetworkAvailable(WallpaperDemoService.this)) {
                        Log.i(TAG, "getProducerSubscription() - network unavailable");
                        mDebugBlockedApiCalls++;
                        return;
                    }
                    Log.i(TAG, "getProducerSubscription() - ### POTENTIAL NETWORK CALL ###");
                    mDebugApiCalls++;
                    // Fetch a new set of track & waveforms from the API
                    // TODO Is this correct? should we even hold this?
                    mWorkerSubscription = getWorkerSubscription(api, search, limit);
                }
            }, 0, reloadRate, TimeUnit.SECONDS); // um, TimeUnit.MINUTES enum didn't exist until API Level 9!
        }

        /*
         * Creates a Subscription to fetch Tracks from the SoundCloud API
         * that match the search criteria
         */
        private Subscription getWorkerSubscription(SoundCloudClient api, String search,
                                                   final int limit) {
            return api.getTracks(search, limit)
                    .subscribeOn(Schedulers.io()).flatMap(new Func1<List<Track>, Observable<Track>>() {
                        @Override
                        public Observable<Track> call(List<Track> tracks) {
                            // Process each track individually
                            return Observable.from(tracks);
                        }
                    }).map(new Func1<Track, Track>() {
                        @Override
                        public Track call(Track track) {
                            // Attempt to fetch the image waveform bitmap
                            try {
                                Log.i(TAG, "Downloading waveform for track: " + track.getTitle());
                                Bitmap bitmap = mPicasso.load(track.getWaveformUrl()).get();
                                float[] waveformData = new WaveformProcessor().transform(bitmap);
                                track.setWaveformData(waveformData);
                            } catch (IOException e) {
                                Log.w(TAG, "Failed to get Bitmap for track: " + track.getTitle(), e);
                                // We will filter this track from the results
                            }
                            return track;
                        }
                    }).filter(new Func1<Track, Boolean>() {
                        @Override
                        public Boolean call(Track track) {
                            // Remove tracks with no waveform data
                            return track.getWaveformData() != null && track.getWaveformData().length != 0;
                        }
                    }).observeOn(Schedulers.immediate())
                    .subscribe(new Observer<Track>() {

                        @Override
                        public void onNext(Track response) {
                            Log.i(TAG, "onNext() producer. Track received: " + response.getTitle());

                            // Keep some tracks in the list, let new ones slowly take their place.
                            // You get a mixture of tracks when result set < limit.
                            if (mTracks.size() >= limit) {
                                mTracks.removeFirst();
                            }
                            mTracks.addLast(response);
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
                            Log.w(TAG, "PeriodFetchSubscription#onError()", e);
                            // TODO Display message if nothing else to show.
                            // Note: There may still be tracks in mTracks
                            mDebugFailedApiCalls++;
                        }
                    });
        }

        /*
         * Creates a periodic "consumer" Subscription to draw a track's waveform
         */
        private Subscription getConsumerSubscription(int drawRate) {
            Log.i(TAG, "getConsumerSubscription() - drawRate: " + drawRate);
            return Schedulers.newThread().createWorker().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    Log.i(TAG, "call() - consumer");
                    mLock.lock();
                    try {
                        while (mTracks == null || mTracks.isEmpty()) {
                            mTracksExist.await();
                        }
                    } catch (InterruptedException e) {
                        // Not entirely sure what to do here
                        Log.w(TAG, "Consumer Thread interrupted: ", e);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } finally {
                        mLock.unlock();
                    }
                    mMainThreadHandler.post(mDoubleTapTimeout); // change track, invalidates double tap
                    mMainThreadHandler.post(mDrawRunnable); // post draw event
                }
            }, 0, drawRate, TimeUnit.SECONDS);
        }

        // Quick little hack to make a pseudo-circular queue
        private Track getTrack() {
            if (mTracks == null || mTracks.size() == 0)
                return null;

            if (mTracks.size() == 1)
                return mTracks.getFirst();

            Track first = mTracks.removeFirst();
            mTracks.addLast(first);
            return mTracks.getFirst();
        }

        // Unsubscribes from all subscriptions.
        private void unsubscribeAll() {
            cancelCallbacks();

            unsubscribeSafely(mConsumerSubscription);
            mConsumerSubscription = null;

            unsubscribeSafely(mWorkerSubscription);
            mWorkerSubscription = null;

            unsubscribeSafely(mProducerSubscription);
            mProducerSubscription = null;
        }

        private void unsubscribeSafely(Subscription sub) {
            if (sub != null && !sub.isUnsubscribed()) {
                sub.unsubscribe();
            }
        }

        private void cancelCallbacks() {
            mMainThreadHandler.removeCallbacks(mDeadlineRunnable);
            mMainThreadHandler.removeCallbacks(mDrawRunnable);
        }

        /*
         * Draws the current track or a placeholder on a canvas
         */
        public void draw(TrackDrawer drawer, Track track) {
            Log.i(TAG, "draw() - start: " + this + " on: " + Thread.currentThread());
            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    if (track != null) {
                        drawer.drawOn(c, track);
                    } else {
                        drawPlaceholderOn(c);
                    }
                    // drawDebug(c);
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }
            Log.i(TAG, "draw() - end");
        }

        // Placeholder screen implementation
        private void drawPlaceholderOn(Canvas canvas) {
            Log.i(TAG, "drawPlaceholderOn() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(24);
            canvas.drawColor(Color.BLACK);
            canvas.drawText("Wallpaper Demo", canvas.getWidth() / 2, canvas.getHeight() / 2, textPaint);
        }

        // Asks framework to open the provided URL via Intent
        private void openUrl(String url) {
            if (url != null) {
                Log.i(TAG, "openUrl() => Attempt to open track at: " + url);
                Uri uri = Uri.parse(url);
                Intent openIntent = new Intent(Intent.ACTION_VIEW, uri);
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            }
        }

        // Writes debug info on the canvas.
        private void drawDebug(Canvas canvas) {
            Log.i(TAG, "drawDebug() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(24);
            String msg = String.format("API calls made: %d, blocked %d, failed %d, tracks: %d", mDebugApiCalls, mDebugBlockedApiCalls, mDebugFailedApiCalls, mTracks.size());
            canvas.drawText(msg, 0, canvas.getHeight(), textPaint);
        }
    }
}
