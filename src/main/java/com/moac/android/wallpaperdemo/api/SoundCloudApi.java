package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.api.rx.GetTracks;
import com.moac.android.wallpaperdemo.model.Track;
import rx.Observable;

import java.util.List;

public class SoundCloudApi {

    public static Observable<Track> getApiTracks(SoundCloudClient _api, String _search, int _limit) {
        return Observable.create(new GetTracks(_api, _search, _limit));
    }
}
