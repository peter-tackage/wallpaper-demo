package com.moac.android.wallpaperdemo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.util.Log;
import com.moac.android.wallpaperdemo.model.Track;

public class TrackDrawer {

    private static final String TAG = TrackDrawer.class.getSimpleName();

    private final Paint mBackgroundPaint;
    private final Paint mWaveformPaint;
    private final Paint mTextPaint;
    private final int mColumns;
    private final int mColumnGap;

    public TrackDrawer(Paint _backgroundPaint, Paint _waveformPaint, Paint _textPaint, int _columns, int _columnGap) {
        mBackgroundPaint = _backgroundPaint;
        mWaveformPaint = _waveformPaint;
        mTextPaint = _textPaint;
        mColumns = _columns;
        mColumnGap = _columnGap;
    }

    public void drawOn(Canvas _canvas, Track _track) {

        final float[] waveform = _track.getWaveformData();

        // Don't draw on Canvas if we haven't got anything to draw!
        if(waveform == null || waveform.length == 0) {
            Log.w(TAG, "Track contains empty waveform: " + _track.getId());
            return;
        }

        Log.v(TAG, "drawOn() - data width: " + waveform.length + " columns:" + mColumns);

        _canvas.drawPaint(mBackgroundPaint);

        final int columnIndexFactor = waveform.length / mColumns;
        final int columnWidth = _canvas.getWidth() / mColumns;
        // How much of the display should be used up.
        final int heightScalingFactor = _canvas.getHeight() / 2;
        final int centreLine = _canvas.getHeight() / 2;
        final int centreWidth = _canvas.getWidth() / 2;

        float left = 0;
        float right = left + columnWidth;

//            // FIXME This only varies when the dimensions change
//            LinearGradient gradient = new LinearGradient(
//              0,
//              _canvas.getHeight() / 4,
//              0,
//              centreLine + _canvas.getHeight() / 4,
//              0xFFFF8500, 0xFFFF1009,
//              android.graphics.Shader.TileMode.MIRROR);

        for(int i = 0; i < mColumns; i++) {
            Log.v(TAG, "drawOn() - drawing column: " + i);
            Log.v(TAG, "drawOn() - waveform value: " + waveform[i * columnIndexFactor]);
            float columnLength = waveform[i * columnIndexFactor] * heightScalingFactor;
            float top = centreLine - (columnLength / 2);
            float bottom = top + columnLength;

            Log.v(TAG, "drawOn() - left: " + left + " right: " + right + " top: " + top + " bottom: " + bottom);

//                Paint p = new Paint();
//                p.setDither(true);
//                p.setShader(gradient);

            _canvas.drawRect(left, top, right, bottom, mWaveformPaint);
            left = right + mColumnGap;
            right = left + columnWidth;
        }

        // Display track text below waveform with small buffer
        String title = _track.getTitle();
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        _canvas.drawText(title, centreWidth, centreLine + (heightScalingFactor /2) + 10, mTextPaint);
    }
}
