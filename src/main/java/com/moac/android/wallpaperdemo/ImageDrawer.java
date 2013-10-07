package com.moac.android.wallpaperdemo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.util.Log;

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

    public void drawOn(Canvas _canvas, float[] _waveform, int _columns) {
        _canvas.drawColor(BACKGROUND_COLOR);

        Log.v(TAG, "drawOn() - data width: " + _waveform.length + " columns:" + _columns);

        final int columnIndexFactor = _waveform.length / _columns;
        final int columnWidth =  _canvas.getWidth() / _columns;
        final int heightScalingFactor = _canvas.getHeight() / 2;
        final int centreLine = _canvas.getHeight() / 2;

        float left = 0;
        float right = left + columnWidth;

        LinearGradient gradient = new LinearGradient(
          0,
          _canvas.getHeight() / 4,
          0,
          centreLine + _canvas.getHeight() /4 ,
          0xFFFF8500, 0xFFFF1009,
          android.graphics.Shader.TileMode.MIRROR);

        for(int i = 0; i < _columns; i++) {
            Log.v(TAG, "drawOn() - drawing column: " + i);
            Log.v(TAG, "drawOn() - waveform value: " + _waveform[i * columnIndexFactor]);
            float columnLength = _waveform[i * columnIndexFactor] * heightScalingFactor;
            float top =  centreLine - (columnLength /2);
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
