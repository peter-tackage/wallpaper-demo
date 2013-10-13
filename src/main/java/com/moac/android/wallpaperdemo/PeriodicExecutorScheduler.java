package com.moac.android.wallpaperdemo;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TODO I feel like this is weird hybrid of an Executor and TimerTask
 * It's really a scheduler either, it's more of a Task that schedules itself.
 */
public abstract class PeriodicExecutorScheduler implements Scheduler {

    private static final String TAG = PeriodicExecutorScheduler.class.getSimpleName();

    private final ScheduledExecutorService mScheduler =
      Executors.newSingleThreadScheduledExecutor();

    private final long mPeriod;
    private final TimeUnit mUnits;
    private final Runnable mRunnable;

    private ScheduledFuture mFuture;
    private long mScheduledFor;

    public PeriodicExecutorScheduler(long _period, TimeUnit _units) {
        mRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "performTask() called");
                performTask();
            }
        };
        mPeriod = _period;
        mUnits = _units;
    }

    @Override
    public void start() {
        Log.i(TAG, "Starting Task");
        if(mFuture != null)
            mFuture.cancel(false);
        // If we never got past the previously scheduled execution time
        // then add a delay to keep the updates vaguely periodic, otherwise
        // execute immediately.
        long now = android.os.SystemClock.uptimeMillis();
        long delay = now < mScheduledFor ? mScheduledFor - now : 0;

        mFuture =
          mScheduler.scheduleAtFixedRate(mRunnable, delay, mPeriod, mUnits);
    }

    @Override
    public void pause() {
        Log.i(TAG, "Pausing Task");

        mFuture.cancel(false);
        long delay = mFuture.getDelay(mUnits);
        long now = mUnits.convert(android.os.SystemClock.uptimeMillis(), TimeUnit.MILLISECONDS);
        mScheduledFor =  now + delay;
        Log.i(TAG, "pause() - was scheduledFor: " + mScheduledFor);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping Task");
        mScheduledFor = 0;
        mFuture.cancel(false);
    }

    protected abstract void performTask();
}
