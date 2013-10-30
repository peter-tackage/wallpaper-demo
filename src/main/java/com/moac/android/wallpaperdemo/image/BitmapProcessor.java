package com.moac.android.wallpaperdemo.image;

import android.graphics.Bitmap;

public interface BitmapProcessor {

    /**
     * Extract the one dimensional data from the bitmap
     */
    public float[] transform(Bitmap _bitmap);
}

