package com.moac.android.wallpaperdemo;

import android.graphics.Bitmap;

public interface BitmapProcessor {

    /**
     * Do whatever processing we want to make the image look good.
     *
     * @param _bitmap
     */
    public float[] transform(Bitmap _bitmap);
}

