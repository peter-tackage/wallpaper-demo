package com.moac.android.wallpaperdemo;

import android.graphics.Bitmap;

public interface BitmapProcessor {

    /**
     * Extract the one dimensional data from the bitmap
     */
    public float[] transform(Bitmap _bitmap);
}

