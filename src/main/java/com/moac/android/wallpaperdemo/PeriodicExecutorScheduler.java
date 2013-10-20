package com.moac.android.wallpaperdemo;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PeriodicExecutorScheduler implements Scheduler {

    private static final String TAG = PeriodicExecutorScheduler.class.getSimpleName();

    private final ScheduledExecutorService mScheduler =
      Executors.newSingleThreadScheduledExecutor();

    private final long mPeriod;
    private final TimeUnit mUnits;
    private final Runnable mRunnable;

    private ScheduledFuture mFuture;
    private long mScheduledFor;

    public PeriodicExecutorScheduler(Runnable _runnable, long _period, TimeUnit _units) {
        Log.i(TAG, "Creating PeriodicExecutorScheduler with period: " + _period + " " + _units.toString());
        mRunnable = _runnable;
        mPeriod = _period;
        mUnits = _units;
    }

    @Override
    public void start() {
        Log.i(TAG, "Starting Schedule");
        if(mFuture != null) {
            //  mFuture.cancel(true);
            return; // ignore, don't hard restart.
        }

        // If we never got past the previously scheduled execution time
        // then add a delay to keep the updates vaguely periodic, otherwise
        // execute immediately.
        long now = android.os.SystemClock.uptimeMillis();
        long delay = now < mScheduledFor ? mScheduledFor - now : 0;

        mFuture =
          mScheduler.scheduleWithFixedDelay(mRunnable, delay, mPeriod, mUnits);
    }

    @Override
    public void pause() {
        // TODO Or possible just set a flag to not draw...
        Log.i(TAG, "Pausing Schedule");
        mFuture.cancel(false);
        long delay = mFuture.getDelay(mUnits);
        long now = mUnits.convert(android.os.SystemClock.uptimeMillis(), TimeUnit.MILLISECONDS);
        mScheduledFor = now + delay;
        Log.i(TAG, "pause() - was scheduledFor: " + mScheduledFor);
    }

    @Override
    public void resume() {
        // TODO
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping Schedule");
        mScheduledFor = 0;
        mFuture.cancel(true);
    }
}
