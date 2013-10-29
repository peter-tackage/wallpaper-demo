package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.moac.android.wallpaperdemo.api.rx.GetTracksFunction;
import com.moac.android.wallpaperdemo.model.Track;
import com.moac.android.wallpaperdemo.util.NumberUtils;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.concurrency.AndroidSchedulers;
import rx.concurrency.Schedulers;
import rx.util.functions.Action0;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

        private String mSearchPref;
        private int mDrawRatePref;
        private int mReloadRatePref;
        private int mBatchingPref;

        private SoundCloudApi mApi;
        private Subscription mPeriodicFetchSubscription;
        private Subscription mApiTrackSubscription;
        private Subscription mDrawerSubscription;
        private TrackDrawer mTrackDrawer;
        private LinkedList<Track> mTracks;
        private Track mCurrentTrack;

        private Handler mHandler;
        private Runnable mSleeping = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Unsubscribing from API due to inactivity");
                unsubscribe();
            }
        };

        // See http://dribbble.com/colors/<value>
        private final Integer[] mPrettyColors =
          { 0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
            0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68 };

        private long mLastCommandTime;

        // Debug info
        private int mDebugApiCalls = 0;
        private int mDebugBlockedApiCalls = 0;
        private int mDebugFailedApiCalls = 0;

        @Override
        public void onCreate(SurfaceHolder _surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(_surfaceHolder);
            setTouchEventsEnabled(true);

            Log.i(TAG, "Creating new WallpaperEngine instance");

            mHandler = new Handler();

            // Configure the TrackDrawer
            mTrackDrawer = new TrackDrawer(10, 10);
            mTracks = new LinkedList<Track>();

            // Configure and initialise model
            mApi = WallpaperApplication.getInstance().getSoundCloudApi();

            SharedPreferences prefs = WallpaperDemoService.this.getSharedPreferences(getString(R.string.wallpaper_settings_key), 0);
            prefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(prefs, null);  // triggers the start
        }

        private Observable<List<Track>> getApiTracks(SoundCloudApi _api, String _search, int _limit) {
            return Observable.create(new GetTracksFunction(getApplicationContext(), _api, _search, _limit));
        }

        private Subscription getPeriodicFetchSubscription(final SoundCloudApi api, int reloadRate,
                                                          final int limit, final String search, final int drawRate) {
            Log.i(TAG, "getPeriodicFetchSubscription() - reload: " + reloadRate + ", limit: " + limit + ", search: " + search + ", drawRate: " + drawRate);
            return AndroidSchedulers.mainThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    if(!isNetworkAvailable()) {
                        Log.i(TAG, "getPeriodicFetchSubscription() - network unavailable");
                        mDebugBlockedApiCalls++;
                        return;
                    }
                    Log.i(TAG, "getPeriodicFetchSubscription - ### POTENTIAL NETWORK CALL ###");
                    mDebugApiCalls++;
                    // Fetch a new set of track & waveforms from the API
                    mApiTrackSubscription = getApiTracks(api, search, limit)
                      .subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                      .subscribe(new Observer<List<Track>>() {

                          @Override
                          public void onNext(List<Track> response) {
                              Log.i(TAG, "Track list size: " + response.size());
                              mTracks = new LinkedList<Track>(response);
                              scheduleDrawer(drawRate);
                          }

                          @Override
                          public void onCompleted() {}

                          @Override
                          public void onError(Throwable e) {
                              Log.w(TAG, "PeriodFetchSubscription#onError()", e);
                              // TODO Display message if nothing else to show.
                              mDebugFailedApiCalls++;
                          }
                      });
                }
            }, 0, reloadRate, TimeUnit.SECONDS); // um, TimeUnit.MINUTES enum didn't exist until API Level 9!
        }

        private void scheduleDrawer(int drawRate) {
            if(mDrawerSubscription != null)
                return;

            Log.i(TAG, "scheduleDrawer() - start the drawer subscription");

            mDrawerSubscription = AndroidSchedulers.mainThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    Log.i(TAG, "call() - Drawer");
                    mCurrentTrack = getTrack();
                    mTrackDrawer.setColor(NumberUtils.getRandomElement(mPrettyColors));
                    draw();
                }
            }, 0, drawRate, TimeUnit.SECONDS);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String _key) {
            Log.i(TAG, "onSharedPreferenceChanged() - Notified: " + prefs);
            // integer-arrays don't work:  http://code.google.com/p/android/issues/detail?id=2096
            mSearchPref = prefs.getString("search_term_preference", "");
            mDrawRatePref = Integer.parseInt(prefs.getString("change_rate_preference", "60"));
            mReloadRatePref = Integer.parseInt(prefs.getString("reload_rate_preference", "3600"));
            mBatchingPref = Integer.parseInt(prefs.getString("batching_preference", "10"));
            unsubscribe();
            mPeriodicFetchSubscription = getPeriodicFetchSubscription(mApi, mReloadRatePref, mBatchingPref, mSearchPref, mDrawRatePref);
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            unsubscribe();
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder _holder, int _format,
                                     int _width, int _height) {
            super.onSurfaceChanged(_holder, _format, _width, _height);
            Log.v(TAG, "onSurfaceChanged() Current surface size: " + _width + "," + _height);
            // Redraw the current Track, this called on orientation change.
            draw();
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
            /**
             * If the canvas is not visible, set a deadline for visibility that
             * if reached will cancel the subscription: don't download data that
             * is not likely to be seen
             */
            Log.d(TAG, "onVisibilityChanged() isVisible: " + isVisible);
            if(!isVisible) {
                Log.i(TAG, "Preparing API subscription for possible sleep");
                mHandler.postDelayed(mSleeping, TimeUnit.MILLISECONDS.convert(mReloadRatePref, TimeUnit.SECONDS));
            } else {
                // And we're back! Cancel the deadline, resubscribe if it expired.
                Log.i(TAG, "Cancelling API subscription sleep deadline");
                mHandler.removeCallbacks(mSleeping);
                if(mPeriodicFetchSubscription == null) {
                    Log.i(TAG, "Restarting API subscription sleep deadline");
                    mPeriodicFetchSubscription =
                      getPeriodicFetchSubscription(mApi, mReloadRatePref, mBatchingPref, mSearchPref, mDrawRatePref);
                }
            }
        }

        // Terrible hack retrieve like a circular queue
        private Track getTrack() {
            if(mTracks == null || mTracks.size() == 0)
                return null;

            if(mTracks.size() == 1)
                return mTracks.getFirst();

            Track first = mTracks.removeFirst();
            mTracks.addLast(first);
            return mTracks.getFirst();
        }

        private void unsubscribe() {
            if(mDrawerSubscription != null) {
                mDrawerSubscription.unsubscribe();
                mDrawerSubscription = null;
            }
            if(mApiTrackSubscription != null) {
                mApiTrackSubscription.unsubscribe();
                mDrawerSubscription = null;
            }
            if(mPeriodicFetchSubscription != null) {
                mPeriodicFetchSubscription.unsubscribe();
                mPeriodicFetchSubscription = null;
            }
        }

        /**
         * Draws the current track or a placeholder on a canvas
         */
        public void draw() {
            Log.i(TAG, "draw() - start");

            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if(c != null) {
                    if(mCurrentTrack != null) {
                        mTrackDrawer.drawOn(c, mCurrentTrack);
                    } else {
                        drawPlaceholderOn(c);
                    }
                    drawDebug(c);
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
            _canvas.drawColor(Color.LTGRAY);
            _canvas.drawText("Wallpaper Demo", _canvas.getWidth() / 2, _canvas.getHeight() / 2, textPaint);
        }

        private void drawDebug(Canvas _canvas) {
            Log.i(TAG, "drawDebug() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(24);
            String msg = String.format("API calls made: %d, blocked %d, failed %d, tracks: %d", mDebugApiCalls, mDebugBlockedApiCalls, mDebugFailedApiCalls, mTracks.size());
            _canvas.drawText(msg, 0, _canvas.getHeight(), textPaint);
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

        /**
         * Return true if the network is available and data transfer is allowed.
         */
        private boolean isNetworkAvailable() {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                //noinspection deprecation
                return connMgr.getBackgroundDataSetting();
            } else {
                return connMgr.getActiveNetworkInfo() != null
                  && connMgr.getActiveNetworkInfo().isConnected();
            }
        }
    }
}
