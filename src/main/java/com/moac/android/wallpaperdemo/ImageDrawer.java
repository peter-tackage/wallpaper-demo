package com.moac.android.wallpaperdemo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.util.Log;
import com.moac.android.wallpaperdemo.model.Track;

public class ImageDrawer {

    private static final String TAG = ImageDrawer.class.getSimpleName();

    /*
     * Paints - defined here as constant for performance.
	 */
    private static final int BACKGROUND_COLOR = Color.DKGRAY;
    private static final int COLUMN_GAP_WIDTH_DIP = 10;

    private static final Paint TEXT_PAINT;

    static {
        TEXT_PAINT = new Paint();
        TEXT_PAINT.setColor(Color.WHITE);
        TEXT_PAINT.setTextAlign(Paint.Align.CENTER);
        TEXT_PAINT.setTextSize(24);
        TEXT_PAINT.setAntiAlias(true);
    }

    private static final Paint WAVEFORM_PAINT;

    static {
        WAVEFORM_PAINT = new Paint();
        WAVEFORM_PAINT.setDither(true);
        WAVEFORM_PAINT.setAntiAlias(true);
        WAVEFORM_PAINT.setFilterBitmap(true);
        WAVEFORM_PAINT.setColor(Color.WHITE);
    }

    private static final Paint BACKGROUND_PAINT;

    static {
        BACKGROUND_PAINT = new Paint();
        BACKGROUND_PAINT.setColor(BACKGROUND_COLOR);
    }

    private static final int DEFAULT_COLUMNS = 120;

    private int mColumns = DEFAULT_COLUMNS;

    public void setColumns(int _columns) {
        mColumns = _columns;
    }

    public void drawOn(Canvas _canvas, Track _track) {

        if(_track == null) {
            drawPlaceholderOn(_canvas);
            return;
        }

        final float[] waveform = _track.getWaveformData();

        /**
         * If we have a Track, but the waveform is null, don't bother updating.
         * Instead, keep showing placeholder or the current waveform.
         *
         * This could occur if there is a failure to retrieve the track's
         * image or if the image processing somehow failed.
         */
        if(waveform != null) {
            Log.v(TAG, "drawOn() - data width: " + waveform.length + " columns:" + mColumns);

            _canvas.drawColor(BACKGROUND_COLOR);

            final int columnIndexFactor = waveform.length / mColumns;
            final int columnWidth = _canvas.getWidth() / mColumns;
            final int heightScalingFactor = _canvas.getHeight() / 2;
            final int centreLine = _canvas.getHeight() / 2;

            float left = 0;
            float right = left + columnWidth;

            LinearGradient gradient = new LinearGradient(
              0,
              _canvas.getHeight() / 4,
              0,
              centreLine + _canvas.getHeight() / 4,
              0xFFFF8500, 0xFFFF1009,
              android.graphics.Shader.TileMode.MIRROR);

            for(int i = 0; i < mColumns; i++) {
                Log.v(TAG, "drawOn() - drawing column: " + i);
                Log.v(TAG, "drawOn() - waveform value: " + waveform[i * columnIndexFactor]);
                float columnLength = waveform[i * columnIndexFactor] * heightScalingFactor;
                float top = centreLine - (columnLength / 2);
                float bottom = top + columnLength;

                Log.v(TAG, "drawOn() - left: " + left + " right: " + right + " top: " + top + " bottom: " + bottom);

                Paint p = new Paint();
                p.setDither(true);
                p.setShader(gradient);

                _canvas.drawRect(left, top, right, bottom, p);
                left = right + COLUMN_GAP_WIDTH_DIP;
                right = left + columnWidth;
            }
        }
    }

    private void drawPlaceholderOn(Canvas _canvas) {
        _canvas.drawColor(BACKGROUND_COLOR);
        _canvas.drawText("Wallpaper Demo", _canvas.getWidth() / 2, _canvas.getHeight() / 2, TEXT_PAINT);
    }
}
