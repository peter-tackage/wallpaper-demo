package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.api.observable.GetTracks;
import com.moac.android.wallpaperdemo.model.Track;
import rx.Observable;

public class SoundCloudApi {

    public static Observable<Track> getApiTracks(SoundCloudClient api, String search, int limit) {
        return Observable.create(new GetTracks(api, search, limit));
    }
}
