package com.moac.android.wallpaperdemo.api;

import retrofit.RequestInterceptor;

public class ScRequestInterceptor implements RequestInterceptor {

    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String FORMAT_PARAM = "format";

    private final String mClientId;
    private final String mFormat;

    public ScRequestInterceptor(String _clientId, String _format) {
        mClientId = _clientId;
        mFormat = _format;
    }

    @Override
    public void intercept(RequestFacade _request) {
        // Add client id and response data format type to request
        _request.addEncodedQueryParam(CLIENT_ID_PARAM, mClientId);
        _request.addEncodedQueryParam(FORMAT_PARAM, mFormat);
    }
}
