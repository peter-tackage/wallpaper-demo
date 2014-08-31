package com.moac.android.wallpaperdemo.gfx;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import com.moac.android.wallpaperdemo.BuildConfig;

import java.util.Arrays;

public class WaveformProcessor implements BitmapProcessor {

    private static final String TAG = WaveformProcessor.class.getSimpleName();
    private static final boolean DEBUG = BuildConfig.DEBUG && false;

    public float[] transform(Bitmap bitmap) {
        // TODO Test performance for extractAlpha vs Color.alpha()
        Log.i(TAG, "transform() - width: " + bitmap.getWidth());
        final int width = bitmap.getWidth();
        final float centreLine = (float) bitmap.getHeight() / 2f;
        float[] normalizedAmplitude = new float[width];
        Arrays.fill(normalizedAmplitude, 0f); // assume no amplitude

        for(int x = 0; x < width; x++) {
            for(int y = 0; y < centreLine; y++) {
                if(Color.alpha(bitmap.getPixel(x, y)) == 0) {
                    if(DEBUG)
                        Log.v(TAG, "transform() - found transparent pixel: " + x + "/" + y);
                    normalizedAmplitude[x] = (centreLine - y) / centreLine;
                    if(DEBUG)
                        Log.v(TAG, "transform() - normalised amp for x: " + x + " is: " + normalizedAmplitude[x]);
                    break; // next sample in x.
                }
            }
        }
        return normalizedAmplitude;
    }
}