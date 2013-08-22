package com.moac.android.wallpaperdemo;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.Arrays;

public class WaveformProcessor implements BitmapProcessor {

    private static final String TAG = WaveformProcessor.class.getSimpleName();

    public float[] transform(Bitmap _bitmap) {
        // TODO Test performance for extractAlpha vs Color.alpha()

        final int width = _bitmap.getWidth();
        final float centreLine = (float)_bitmap.getHeight() / 2f;
        float[] normalizedAmplitude = new float[width];
        Arrays.fill(normalizedAmplitude, 0f); // assume no amplitude

        for(int x = 0; x < width; x++) {
            for(int y = 0; y < centreLine; y++) {
                if(Color.alpha(_bitmap.getPixel(x,y)) == 0) {
             //       Log.v(TAG, "transform() - found transparent pixel: " + x +"/" +y);
                    normalizedAmplitude[x] = (centreLine - y) / centreLine;
             //       Log.v(TAG, "normalised amp for x: " + x + " is: " + normalizedAmplitude[x]);
                    break; // next sample in x.
                }
            }
        }
        return normalizedAmplitude;
    }
}