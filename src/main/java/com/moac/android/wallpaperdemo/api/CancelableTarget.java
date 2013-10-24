package com.moac.android.wallpaperdemo.api;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class CancelableTarget implements Target {

    protected Target mTarget;
    private boolean mIsCancelled;

    public CancelableTarget(Target _target) {
        mTarget = _target;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        if(!mIsCancelled)
            mTarget.onBitmapLoaded(bitmap, from);
    }

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        if(!mIsCancelled)
            mTarget.onBitmapFailed(errorDrawable);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
        if(!mIsCancelled)
            mTarget.onPrepareLoad(placeHolderDrawable);
    }

    public void cancel() {
        mIsCancelled = true;
        mTarget = null;
    }
}
