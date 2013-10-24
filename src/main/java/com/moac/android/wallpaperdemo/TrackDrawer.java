package com.moac.android.wallpaperdemo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import com.moac.android.wallpaperdemo.model.Track;

public class TrackDrawer {

    private static final String TAG = TrackDrawer.class.getSimpleName();

    private static final int DEFAULT_BACKGROUND_COLOR = Color.LTGRAY;
    private static final int DEFAULT_WAVEFORM_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;

    private final Paint mBackgroundPaint;
    private final Paint mWaveformPaint;
    private final Paint mTextPaint;
    private final int mColumnWidthPx;
    private final int mColumnPaddingPx;

    public TrackDrawer(int _columnWidth) {
        // Define Paint values once.
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(DEFAULT_BACKGROUND_COLOR);
        mWaveformPaint = new Paint();
        mWaveformPaint.setColor(DEFAULT_WAVEFORM_COLOR);
        mTextPaint = new Paint();
        mTextPaint.setColor(DEFAULT_TEXT_COLOR);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mColumnWidthPx = _columnWidth;
        mColumnPaddingPx = _columnWidth;
    }

    public void setBackgroundColor(int _color) {
        mBackgroundPaint.setColor(_color);
    }

    public void setWaveformColor(int _color) {
        mWaveformPaint.setColor(_color);
    }

    public void setTextColor(int _color) {
        mTextPaint.setColor(_color);
    }

    public void drawOn(Canvas _canvas, Track _track) {

        final float[] waveform = _track.getWaveformData();

        // Don't draw if we haven't got anything to draw!
        if(waveform == null || waveform.length == 0) {
            Log.w(TAG, "Track contains empty waveform: " + _track.getId());
            return;
        }

        // Background color
        _canvas.drawPaint(mBackgroundPaint);

        // The number of columns that fit in the canvas with the desired column spacing
        final int columns = _canvas.getWidth() / (mColumnWidthPx + mColumnPaddingPx);

        // The number of datapoints that contribute to a column
        final int datapoints = waveform.length / columns;

        Log.v(TAG, "drawOn() - data width: " + waveform.length + " columns:" + columns);

        // The display height to be used by the waveform
        final int heightScalingFactor = _canvas.getHeight() / 2;
        final int centreLine = _canvas.getHeight() / 2;
        final int centreWidth = _canvas.getWidth() / 2;

        // Column extremities
        float left = 0;
        float right = left + mColumnWidthPx;

        for(int i = 0; i < waveform.length; i += datapoints) {
            Log.v(TAG, "drawOn() - drawing column: " + i);
            Log.v(TAG, "drawOn() - waveform value: " + waveform[i]);
            float columnLength = waveform[i] * heightScalingFactor;
            float top = centreLine - (columnLength / 2);
            float bottom = top + columnLength;

            Log.v(TAG, "drawOn() - left: " + left + " right: " + right + " top: " + top + " bottom: " + bottom);

            _canvas.drawRect(left, top, right, bottom, mWaveformPaint);

            // Update for next column
            left = right + mColumnPaddingPx;
            right = left + mColumnWidthPx;
        }

        // Write track title text below waveform with small buffer
        String title = _track.getTitle();
        _canvas.drawText(title, centreWidth, centreLine + (heightScalingFactor / 2) + 10, mTextPaint);
    }
}
