package com.moac.android.wallpaperdemo.api.rx;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.moac.android.wallpaperdemo.WaveformProcessor;
import com.moac.android.wallpaperdemo.api.SoundCloudApi;
import com.moac.android.wallpaperdemo.model.Track;
import com.squareup.picasso.Picasso;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetTracksFunction implements Observable.OnSubscribeFunc<List<Track>> {

    private static final String TAG = GetTracksFunction.class.getSimpleName();

    private final Context mContext;
    private final SoundCloudApi mApi;
    private final String mSearch;
    private final int mLimit;

    public GetTracksFunction(Context _context, SoundCloudApi _api, String _search, int _limit) {
        mContext = _context;
        mApi = _api;
        mSearch = _search;
        mLimit = _limit;
    }

    @Override
    public Subscription onSubscribe(Observer<? super List<Track>> _observer) {
        try {
            List<Track> tracks = mApi.getTracks(mSearch, mLimit);
            List<Track> completeTracks = new ArrayList<Track>();
            // FIXME Even if we unsubscribe, it will still loop over all.
            // Or return a BooleanSubscription??
            for(Track track : tracks) {
                try {
                    Bitmap bitmap = Picasso.with(mContext).load(track.getWaveformUrl()).get();
                    float[] waveformData = new WaveformProcessor().transform(bitmap);
                    track.setWaveformData(waveformData);
                    completeTracks.add(track);
                    _observer.onNext(completeTracks);
                } catch(IOException e) {
                    Log.w(TAG, "Failed to get Bitmap for track: " + track.getTitle());
                    // Don't bother telling observer, it's just one track that's failed.
                }
            }
            _observer.onCompleted();
        } catch(Exception e) {
            _observer.onError(e);
        }

        return Subscriptions.empty();
    }
}
