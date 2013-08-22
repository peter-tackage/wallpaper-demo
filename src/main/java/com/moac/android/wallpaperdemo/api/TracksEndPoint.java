package com.moac.android.wallpaperdemo.api;

import com.google.gson.reflect.TypeToken;
import com.moac.android.wallpaperdemo.model.Track;

import java.util.Collection;
import java.util.List;

/**
 * Refer - http://developers.soundcloud.com/docs/api/reference#tracks
 */
public class TracksEndPoint {

    public static final String GENRE_FILTER = "genres";
    public static final String TAGS_FILTER = "tags";
    public static final String OFFSET_QUERY = "offset";
   // public static final String QUERY_FILTER = "q";

    public static ApiRequest<List<Track>> getTracksByGenre(String _genre) {
        return getTracksByGenre(_genre, 0);
    }

    public static ApiRequest<List<Track>> getTracksByGenre(String _genre, int _offset) {
        ApiRequest<List<Track>> request = new ApiRequest<List<Track>>(new TypeToken<List<Track>>() {}, EndPoints.TRACKS);
        request.withQuery(GENRE_FILTER, _genre);
        request.withQuery(TAGS_FILTER, _genre);
        if(_offset > 0)
            request.withQuery(OFFSET_QUERY, _offset);
        return request;
    }

    public static ApiRequest<Collection<Track>> getTracksByUser(long _userId) {
        String userEndpoint = String.format(EndPoints.TRACKS_USER, _userId);
        return new ApiRequest<Collection<Track>>(new TypeToken<Collection<Track>>() {}, userEndpoint);
    }
}
