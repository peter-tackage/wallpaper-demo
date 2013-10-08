package com.moac.android.wallpaperdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.Log;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.moac.android.wallpaperdemo.model.Track;

import java.util.concurrent.TimeUnit;

public class ImageScheduler {

    private static final long DEFAULT_WAVEFORM_INTERVAL_SEC = 30;

    private static final String TAG = ImageScheduler.class.getSimpleName();

    private final Context mContext;
    private final ScheduleListener mScheduleListener;
    private Handler mHandler;
    private ImageDrawer mImageDrawer;
    private TrackStore mTrackStore;
    private Track mTrack;
    private long mInterval;

    private final Runnable drawRunner = new Runnable() {
        @Override
        public void run() {
            try {
                // TODO Dynamically change the column count.
                fetchNextImage();
            } finally {
                // Retask
                mHandler.postDelayed(this,
                  TimeUnit.MILLISECONDS.convert(mInterval, TimeUnit.SECONDS));
            }
        }
    };
    private long mPausedAt = 0;
    private long mScheduledFor = 0;

    public ImageScheduler(Context _context, ImageDrawer _imageDrawer,
                          TrackStore _trackStore, ScheduleListener _listener) {
        this(_context, _imageDrawer, _trackStore, _listener, DEFAULT_WAVEFORM_INTERVAL_SEC);
    }

    public ImageScheduler(Context _context, ImageDrawer _imageDrawer,
                          TrackStore _trackStore, ScheduleListener _listener, long _interval) {
        mContext = _context;
        mHandler = new Handler();
        mScheduleListener = _listener;
        mImageDrawer = _imageDrawer;
        mTrackStore = _trackStore;
        mInterval = _interval;
    }

//    // Define connectivity first.
//    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//    boolean isDataAllowed = connMgr.getBackgroundDataSetting();
//    // ICS will only ever return true for getBackgroundDataSetting().
//    // Apparently uses getActiveNetworkInfo
//    boolean ICSDataAllowed = connMgr.getActiveNetworkInfo() != null
//      && connMgr.getActiveNetworkInfo().isAvailable();

    public void draw(final Canvas c) {
        Log.v(TAG, "draw() - start");
        // Canvas is locked here.
        if(mTrack != null && mTrack.getWaveformData() != null) {
            Log.v(TAG, "draw() - is valid!");
            mImageDrawer.drawOn(c, mTrack.getWaveformData(), 125);
        }
    }

    public void pause() {
        mHandler.removeCallbacks(drawRunner);
        mPausedAt = android.os.SystemClock.uptimeMillis();
    }

    public void start() {
        final long now = android.os.SystemClock.uptimeMillis();
        final long interval = TimeUnit.MILLISECONDS.convert(mInterval, TimeUnit.SECONDS);
        long delay = 0;
        if(now - interval < mScheduledFor) {
            // Reschedule its original time to remain periodic
            delay = mScheduledFor + interval - mPausedAt;
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
            WallpaperApplication.getInstance().getImageLoader().get(track.getWaveformUrl(),
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
                // TODO This transform is on the main thread.
                float[] waveformData = mTransformer.transform(_imageContainer.getBitmap());
                mWaveformTrack.setWaveformData(waveformData);    // FIXME Memory constraints?
                // TODO Otto bus?
                mScheduleListener.onScheduleEvent();
            }
        }

        @Override
        public void onErrorResponse(VolleyError _volleyError) {
            // Ignore for now
            Log.w(TAG, "WaveformResponseListener Error fetching waveform image", _volleyError.getCause());
        }
    }
}
