package com.moac.android.wallpaperdemo;

import android.graphics.*;
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
    private final float mColumnWidthPx;
    private final float mColumnPaddingPx;

    public TrackDrawer(float _columnWidth, float _gap) {
        // Define Paint values once.
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(DEFAULT_BACKGROUND_COLOR);
        mWaveformPaint = new Paint();
        mWaveformPaint.setColor(DEFAULT_WAVEFORM_COLOR);
        mWaveformPaint.setAntiAlias(true);
        mTextPaint = new Paint();
        mTextPaint.setColor(DEFAULT_TEXT_COLOR);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(16);
        mColumnWidthPx = _columnWidth;
        mColumnPaddingPx = _gap;
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

        // Draw background
        _canvas.drawPaint(mBackgroundPaint);

        // The number of columns that fit in the canvas with the desired column spacing
        final int columns = (int) ((_canvas.getWidth() - 2f* (mColumnPaddingPx)) / (mColumnWidthPx + mColumnPaddingPx));

        // The number of datapoints that contribute to a column
        final int datapoints = waveform.length / columns;

        Log.v(TAG, "drawOn() - data width: " + waveform.length + " columns:" + columns);

        // The display height to be used by the waveform
        final int heightScalingFactor = _canvas.getHeight() / 3;
        final int centreLine = _canvas.getHeight() / 2;

        // Incrementing column borders
        float left = mColumnPaddingPx;
        float right = left + mColumnWidthPx;

        for(int i = 0; i < waveform.length; i += datapoints) {
            Log.v(TAG, "drawOn() - drawing column: " + i);
            Log.v(TAG, "drawOn() - waveform value: " + waveform[i]);
            float columnLength = waveform[i] * heightScalingFactor;
            float top = centreLine - (columnLength / 2);
            float bottom = top + columnLength;

            Log.v(TAG, "drawOn() - left: " + left + " right: " + right + " top: " + top + " bottom: " + bottom);

            RectF rect = new RectF(left, top, right, bottom);
            _canvas.drawOval(rect, mWaveformPaint);
            drawTails(_canvas, left, right, top, true, 1);
            drawTails(_canvas, left, right, bottom, false, 1);

            // Increment for next column
            left = right + mColumnPaddingPx;
            right = left + mColumnWidthPx;
        }

        // Write track title text below waveform
        String title = _track.getTitle();
        _canvas.drawText(title, _canvas.getWidth() / 2f, centreLine + (heightScalingFactor / 2f) + (2f * mColumnWidthPx) + 10, mTextPaint);
    }

    private void drawTails(Canvas _canvas, float left, float right, float initY, boolean isTop, int count) {
        float startY = initY;
        for(int i = 0; i < count; i++) {
            startY = isTop ? startY - mColumnWidthPx : startY + mColumnWidthPx;
            _canvas.drawCircle(left + ((right - left) / 2f), startY, mColumnWidthPx / 2f, mWaveformPaint);
            startY = isTop ? startY - mColumnWidthPx : startY + mColumnWidthPx;  // Gap
        }
    }
}
