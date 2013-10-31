package com.moac.android.wallpaperdemo.api.rx;

import com.moac.android.wallpaperdemo.api.SoundCloudClient;
import com.moac.android.wallpaperdemo.model.Track;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.util.List;

public class GetTracks implements Observable.OnSubscribeFunc<Track> {

    private static final String TAG = GetTracks.class.getSimpleName();

    private final SoundCloudClient mApi;
    private final String mSearch;
    private final int mLimit;

    public GetTracks(SoundCloudClient _api, String _search, int _limit) {
        mApi = _api;
        mSearch = _search;
        mLimit = _limit;
    }

    @Override
    public Subscription onSubscribe(Observer<? super Track> _observer) {
        try {
            List<Track> tracks = mApi.getTracks(mSearch, mLimit);
            // TODO Perhaps indicate no tracks as an error?
            for(Track track : tracks) {
                _observer.onNext(track);
            }
            _observer.onCompleted();
        } catch(Exception e) {
            _observer.onError(e);
        }
        return Subscriptions.empty();
    }
}
