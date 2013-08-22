package com.moac.android.wallpaperdemo;

import android.app.WallpaperManager;
import android.graphics.*;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine implements SchedulerListener {

        private long mLastCommandTime = 0; // would use for doubletap
        private ImageScheduler mImageScheduler;

        public WallpaperEngine() {
            super();
            Log.i(TAG, "Creating new WallpaperEngine instance");
            mImageScheduler = new ImageScheduler(getApplicationContext(), this);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            mImageScheduler.start();
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            mImageScheduler.stop();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean _isVisible) {
            Log.d(TAG, "onVisibilityChanged() isVisibile: " + _isVisible);
            // TODO if not visible, pause the ImageScheduler
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format,
                                     int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "Current surface size: " + width + "," + height);
            // Draw the current image in accordance with the changes.
            drawImage();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep,
                                     float yStep, int xPixels, int yPixels) {
            // Draw the current image in accordance with the changes.
            drawImage();
        }

        //        public void openTrack(Track track) {
//
//            // Attempt to use the browser and the track URL. to view it.
//            if(mCurrentTrack != null
//              && mCurrentTrack.getPermalinkUrl() != null) {
//                Log.i(TAG, "openTrack() => Attempt to open track at: "
//                  + mCurrentTrack.getPermalinkUrl());
//                openBrowsertoUrl(Uri.parse(mCurrentTrack.getPermalinkUrl()));
//            } else {
//                // No URL? No problem. Just open the SoundCloud page.
//                Log.i(TAG, "openTrack() => Attempt to Soundcloud home ");
//                openBrowsertoUrl(Uri
//                  .parse(Env.LIVE.sslAuthResourceHost.toURI()));
//            }
//        }
//
//        private void openBrowsertoUrl(Uri url) {
//            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, url);
//            launchBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(launchBrowser);

        @Override
        public void onScheduleEvent() {
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
                    //openTrack(mCurrentTrack);
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
                    mImageScheduler.draw(c);
                }
            } finally {
                if(c != null)
                    holder.unlockCanvasAndPost(c);
            }
            Log.i(TAG, "drawImage() - end");
        }
    }
}
