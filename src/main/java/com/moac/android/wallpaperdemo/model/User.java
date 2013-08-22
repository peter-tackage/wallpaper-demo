package com.moac.android.wallpaperdemo.model;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("id") protected String mId;
    @SerializedName("username") protected String mUsername;
    @SerializedName("uri") protected String mUri;

    public String getId() { return mId; }
    public String getUsername() { return mUsername; }
    public String getUri() { return mUri; }
}
