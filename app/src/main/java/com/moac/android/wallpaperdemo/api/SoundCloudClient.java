package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.api.model.Track;

import retrofit.http.GET;
import retrofit.http.Query;

import java.util.List;

public interface SoundCloudClient {
    @GET("/tracks")
    public List<Track> getTracks(@Query("q") String search,
                                 @Query("limit") long limit);
}
