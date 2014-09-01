package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ViewConfiguration;

import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.api.model.Track;
import com.moac.android.wallpaperdemo.gfx.TrackDrawer;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import com.squareup.picasso.Picasso;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

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

    // Get more at http://dribbble.com/colors/<value>
    private static final Integer[] PRETTY_COLORS =
            {0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
                    0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68, 0xFFFF6600,
                    0xFF669900, 0xFF66CCCC};

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
        private Subscription mConsumerSubscription;
        private TrackProvider mTrackProvider;
        private TrackDrawer mTrackDrawer;
        private Track mCurrentTrack;

        final private Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

        // Shutdown API subscription when inactive
        private Runnable mDeadlineRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Unsubscribing from API due to inactivity");
                unsubscribeSafely(mProducerSubscription);
            }
        };
        private Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Executing Draw Runnable");
                mCurrentTrack = mTrackProvider.getNextTrack();
                mTrackDrawer.setColor(NumberUtils.getRandomElement(PRETTY_COLORS));
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
            mMainThreadHandler.postDelayed(mDoubleTapTimeout, ViewConfiguration.getDoubleTapTimeout());
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

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.i(TAG, "onCreate() - Creating new WallpaperEngine instance: " + this);
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);

            mTrackProvider = new TrackProvider(getApplicationContext(), mApi, mPicasso);
            mTrackDrawer = new TrackDrawer(10, 10);
            mWallpaperPreferences.addChangeListener(this);

            // Start drawing
            startAll();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.d(TAG, "onSharedPreferenceChanged()");
            unsubscribeAll();
            startAll();
        }

        private void startAll() {
            startProducer();
            startConsumer();
        }

        private void startConsumer() {
            int drawRate = mWallpaperPreferences.getDrawRateInSeconds();
            Log.i(TAG, String.format("Starting Consumer - Draw Rate: %d", drawRate));
            mConsumerSubscription = createConsumerSubscription(drawRate);
        }

        private void startProducer() {
            String searchTerm = mWallpaperPreferences.getSearchTerm();
            int reloadRate = mWallpaperPreferences.getReloadRateInSeconds();
            int prefetchCount = mWallpaperPreferences.getPrefetchCount();
            Log.i(TAG, String.format("Starting Producer - Search Term: %s, Reload Rate: %d, Prefetch Count: %d", searchTerm, reloadRate, prefetchCount));
            mProducerSubscription = createProducerSubscription(reloadRate, prefetchCount, searchTerm);
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
             * if reached will cancel the producer subscription: don't download data that
             * is not likely to be seen
             */
            if (!isVisible) {
                Log.i(TAG, "Preparing API subscription for possible sleep");
                mMainThreadHandler.postDelayed(mDeadlineRunnable, TimeUnit.MILLISECONDS.
                        convert(mWallpaperPreferences.getReloadRateInSeconds(), TimeUnit.SECONDS));
            } else {
                // And we're back! Cancel the deadline, resubscribe if it expired.
                Log.i(TAG, "Cancelling API subscription sleep deadline");
                mMainThreadHandler.removeCallbacks(mDeadlineRunnable);
                if (mProducerSubscription.isUnsubscribed()) {
                    Log.i(TAG, "Restarting API subscription");
                    startProducer();
                }
            }
        }

        /*
         * Creates a periodic "producer" Subscription to get Tracks from the SoundCloud API
         *
         * "A subscription to periodically subscribe to the API observable"
         */
        private Subscription createProducerSubscription(int reloadRateSec, final int limit, final String searchTerm) {
            return mTrackProvider.getTrackPeriodically(searchTerm, limit, reloadRateSec);
        }

        /*
         * Creates a periodic "consumer" Subscription to draw a track's waveform
         */
        private Subscription createConsumerSubscription(int drawRate) {
            return Schedulers.newThread().createWorker().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    try {
                        mTrackProvider.waitUntilReady();
                    } catch (InterruptedException e) {
                        // We've been interrupted when waiting for producer
                        Log.d(TAG, "Consumer Thread interrupted");
                        Thread.currentThread().interrupt();
                    }
                    // Post to main thread
                    mMainThreadHandler.post(mDoubleTapTimeout); // change track, invalidates double tap
                    mMainThreadHandler.post(mDrawRunnable); // post draw event
                }
            }, 0, drawRate, TimeUnit.SECONDS);
        }

        // Unsubscribes from all subscriptions.
        private void unsubscribeAll() {
            cancelCallbacks();
            unsubscribeSafely(mConsumerSubscription);
            unsubscribeSafely(mProducerSubscription);
        }

        private void unsubscribeSafely(Subscription sub) {
            if (sub != null && !sub.isUnsubscribed()) {
                sub.unsubscribe();
            }
        }

        private void cancelCallbacks() {
            mMainThreadHandler.removeCallbacks(mDoubleTapTimeout);
            mMainThreadHandler.removeCallbacks(mDeadlineRunnable);
            mMainThreadHandler.removeCallbacks(mDrawRunnable);
        }

        /*
         * Draws the current track or a placeholder on a canvas
         */
        public void draw(TrackDrawer drawer, Track track) {
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
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }
        }

        // Placeholder screen implementation
        private void drawPlaceholderOn(Canvas canvas) {
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

    }
}
