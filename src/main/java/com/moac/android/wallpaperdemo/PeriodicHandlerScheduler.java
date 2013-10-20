package com.moac.android.wallpaperdemo;

import android.os.Handler;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * This is strictly periodic when pausing.
 *
 * Good things about this approach -
 *
 * 1. Simple! No messy calculations about when the next task should be due
 * 2. Performance - No forced action when unpausing due to "catching up" on updates
 * 3. Schedule with fixed delay is performed.
 *
 * Bad things -
 * 1. Not thread safe - only one thread can control it.
 * 2. Handler is always posted a Runnable, even when paused, it just does no real work.
 */
public class PeriodicHandlerScheduler implements Scheduler {

    private static final String TAG = PeriodicHandlerScheduler.class.getSimpleName();

    private final Runnable mRunnable;
    private final Handler mHandler;
    private boolean mIsPaused;
    private boolean mIsStarted;

    public PeriodicHandlerScheduler(final Runnable _runnable, final long _period, final TimeUnit _units) {
        Log.i(TAG, "Creating PeriodicHandlerScheduler with period: " + _period + " " + _units.toString());
        mIsPaused = false;
        mIsStarted = false;
        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
                if(!mIsPaused)
                    _runnable.run();
                mHandler.postDelayed(this, TimeUnit.MILLISECONDS.convert(_period, _units));
            }
        };
    }

    @Override
    public void start() {
        Log.i(TAG, "Starting Schedule");
        if(!mIsStarted)
            mIsStarted = mHandler.post(mRunnable);
    }

    @Override
    public void pause() {
        mIsPaused = true;
    }

    @Override
    public void resume() {
        mIsPaused = false;
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping Schedule");
        mHandler.removeCallbacks(mRunnable);
        mIsPaused = false;
        mIsStarted = false;
    }
}
