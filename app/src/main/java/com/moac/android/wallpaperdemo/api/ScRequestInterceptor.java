package com.moac.android.wallpaperdemo.api;

import retrofit.RequestInterceptor;

public class ScRequestInterceptor implements RequestInterceptor {

    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String FORMAT_PARAM = "format";

    private final String mClientId;
    private final String mFormat;

    public ScRequestInterceptor(String clientId, String format) {
        mClientId = clientId;
        mFormat = format;
    }

    @Override
    public void intercept(RequestFacade request) {
        // Add client id and response data format type to request
        request.addEncodedQueryParam(CLIENT_ID_PARAM, mClientId);
        request.addEncodedQueryParam(FORMAT_PARAM, mFormat);
    }
}
