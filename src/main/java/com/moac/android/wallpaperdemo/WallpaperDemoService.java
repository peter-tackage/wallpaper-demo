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

import java.util.concurrent.TimeUnit;

public class WallpaperDemoService extends WallpaperService {

    private static final String TAG = WallpaperDemoService.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    protected class WallpaperEngine extends Engine implements TrackListener {

        private long mLastCommandTime = 0;
        private TrackScheduler mTrackScheduler;
        private ImageDrawer mImageDrawer;
        private Track mCurrentTrack;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "onCreate()");
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);

            // Build the Track Scheduler, so we get track updates.
            Log.i(TAG, "Creating new WallpaperEngine instance");
            SoundCloudApi api = WallpaperApplication.getInstance().getSoundCloudApi();
            mTrackScheduler = new TrackScheduler(api, this, 30, TimeUnit.SECONDS);
            mTrackScheduler.setGenre("electronic");
            mImageDrawer = new ImageDrawer();
            mTrackScheduler.start();
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy()");
            mTrackScheduler.stop();
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
        public void onScheduleEvent(Track _track) {
            Log.i(TAG, "onScheduleEvent() + track:" + _track.getTitle());
            mCurrentTrack = _track;
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
            if(mCurrentTrack == null || mCurrentTrack.getWaveformData() == null)
                return;

            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if(c != null) {
                    mImageDrawer.drawOn(c, mCurrentTrack.getWaveformData(),  120);
                }
            } finally {
                if(c != null)
                    holder.unlockCanvasAndPost(c);
            }
            Log.i(TAG, "drawImage() - end");
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
