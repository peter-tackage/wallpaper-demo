package com.moac.android.wallpaperdemo.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Track {

    @SerializedName("id") private long id;
    @SerializedName("title") private String title;
    @SerializedName("user") private User user;
    @SerializedName("waveform_url") private String waveformUrl;
    @SerializedName("permalink_url") private String permalinkUrl;

    // Calculated from Bitmap data, not from API response
    @Expose(deserialize = false) protected float[] waveformData;

    public long getId() { return id; }

    public String getTitle() { return title; }

    public User getUser() { return user; }

    public String getWaveformUrl() { return waveformUrl; }

    public String getPermalinkUrl() { return permalinkUrl; }

    public float[] getWaveformData() { return waveformData; }

    public void setWaveformData(float[] waveformData) {
        this.waveformData = waveformData;
    }
}
