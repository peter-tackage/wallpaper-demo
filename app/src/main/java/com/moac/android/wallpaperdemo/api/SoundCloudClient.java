package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.api.model.Track;

import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

import java.util.List;

public interface SoundCloudClient {
    @GET("/tracks")
    public Observable<List<Track>> getTracks(@Query("q") String search,
                                 @Query("limit") long limit);
}
