package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.image.WaveformProcessor;
import com.moac.android.wallpaperdemo.model.Track;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import com.squareup.picasso.Picasso;
import rx.Observer;
import rx.Subscription;
import rx.android.concurrency.AndroidSchedulers;
import rx.concurrency.Schedulers;
import rx.util.functions.Action0;
import rx.util.functions.Func1;

import javax.inject.Inject;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is a demo of a live wallpaper using data retrieved from the SoundCloud API.
 * It provides a rolling set of waveforms based on search query parameters, which are
 * periodically refreshed via an API query.
 *
 * It uses various strategies to find a balance between providing a vaguely interesting
 * user experience and not placing too much of a drain on device resources, such
 * as battery and data.
 *
 * The wallpaper operates by acquiring waveform and metadata from
 * the API via a period "producer" subscription which fetches a user configurable batch
 * of between 5-25 tracks.
 *
 * This data is then processed and stored as an in-memory model. At this point
 * there is no need to access the network until the next invocation. Until then, the
 * current set of images are displayed cyclically via a "consumer" subscription, keeping
 * the user entertained with the waveforms overlaying a range of handpicked background
 * colours, *most* of which don't look like day-old mustard spilt down the front
 * of an olive green jumper.
 *
 * The pre-fetching strategy, rather than more frequent periodic retrieval of
 * smaller amounts of data provides a battery saving in the long term. Refer to
 * http://developer.android.com/training/efficient-downloads/index.html
 *
 * In our case, the amount of data retrieved is relatively small by most standards
 * so there's no good reason not to prefetch as much as sensible.
 *
 * Live wallpaper guidelines strongly suggest not to use CPU when the wallpaper
 * is not visible. This definitely makes sense when the wallpaper is a fancy,
 * animated wallpaper, but less sense in our case. If we limited the wallpaper
 * to only updating when it was visible, it would be pretty boring. If we change
 * the image while the background is not visible, it arguably makes for a better
 * user experience; they get a new waveform more often! Naturally, this comes at
 * the cost of battery drain (although very minimal, it's a short, in-memory interaction).
 *
 * As a middle ground, we attempt to avoid repeated API calls when the wallpaper
 * is not visible. Once the wallpaper loses visibility, a deadline timer is
 * initiated which halts all wallpaper API calls when it expires. If visibility is
 * restored before the expiry, the timer is cancelled. If visibility is restored
 * after the expiry, the periodic fetching is restarted. The deadline time is
 * the period of the periodic fetching task. The cyclic displaying of images is
 * not affect by this as it is seen as lightweight.
 *
 * Things that aren't supported just yet -
 *
 * 1. Persistent caching other than that provided by the HTTP layer and Picasso's
 * own cache. This doesn't affect the wallpaper unless the user restarts their
 * phone in an area without a connection, in which they've no doubt got bigger
 * worries.
 *
 * 2. Listening to Android Network Status Broadcasts to determine if the wallpaper
 * can attempt to initialise the track list following a fetch failure. If the user starts
 * the wallpaper when they don't have an internet connection, it will fail to fetch
 * and won't retry until the next poll. There's no error message. Ideally, there would
 * be a stylized picture.
 *
 * 3. A set of constantly changing *new* tracks! It seems that without using the
 * search "offset" parameter users tend to get the same tracks for a given
 * query. This means that frequent refreshing of the track list if mostly pointless.
 * Perhaps the parameter's use could be introduced to provide a better "discover"
 * experience.
 *
 * Proper known issues:
 *
 * 1. I've have noticed that sometimes the periodic subscriptions aren't actually
 * unsubscribed via the onDestroy call. This can be seen in the logs -
 *
 * 10-31 00:49:58.538: DEBUG/WallpaperDemoService(4487): onDestroy()com.moac.android.wallpaperdemo.WallpaperDemoService$WallpaperEngine@b517e350
 * ...
 * 10-31 00:52:50.790: INFO/WallpaperDemoService(4487): draw() - start: com.moac.android.wallpaperdemo.WallpaperDemoService$WallpaperEngine@b517e350 on: Thread[main,5,main]
 * repeat...
 *
 * This causes repeated canvas lock errors in draw() as the two Engines fight for control.
 * I'll have to look into whether this is an RxJava issue, or perhaps I'm just not doing
 * it right.
 *
 * Someone else reported a similar issue - https://github.com/Netflix/RxJava/issues/431
 */
public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    private static final String SEARCH_TERM_PREFERENCE = "search_term_preference";
    private static final String CHANGE_RATE_PREFERENCE = "change_rate_preference";
    private static final String RELOAD_RATE_PREFERENCE = "reload_rate_preference";
    private static final String PREFETCH_PREFERENCE = "prefetch_preference";

    @Inject SoundCloudClient mApi;
    @Inject Picasso mPicasso;
    WallpaperApplication mApplication;

    @Override
    public Engine onCreateEngine() {
        mApplication = WallpaperApplication.from(this);
        mApplication.inject(this);
        return new WallpaperEngine();
    }

    // Dagger can't see this class, must DI into the Service instead
    protected class WallpaperEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

        private String mSearchPref;
        private int mDrawRatePref;
        private int mReloadRatePref;
        private int mPrefetchPref;

        private Subscription mProducerSubscription;
        private Subscription mWorkerSubscription;
        private Subscription mConsumerSubscription;
        private TrackDrawer mTrackDrawer;
        private LinkedList<Track> mTracks;
        private Track mCurrentTrack;

        final private Lock mLock;
        final private Condition mTracksExist;
        final private Handler mHandler;

        private Runnable mDeadlineRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Unsubscribing from API due to inactivity");
                unsubscribe(mProducerSubscription);
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

        // Get more at http://dribbble.com/colors/<value>
        private final Integer[] mPrettyColors =
          { 0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
            0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68, 0xFFFF6600,
            0xFF669900, 0xFF66CCCC };

        private long mLastCommandTime;

        // Debug info
        private int mDebugApiCalls = 0;
        private int mDebugBlockedApiCalls = 0;
        private int mDebugFailedApiCalls = 0;

        public WallpaperEngine() {
            mLock = new ReentrantLock();
            mTracksExist = mLock.newCondition();
            mHandler = new Handler();
        }

        @Override
        public void onCreate(SurfaceHolder _surfaceHolder) {
            Log.i(TAG, "onCreate() - Creating new WallpaperEngine instance: " + this);
            super.onCreate(_surfaceHolder);
            setTouchEventsEnabled(true);

            mTracks = new LinkedList<Track>();

            // Configure the TrackDrawer
            mTrackDrawer = new TrackDrawer(10, 10);
            mTracks = new LinkedList<Track>();

            SharedPreferences prefs = WallpaperDemoService.this.getSharedPreferences(getString(R.string.wallpaper_settings_key), 0);
            prefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(prefs, null);  // triggers the start
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String _key) {
            Log.d(TAG, "onSharedPreferenceChanged() - Notified: " + prefs);
            // integer-arrays don't work:  http://code.google.com/p/android/issues/detail?id=2096
            mSearchPref = prefs.getString(SEARCH_TERM_PREFERENCE, getString(R.string.default_search_term));
            mDrawRatePref = Integer.parseInt(prefs.getString(CHANGE_RATE_PREFERENCE, getString(R.string.default_change_rate)));
            mReloadRatePref = Integer.parseInt(prefs.getString(RELOAD_RATE_PREFERENCE, getString(R.string.default_reload_rate)));
            mPrefetchPref = Integer.parseInt(prefs.getString(PREFETCH_PREFERENCE, getString(R.string.default_prefetch)));

            String values = String.format("Search: %s, Draw Rate: %s, Reload Rate: %s, Prefetch: %s", mSearchPref, mDrawRatePref, mReloadRatePref, mPrefetchPref);
            Log.i(TAG, "onSharedPreferenceChanged: " + values);

            // TODO We could be smart here - only restart the relevant components
            unsubscribeAll();
            mConsumerSubscription = getConsumerSubscription(mDrawRatePref);
            mProducerSubscription = getProducerSubscription(mApi, mReloadRatePref, mPrefetchPref, mSearchPref);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "onDestroy() - " + this);
            unsubscribeAll();
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder _holder, int _format,
                                     int _width, int _height) {
            super.onSurfaceChanged(_holder, _format, _width, _height);
            Log.v(TAG, "onSurfaceChanged() Current surface size: " + _width + "," + _height);
            // Redraw canvas. Called on orientation change.
            draw(mTrackDrawer, mCurrentTrack);
        }

        @Override
        public Bundle onCommand(String _action, int x, int y, int z,
                                Bundle _extras, boolean _resultRequested) {
            if(_action.equals(WallpaperManager.COMMAND_TAP)) {
                long time = android.os.SystemClock.elapsedRealtime();
                // Look for taps in the double-tap window (in ms)
                if(((time - mLastCommandTime) < 500)
                  && ((time - mLastCommandTime) > 100) && mCurrentTrack != null) {
                    // Double tap = view track
                    openUrl(mCurrentTrack.getPermalinkUrl());
                }
                mLastCommandTime = time;
            }
            return null;
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
            if(!isVisible) {
                Log.i(TAG, "Preparing API subscription for possible sleep");
                mHandler.postDelayed(mDeadlineRunnable, TimeUnit.MILLISECONDS.convert(mReloadRatePref, TimeUnit.SECONDS));
            } else {
                // And we're back! Cancel the deadline, resubscribe if it expired.
                Log.i(TAG, "Cancelling API subscription sleep deadline");
                mHandler.removeCallbacks(mDeadlineRunnable);
                if(mProducerSubscription == null) {
                    Log.i(TAG, "Restarting API subscription sleep deadline");
                    mProducerSubscription =
                      getProducerSubscription(mApi, mReloadRatePref, mPrefetchPref, mSearchPref);
                }
            }
        }

        /*
         * Creates a periodic "producer" Subscription to get Tracks from the SoundCloud API
         */
        private Subscription getProducerSubscription(final SoundCloudClient _api, int _reloadRate,
                                                     final int _limit, final String _search) {
            Log.i(TAG, "getProducerSubscription() - reload: " + _reloadRate + ", limit: " + _limit + ", search: " + _search);
            return AndroidSchedulers.mainThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    if(!isNetworkAvailable()) {
                        Log.i(TAG, "getProducerSubscription() - network unavailable");
                        mDebugBlockedApiCalls++;
                        return;
                    }
                    Log.i(TAG, "getProducerSubscription() - ### POTENTIAL NETWORK CALL ###");
                    mDebugApiCalls++;
                    // Fetch a new set of track & waveforms from the API
                    // TODO Is this correct? should we even hold this?
                    mWorkerSubscription = getWorkerSubscription(_api, _search, _limit);
                }
            }, 0, _reloadRate, TimeUnit.SECONDS); // um, TimeUnit.MINUTES enum didn't exist until API Level 9!
        }

        /*
         * Creates a Subscription to fetch Tracks from the SoundCloud API
         * that match the search criteria
         */
        private Subscription getWorkerSubscription(SoundCloudClient _api, String _search,
                                                   final int _limit) {
            return SoundCloudApi.getApiTracks(_api, _search, _limit)
              .subscribeOn(Schedulers.newThread()).map(new Func1<Track, Track>() {
                  @Override
                  public Track call(Track track) {
                      try {
                          Log.i(TAG, "Downloading waveform for track: " + track.getTitle());
                          Bitmap bitmap = mPicasso.load(track.getWaveformUrl()).get();
                          float[] waveformData = new WaveformProcessor().transform(bitmap);
                          track.setWaveformData(waveformData);
                      } catch(IOException e) {
                          Log.w(TAG, "Failed to get Bitmap for track: " + track.getTitle(), e);
                          // FIXME Hmmm, how to handle this error?
                          // Currently just filter based on existence of waveform.
                          // More reading: https://github.com/Netflix/RxJava/wiki/Error-Handling-Operators
                          throw new RuntimeException("Error retrieving track waveform: " + track.getWaveformUrl());
                      }
                      return track;
                  }
              }).filter(new Func1<Track, Boolean>() {
                        @Override
                        public Boolean call(Track track) {
                            return track.getWaveformData() != null && track.getWaveformData().length != 0;
                        }
                    }).observeOn(Schedulers.currentThread())
              .subscribe(new Observer<Track>() {

                  @Override
                  public void onNext(Track response) {
                      Log.i(TAG, "onNext() producer. Track received: " + response.getTitle());

                      // Keep some tracks in the list, let new ones slowly take their place.
                      // You get a mixture of tracks when result set < limit.
                      if (mTracks.size() >= _limit) {
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
                  public void onCompleted() {}

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
            return Schedulers.newThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    Log.i(TAG, "call() - consumer");
                    mLock.lock();
                    try {
                        while(mTracks == null || mTracks.isEmpty()) {
                            mTracksExist.await();
                        }
                    } catch(InterruptedException e) {
                        // Not entirely sure what to do here
                        Log.w(TAG, "Consumer Thread interrupted: ", e);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } finally {
                        mLock.unlock();
                    }
                    mHandler.post(mDrawRunnable); // post drawing to handler.
                }
            }, 0, drawRate, TimeUnit.SECONDS);
        }

        // Quick little hack to make a pseudo-circular queue
        private Track getTrack() {
            if(mTracks == null || mTracks.size() == 0)
                return null;

            if(mTracks.size() == 1)
                return mTracks.getFirst();

            Track first = mTracks.removeFirst();
            mTracks.addLast(first);
            return mTracks.getFirst();
        }

        // Unsubscribes from all subscriptions.
        private void unsubscribeAll() {
            cancelCallbacks();

            unsubscribe(mConsumerSubscription);
            mConsumerSubscription = null;

            unsubscribe(mWorkerSubscription);
            mWorkerSubscription = null;

            unsubscribe(mProducerSubscription);
            mProducerSubscription = null;
        }

        private void unsubscribe(Subscription sub) {
            if(sub != null) {
                sub.unsubscribe();
            }
        }

        private void cancelCallbacks() {
            mHandler.removeCallbacks(mDeadlineRunnable);
            mHandler.removeCallbacks(mDrawRunnable);
        }

        /*
         * Draws the current track or a placeholder on a canvas
         */
        public void draw(TrackDrawer _drawer, Track _track) {
            Log.i(TAG, "draw() - start: " + this + " on: " + Thread.currentThread());
            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if(c != null) {
                    if(_track != null) {
                        _drawer.drawOn(c, _track);
                    } else {
                        drawPlaceholderOn(c);
                    }
                    // drawDebug(c);
                }
            } finally {
                if(c != null)
                    holder.unlockCanvasAndPost(c);
            }
            Log.i(TAG, "draw() - end");
        }

        // Placeholder screen implementation
        private void drawPlaceholderOn(Canvas _canvas) {
            Log.i(TAG, "drawPlaceholderOn() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(24);
            _canvas.drawColor(Color.BLACK);
            _canvas.drawText("Wallpaper Demo", _canvas.getWidth() / 2, _canvas.getHeight() / 2, textPaint);
        }

        // Asks framework to open the provided URL via Intent
        private void openUrl(String _url) {
            if(_url != null) {
                Log.i(TAG, "openUrl() => Attempt to open track at: " + _url);
                Uri uri = Uri.parse(_url);
                Intent openIntent = new Intent(Intent.ACTION_VIEW, uri);
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            }
        }

        // Helper - Return true if the network is available and data transfer is allowed.
        private boolean isNetworkAvailable() {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            //noinspection deprecation
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH ?
              connMgr.getBackgroundDataSetting() :
              connMgr.getActiveNetworkInfo() != null
                && connMgr.getActiveNetworkInfo().isConnectedOrConnecting();
        }

        // Writes debug info on the canvas.
        private void drawDebug(Canvas _canvas) {
            Log.i(TAG, "drawDebug() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(24);
            String msg = String.format("API calls made: %d, blocked %d, failed %d, tracks: %d", mDebugApiCalls, mDebugBlockedApiCalls, mDebugFailedApiCalls, mTracks.size());
            _canvas.drawText(msg, 0, _canvas.getHeight(), textPaint);
        }
    }
}
