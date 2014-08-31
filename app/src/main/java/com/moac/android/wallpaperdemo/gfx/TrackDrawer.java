package com.moac.android.wallpaperdemo.gfx;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import com.moac.android.wallpaperdemo.model.Track;

public class TrackDrawer {

    private static final String TAG = TrackDrawer.class.getSimpleName();

    private static final int DEFAULT_BACKGROUND_COLOR = Color.LTGRAY;
    private static final int DEFAULT_WAVEFORM_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final int DEFAULT_TEXT_SIZE_PX = 16;
    private static final int TEXT_OFFSET_PX = 10;

    private final Paint mBackgroundPaint;
    private final Paint mWaveformPaint;
    private final Paint mTextPaint;
    private final float mColumnWidthPx; // waveform column
    private final float mColumnPaddingPx; // padding between columns

    public TrackDrawer(float columnWidth, float gap) {
        // Define Paint values once
        mBackgroundPaint = buildDefaultBackgroundPaint();
        mWaveformPaint = buildDefaultWaveformPaint();
        mTextPaint = buildDefaultTextPaint();

        // Define column properties
        mColumnWidthPx = columnWidth;
        mColumnPaddingPx = gap;
    }

    public void setColor(int color) {
        mBackgroundPaint.setColor(color);
        mWaveformPaint.setColor(toShade(color));
        mTextPaint.setColor(toShade(color));
    }

    public void drawOn(Canvas canvas, Track track) {

        final float[] waveform = track.getWaveformData();

        // Don't draw if we haven't got anything to draw!
        if(waveform == null || waveform.length == 0) {
            Log.w(TAG, "Track contains empty waveform: " + track.getId());
            return;
        }

        // Draw background
        canvas.drawPaint(mBackgroundPaint);

        Log.d(TAG, "drawOn() - data width: " + waveform.length);
        Log.d(TAG, "drawOn() - canvas width: " + canvas.getWidth());
        Log.d(TAG, "drawOn() - column width & padding width: " + mColumnWidthPx + "," + mColumnPaddingPx);

        float drawableWidth = canvas.getWidth() - mColumnPaddingPx;
        Log.d(TAG, "drawOn() - usableWidth: " + drawableWidth);

        // The number of whole columns that fit in the drawable width with the desired column spacing
        final int columns = (int) (drawableWidth / (mColumnPaddingPx));
        Log.d(TAG, "drawOn() - columns: " + columns);

        // The remainder, we want to shift the columns to the centre of the available width.
        float remainder = drawableWidth % columns;
        Log.d(TAG, "drawOn() - remainder: " + remainder);

        // The number of datapoints that contribute to a column
        final int datapoints = waveform.length / columns;
        Log.d(TAG, "drawOn() - datapoint per column: " + datapoints);

        // Max height to be used by the waveform
        final int heightScalingFactor = canvas.getHeight() / 3;
        final int centreLine = canvas.getHeight() / 2;

        // Incrementing column borders
        float left = (mColumnPaddingPx + remainder) / 2; // initial margin
        float right = left + mColumnWidthPx;

        for(int col = 0; col < columns; col++) {
            Log.v(TAG, "drawOn() - drawing column: " + col);
            Log.v(TAG, "drawOn() - waveform value: " + waveform[col * datapoints]);
            float columnLength = waveform[col * datapoints] * heightScalingFactor;
            float top = centreLine - (columnLength / 2);
            float bottom = top + columnLength;

            Log.v(TAG, "drawOn() - left: " + left + " right: " + right + " top: " + top + " bottom: " + bottom);

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawOval(rect, mWaveformPaint);
            drawTails(canvas, left, right, top, true, 1);
            drawTails(canvas, left, right, bottom, false, 1);

            // Increment for next column
            left = right + mColumnPaddingPx;
            right = left + mColumnWidthPx;
        }

        // Write track title text below waveform
        String title = track.getTitle();
        canvas.drawText(title, canvas.getWidth() / 2f, centreLine + (heightScalingFactor / 2f) + (2f * mColumnWidthPx) + TEXT_OFFSET_PX, mTextPaint);
    }

    // Draws the circles on either top or bottom of the column
    private void drawTails(Canvas canvas, float left, float right, float initY, boolean isTop, int count) {
        float startY = initY;
        for(int i = 0; i < count; i++) {
            startY = isTop ? startY - mColumnWidthPx : startY + mColumnWidthPx;
            canvas.drawCircle(left + ((right - left) / 2f), startY, mColumnWidthPx / 2f, mWaveformPaint);
            startY = isTop ? startY - mColumnWidthPx : startY + mColumnWidthPx;  // Gap
        }
    }

    private static Paint buildDefaultTextPaint() {
        Paint paint = new Paint();
        paint.setColor(DEFAULT_TEXT_COLOR);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);
        paint.setTextSize(DEFAULT_TEXT_SIZE_PX); // TODO Factor in density
        return paint;
    }

    private static Paint buildDefaultWaveformPaint() {
        Paint paint = new Paint();
        paint.setColor(DEFAULT_WAVEFORM_COLOR);
        paint.setAntiAlias(true);
        return paint;
    }

    private static Paint buildDefaultBackgroundPaint() {
        Paint paint = new Paint();
        paint.setColor(DEFAULT_BACKGROUND_COLOR);
        return paint;
    }

    // Create a complementary shade to the provided color
    private static int toShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= 0.5;
        hsv[2] *= 1.5;
        return Color.HSVToColor(hsv);
    }
}
