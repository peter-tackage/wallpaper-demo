package com.moac.android.wallpaperdemo.gfx;

import android.graphics.Bitmap;

public interface BitmapProcessor {

    /**
     * Extract the one dimensional data from the bitmap
     */
    public float[] transform(Bitmap bitmap);
}

