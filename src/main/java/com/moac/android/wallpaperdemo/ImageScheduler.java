package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.Log;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.moac.android.wallpaperdemo.model.Track;

public class ImageScheduler {

    private final int DEFAULT_NEXT_WAVEFORM_INTERVAL_MILLIS = 30 * 1000;

    private static final String TAG = ImageScheduler.class.getSimpleName();

    private final Context mContext;
    private Handler mHandler;
    private final SchedulerListener mSchedulerListener;
    private ImageDrawer mImageDrawer;
    private TrackStore mTrackStore;
    private Track mTrack;

    private final Runnable drawRunner = new Runnable() {
        @Override
        public void run() {
            try {
                // TODO Dynamically change the column count.
                fetchNextImage();
            } finally {
                // Retask.
                mHandler.postDelayed(this, DEFAULT_NEXT_WAVEFORM_INTERVAL_MILLIS);
            }
        }
    };
    private long mPausedAt = 0;
    private long mScheduledFor = 0;

    public ImageScheduler(Context _context, SchedulerListener _listener) {
        mContext = _context;
        mHandler = new Handler();
        mSchedulerListener = _listener;
        mImageDrawer = new ImageDrawer();
        mTrackStore = new TrackStore();
    }

//    // Define connectivity first.
//    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//    boolean isDataAllowed = connMgr.getBackgroundDataSetting();
//    // ICS will only ever return true for getBackgroundDataSetting().
//    // Apparently uses getActiveNetworkInfo
//    boolean ICSDataAllowed = connMgr.getActiveNetworkInfo() != null
//      && connMgr.getActiveNetworkInfo().isAvailable();

    public void draw(final Canvas c) {
        Log.v(TAG, "draw()- start");
        // Canvas is locked here.
        if(mTrack != null && mTrack.getWaveformData() != null) {
            Log.v(TAG, "draw()- is valid!");
            mImageDrawer.drawOn(c, mTrack.getWaveformData(), 125);
        }
    }

    public void pause() {
        mHandler.removeCallbacks(drawRunner);
        mPausedAt = android.os.SystemClock.uptimeMillis();
    }

    public void start() {
        long now = android.os.SystemClock.uptimeMillis();
        long delay = 0;
        if(now - DEFAULT_NEXT_WAVEFORM_INTERVAL_MILLIS < mScheduledFor) {
            // Reschedule its original time to remain periodic
            delay = mScheduledFor + DEFAULT_NEXT_WAVEFORM_INTERVAL_MILLIS - mPausedAt;
        }
        mScheduledFor = now + delay;
        mHandler.postAtTime(drawRunner, mScheduledFor);
    }

    public void stop() {
        mPausedAt = 0;
        mScheduledFor = 0;
        mHandler.removeCallbacks(drawRunner);
    }

    private void fetchNextImage() {
        Track track = mTrackStore.getRandomTrack();
        if(track != null) {
            mTrack = track;
            Log.i(TAG, "fetchNextImage() - waveform URL: " + track.getWaveformUrl());
            WallpaperApplication.getInstance().getVolley().getImageLoader().get(track.getWaveformUrl(),
              new WaveformResponseListener(new WaveformProcessor(), track));
        }
    }

    private class WaveformResponseListener implements ImageLoader.ImageListener {

        BitmapProcessor mTransformer;
        Track mWaveformTrack;

        public WaveformResponseListener(BitmapProcessor _transformer, Track _waveformTrack) {
            mTransformer = _transformer;
            mWaveformTrack = _waveformTrack;
        }

        @Override
        public void onResponse(ImageLoader.ImageContainer _imageContainer, boolean _isImmediate) {
            Log.i(TAG, "WaveformResponseListener onResponse() - isImmediate: " + _isImmediate);
            if(_imageContainer.getBitmap() != null) {
                float[] waveformData = mTransformer.transform(_imageContainer.getBitmap());
                mWaveformTrack.setWaveformData(waveformData);    // FIXME Memory constraints?
                mSchedulerListener.onScheduleEvent();
            }
        }

        @Override
        public void onErrorResponse(VolleyError _volleyError) {
            // Ignore for now
            Log.w(TAG, "WaveformResponseListener Error fetching waveform image", _volleyError.getCause());
        }
    }
}
