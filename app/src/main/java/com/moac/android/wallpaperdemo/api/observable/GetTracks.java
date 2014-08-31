package com.moac.android.wallpaperdemo.api.observable;

import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.api.model.Track;

import java.util.List;

import rx.Observable;
import rx.Subscriber;

public class GetTracks implements Observable.OnSubscribe<Track> {

    private final SoundCloudClient mApi;
    private final String mSearch;
    private final int mLimit;

    public GetTracks(SoundCloudClient api, String search, int limit) {
        mApi = api;
        mSearch = search;
        mLimit = limit;
    }

    @Override
    public void call(Subscriber<? super Track> subscriber) {
        try {
            List<Track> tracks = mApi.getTracks(mSearch, mLimit);
            for (Track track : tracks) {
                subscriber.onNext(track);
            }
            subscriber.onCompleted();
        } catch (Exception e) {
            subscriber.onError(e);
        }
    }
}
