package com.moac.android.wallpaperdemo.api;

import retrofit.RequestInterceptor;

public class ScRequestInterceptor implements RequestInterceptor{

    public static final String CLIENT_ID_PARAM = "client_id";

    private final String mClientId;

    public ScRequestInterceptor(String _clientId) {
        mClientId = _clientId;
    }

    @Override
    public void intercept(RequestFacade request) {
        // Add client id to request
       request.addEncodedQueryParam(CLIENT_ID_PARAM, mClientId);
    }
}
