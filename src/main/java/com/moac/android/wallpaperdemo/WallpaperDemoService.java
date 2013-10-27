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
import rx.Subscription;
import rx.android.concurrency.AndroidSchedulers;
import rx.concurrency.Schedulers;
import rx.util.functions.Action1;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

        private long mLastCommandTime;
        private Subscription mSubscription;
        private Scheduler mScheduler;
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

            // Configure the Track Scheduler
            mScheduler = new PeriodicHandlerScheduler(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "run() Scheduler task callback ");
                    mCurrentTrack = getTrack();
                    mTrackDrawer.setColor(NumberUtils.getRandomElement(mPrettyColors));
                    drawImage();
                }
            }, 10, TimeUnit.SECONDS);

            // Configure and initialise model
            SoundCloudApi api = WallpaperApplication.getInstance().getSoundCloudApi();
            buildModel(api, "electronic", 10);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(TAG, "onSharedPreferenceChanged() - Notified ");
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            mScheduler.stop();
            unsubscribe();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean _isVisible) {
            Log.v(TAG, "onVisibilityChanged() isVisible: " + _isVisible);
            if(!_isVisible) {
                mScheduler.pause();
            } else {
                mScheduler.resume();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.v(TAG, "onSurfaceChanged() Current surface size: " + width + "," + height);
            // Redraw the current Track, this called on orientation change.
            drawImage();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z,
                                Bundle extras, boolean resultRequested) {
            if(action.equals(WallpaperManager.COMMAND_TAP)) {
                long time = android.os.SystemClock.elapsedRealtime();
                // Look for taps in the double-tap window (in ms)
                if(((time - mLastCommandTime) < 500)
                  && ((time - mLastCommandTime) > 100)) {
                    // Double tap = view track
                    openTrack(mCurrentTrack);
                }
                mLastCommandTime = time;
            }
            return null;
        }

        private Track getTrack() {
            if(mTracks == null || mTracks.size() == 0)
                return null;

            if(mTracks.size() == 1)
                return mTracks.getFirst();

            Track first = mTracks.removeFirst();
            mTracks.addLast(first);
            return mTracks.getFirst();
        }

        private void buildModel(SoundCloudApi _api, String _genre, int _limit) {
            Log.i(TAG, "buildModel()");

            Observable<Track> observable = Observable.create(new GetTracksFunction(getApplicationContext(), _api, _genre, _limit));

            // Cancel any existing subscription and reset state.
            mScheduler.stop(); // Retain current waveform until new ones (no nasty pause during loading)
            unsubscribe();
            mTracks = new LinkedList<Track>();

            mSubscription = observable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
              .subscribe(new Action1<Track>() {
                             @Override
                             public void call(Track response) {
                                 Log.i(TAG, "Emitted Track: " + response.getTitle());
                                 mTracks.add(response);
                                 mScheduler.start();
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
        }

        private void unsubscribe() {
            if(mSubscription != null) {
                mSubscription.unsubscribe();
                mSubscription = null;
            }
        }

        public void drawImage() {
            Log.i(TAG, "drawImage() - start");

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
            Log.i(TAG, "drawImage() - end");
        }

        private void drawPlaceholderOn(Canvas _canvas) {
            Log.i(TAG, "drawPlaceholderOn() - start");
            Paint textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            _canvas.drawColor(Color.LTGRAY);
            _canvas.drawText("Wallpaper Demo", _canvas.getWidth() / 2, _canvas.getHeight() / 2, textPaint);
        }

        public void openTrack(Track _track) {
            if(_track != null
              && _track.getPermalinkUrl() != null) {
                Log.i(TAG, "openTrack() => Attempt to open track at: "
                  + _track.getPermalinkUrl());
                Uri uri = Uri.parse(_track.getPermalinkUrl());
                Intent openIntent = new Intent(Intent.ACTION_VIEW, uri);
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            }
        }
    }
}
