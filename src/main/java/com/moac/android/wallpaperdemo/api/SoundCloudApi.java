package com.moac.android.wallpaperdemo.api;

import com.moac.android.wallpaperdemo.model.Track;
import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Query;

import java.util.List;

public interface SoundCloudApi {
    @GET("/tracks")
    public List<Track> getTrackList(@Query("genre") String _genre,
                                 @Query("tags") String _tags, @Query("offset") long _offset);

    @GET("/tracks")
    public void getTrackList(@Query("genre") String _genre,
                                    @Query("tags") String _tags, @Query("offset") long _offset, Callback<List<Track>> _callback);

    @GET("/tracks")
    public Track getTrack(@Query("genre") String _genre,
                                 @Query("tags") String _tags, @Query("offset") long _offset);
}
