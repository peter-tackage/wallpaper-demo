package com.moac.android.wallpaperdemo.observable;

import android.graphics.Bitmap;
import android.util.Log;

import com.moac.android.wallpaperdemo.api.model.Track;
import com.moac.android.wallpaperdemo.gfx.WaveformProcessor;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.List;

import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class TrackObservables {

    private static final String TAG = TrackObservables.class.getSimpleName();

    public static Observable<Track> from(Observable<List<Track>> apiObservable, final Picasso picasso) {
        return apiObservable.subscribeOn(Schedulers.io()).flatMap(new Func1<List<Track>, Observable<Track>>() {
            @Override
            public Observable<Track> call(List<Track> tracks) {
                // Process each track individually
                return Observable.from(tracks);
            }
        }).map(new Func1<Track, Track>() {
            @Override
            public Track call(Track track) {
                // Attempt to fetch the image waveform bitmap
                try {
                    Log.i(TAG, "Downloading waveform for track: " + track.getTitle());
                    Bitmap bitmap = picasso.load(track.getWaveformUrl()).get();
                    float[] waveformData = new WaveformProcessor().transform(bitmap);
                    track.setWaveformData(waveformData);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to get Bitmap for track: " + track.getTitle(), e);
                    // We will filter this track from the results
                }
                return track;
            }
        }).filter(new Func1<Track, Boolean>() {
            @Override
            public Boolean call(Track track) {
                // Remove tracks with no waveform data
                return track.getWaveformData() != null && track.getWaveformData().length != 0;
            }
        });
    }
}
