package com.moac.android.wallpaperdemo;

import android.graphics.Bitmap;

public interface BitmapProcessor {

    /**
     * Do whatever processing we want to make the image look good.
     *
     * The idea is to ensure that this processing can be done once
     * and outside of the main thread.
     *
     * @param bitmap
     */
    public float[] transform(Bitmap bitmap);
}

