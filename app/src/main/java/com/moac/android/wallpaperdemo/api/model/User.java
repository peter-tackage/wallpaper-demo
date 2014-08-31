package com.moac.android.wallpaperdemo.api.model;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("id") protected String id;
    @SerializedName("username") protected String username;
    @SerializedName("uri") protected String uri;

    public String getId() { return id; }

    public String getUsername() { return username; }

    public String getUri() { return uri; }
}
