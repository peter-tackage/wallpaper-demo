package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import com.moac.android.wallpaperdemo.util.NumberUtils;

import java.util.concurrent.TimeUnit;

public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine {

        private long mLastCommandTime = 0;
        private TrackStore mTrackStore;
        private Scheduler mScheduler;
        private TrackDrawer mTrackDrawer;
        private Track mCurrentTrack;
        // See http://dribbble.com/colors/<value>
        private final Integer[] mPrettyColors =
          { 0xFF434B52, 0xFF54B395, 0xFFD1654C, 0xFFD6B331, 0xFF3D4348,
            0xFFA465C5, 0xFF5661DE, 0xFF4AB498, 0xFFFA7B68 };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);

            Log.i(TAG, "Creating new WallpaperEngine instance");

            // Configure the TrackDrawer
            mTrackDrawer = new TrackDrawer(7);

            // Configure the Track Scheduler
            mScheduler = new PeriodicHandlerScheduler(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "run() Scheduler task due callback ");
                    mCurrentTrack = mTrackStore.getTrack();
                    mTrackDrawer.setBackgroundColor(NumberUtils.getRandomElement(mPrettyColors));
                    drawImage();
                }
            }, 30, TimeUnit.SECONDS);

            // Configure and initialise the TrackStore
            SoundCloudApi api = WallpaperApplication.getInstance().getSoundCloudApi();
            mTrackStore = new TrackStore(api, getApplicationContext(),
              new TrackStore.InitListener() {
                  @Override
                  public void isReady() {
                      Log.i(TAG, "isReady() TrackStore initialisation complete");
                      mScheduler.start();
                  }
              });
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            mScheduler.stop();
            mTrackStore.onDestroy();
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
