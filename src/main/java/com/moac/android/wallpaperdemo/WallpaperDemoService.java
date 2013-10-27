package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import java.util.ArrayList;
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

        private long mLastCommandTime;
        private Subscription mPeriodicFetchSubscription;
        private Subscription mDrawerSubscription;
        private TrackDrawer mTrackDrawer;
        private Track mCurrentTrack;
        private LinkedList<Track> mTracks;

        // See http://dribbble.com/colors/<value>
        private final Integer[] mPrettyColors =
          { 0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
            0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68 };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            prefs.registerOnSharedPreferenceChangeListener(WallpaperEngine.this);

            Log.i(TAG, "Creating new WallpaperEngine instance");

            // Configure the TrackDrawer
            mTrackDrawer = new TrackDrawer(10, 10);
            mTracks = new LinkedList<Track>();

            // Configure and initialise model
            final SoundCloudApi api = WallpaperApplication.getInstance().getSoundCloudApi();
            mPeriodicFetchSubscription = AndroidSchedulers.mainThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    Log.i(TAG, "PeriodFetchSubscription - getting more tracks from API");
                    getApiTracks(api, "electronic", 10)
                      .subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                      .subscribe(new Observer<Track>() {

                          boolean isFirst = true;

                          @Override
                          public void onNext(Track response) {
                              Log.i(TAG, "Emitted Track: " + response.getTitle());
                              if(isFirst) {
                                  mTracks.clear();
                              }
                              mTracks.add(response);
                              if(isFirst) {
                                  startDrawer();
                              }
                              isFirst = false;
                          }

                          @Override
                          public void onCompleted() {}

                          @Override
                          public void onError(Throwable e) {
                              Log.w(TAG, "PeriodFetchSubscription#onError()", e);
                              // TODO Display message if nothing else to show.
                          }
                      });
                }
            }, 0, 2, TimeUnit.MINUTES);
        }

        public void startDrawer() {
            Log.i(TAG, "startDrawer() - start the drawer subscription");
            if(mDrawerSubscription != null)
                mDrawerSubscription.unsubscribe();
            mDrawerSubscription = AndroidSchedulers.mainThread().schedulePeriodically(new Action0() {
                @Override
                public void call() {
                    Log.i(TAG, "call() - Drawer");
                    mCurrentTrack = getTrack();
                    mTrackDrawer.setColor(NumberUtils.getRandomElement(mPrettyColors));
                    drawTrack();
                }
            }, 0, 10, TimeUnit.SECONDS);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(TAG, "onSharedPreferenceChanged() - Notified ");
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            if(mDrawerSubscription != null)
                mDrawerSubscription.unsubscribe();
            mPeriodicFetchSubscription.unsubscribe();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean _isVisible) {
            Log.v(TAG, "onVisibilityChanged() isVisible: " + _isVisible);
            // TODO Not really sure if I should unsubscribe.
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.v(TAG, "onSurfaceChanged() Current surface size: " + width + "," + height);
            // Redraw the current Track, this called on orientation change.
            drawTrack();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z,
                                Bundle extras, boolean resultRequested) {
            if(action.equals(WallpaperManager.COMMAND_TAP)) {
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

        private Observable<Track> getApiTracks(SoundCloudApi _api, String _genre, int _limit) {
            return Observable.create(new GetTracksFunction(getApplicationContext(), _api, _genre, _limit));
        }

        /**
         * Draws the current track or a placeholder on a canvas
         */
        public void drawTrack() {
            Log.i(TAG, "drawTrack() - start");

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
                }
            } finally {
                if(c != null)
                    holder.unlockCanvasAndPost(c);
            }
            Log.i(TAG, "drawTrack() - end");
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

        private void openUrl(String url) {
            if(url != null) {
                Log.i(TAG, "openUrl() => Attempt to open track at: " + url);
                Uri uri = Uri.parse(url);
                Intent openIntent = new Intent(Intent.ACTION_VIEW, uri);
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            }
        }
    }
}
