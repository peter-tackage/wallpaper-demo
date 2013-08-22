package com.moac.android.wallpaperdemo;

import android.content.Context;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.moac.android.wallpaperdemo.util.BitmapLruCache;

public class SimpleVolley {

    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;

    public SimpleVolley(Context _context) {
        // Single request queue.
        mRequestQueue = Volley.newRequestQueue(_context.getApplicationContext());
        mImageLoader = new ImageLoader(mRequestQueue, new BitmapLruCache());
    }

    public RequestQueue getRequestQueue() { return mRequestQueue; }
    public ImageLoader getImageLoader() { return mImageLoader; }
}
