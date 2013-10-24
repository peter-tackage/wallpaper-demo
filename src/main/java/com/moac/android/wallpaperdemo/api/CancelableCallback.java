package com.moac.android.wallpaperdemo.api;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class CancelableCallback<T> implements Callback<T> {

    private Callback<T> mCallback;
    private boolean isCancelled;

    public CancelableCallback(Callback<T> _callback) {
        mCallback = _callback;
    }

    @Override
    public void success(T t, Response response) {
        if(!isCancelled)
            mCallback.success(t, response);
    }

    @Override
    public void failure(RetrofitError error) {
        if(!isCancelled)
            mCallback.failure(error);
    }

    public void cancel() {
        isCancelled = true;
        mCallback = null;
    }
}
