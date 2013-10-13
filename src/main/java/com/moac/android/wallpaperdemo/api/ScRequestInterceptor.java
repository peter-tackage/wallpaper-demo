package com.moac.android.wallpaperdemo.api;

import retrofit.RequestInterceptor;

public class ScRequestInterceptor implements RequestInterceptor{

    public static final String CLIENT_ID_PARAM = "client_id";
    public static final String FORMAT_PARAM = "format";

    private final String mClientId;
    private final String mFormat;

    public ScRequestInterceptor(String _clientId, String _format) {
        mClientId = _clientId;
        mFormat = _format;
    }

    @Override
    public void intercept(RequestFacade request) {
        // Add client id and format type to request
       request.addEncodedQueryParam(CLIENT_ID_PARAM, mClientId);
       request.addEncodedQueryParam(FORMAT_PARAM, mFormat);
    }
}
