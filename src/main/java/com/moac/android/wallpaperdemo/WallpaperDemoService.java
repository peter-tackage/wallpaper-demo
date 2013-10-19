package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import com.android.volley.toolbox.ImageLoader;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * TODO How to behave when the initial data load fails
 */
public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    //    // Define connectivity first.
//    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//    boolean isDataAllowed = connMgr.getBackgroundDataSetting();
//    // ICS will only ever return true for getBackgroundDataSetting().
//    // Apparently uses getActiveNetworkInfo
//    boolean ICSDataAllowed = connMgr.getActiveNetworkInfo() != null
//      && connMgr.getActiveNetworkInfo().isAvailable();

    /**
     * Define these Paints once for performance
     */
    private static final Paint WAVEFORM_PAINT;

    static {
        WAVEFORM_PAINT = new Paint();
        WAVEFORM_PAINT.setDither(true);
        WAVEFORM_PAINT.setAntiAlias(true);
        WAVEFORM_PAINT.setFilterBitmap(true);
        WAVEFORM_PAINT.setColor(Color.WHITE);
    }

    private static final int BACKGROUND_COLOR = Color.DKGRAY;
    private static final Paint BACKGROUND_PAINT;

    static {
        BACKGROUND_PAINT = new Paint();
        BACKGROUND_PAINT.setColor(BACKGROUND_COLOR);
    }

    private static final int TEXT_COLOR = Color.WHITE;
    private static final Paint TEXT_PAINT;

    static {
        TEXT_PAINT = new Paint();
        TEXT_PAINT.setColor(TEXT_COLOR);
    }

    private static final int DEFAULT_COLUMN_GAP_WIDTH_DIP = 10;
    private static final int DEFAULT_COLUMN_COUNT = 120;

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine {

        private long mLastCommandTime = 0;
        private TrackStore mTrackStore;
        private PeriodicExecutorScheduler mTrackScheduler;
        private TrackDrawer mTrackDrawer;
        private Track mCurrentTrack;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);

            Log.i(TAG, "Creating new WallpaperEngine instance");

            // Configure the TrackDrawer
            mTrackDrawer = new TrackDrawer(BACKGROUND_PAINT, WAVEFORM_PAINT,
              DEFAULT_COLUMN_COUNT, DEFAULT_COLUMN_GAP_WIDTH_DIP);

            // Configure the Track Scheduler
            mTrackScheduler = new PeriodicExecutorScheduler(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "PeriodicExecutorScheduler - running task!");
                    mCurrentTrack = mTrackStore.getRandomTrack();
                    drawImage();
                }
            }, 30, TimeUnit.SECONDS);

            // Configure and initialise the TrackStore
            SoundCloudApi api = WallpaperApplication.getInstance().getSoundCloudApi();
            ImageLoader imageLoader = WallpaperApplication.getInstance().getImageLoader();
            mTrackStore = new TrackStore(api, imageLoader,
              new TrackStore.InitListener() {
                  @Override
                  public void isReady() {
                      mTrackScheduler.start();
                  }
              }, new HashMap<String, String>());
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            mTrackScheduler.stop();
            // TODO Stop the TrackStore from requesting/processing updates.
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean _isVisible) {
            Log.d(TAG, "onVisibilityChanged() isVisible: " + _isVisible);
            if(_isVisible) {
                mTrackScheduler.start();
            } else {
                mTrackScheduler.pause();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "onSurfaceChanged() Current surface size: " + width + "," + height);
            // Draw the current image in accordance with the changes.
            // This called on orientation change.
            drawImage();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z,
                                Bundle extras, boolean resultRequested) {
            if(action.equals(WallpaperManager.COMMAND_TAP)) {
                long time = android.os.SystemClock.elapsedRealtime();
                // Look for taps in the double-tap window
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
                    if(mCurrentTrack == null) {
                        drawPlaceholderOn(c);
                    } else {
                        mTrackDrawer.drawOn(c, mCurrentTrack);
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
            _canvas.drawColor(BACKGROUND_COLOR);
            _canvas.drawText("Wallpaper Demo", _canvas.getWidth() / 2, _canvas.getHeight() / 2, TEXT_PAINT);
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
