package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Query;

import java.util.List;

public interface SoundCloudClient {
    @GET("/tracks")
    public void getTracks(@Query("genres") String _genre,
                          @Query("limit") long _limit,
                          Callback<List<Track>> _callback);

    @GET("/tracks")
    public List<Track> getTracks(@Query("q") String _search,
                                 @Query("limit") long _limit);
}
