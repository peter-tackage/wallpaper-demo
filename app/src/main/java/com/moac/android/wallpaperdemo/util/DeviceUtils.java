package com.moac.android.wallpaperdemo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;

public class DeviceUtils {

    private DeviceUtils() {}

    // Helper - Return true if the network is available and data transfer is allowed.
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //noinspection deprecation
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH ?
                connMgr.getBackgroundDataSetting() :
                connMgr.getActiveNetworkInfo() != null
                        && connMgr.getActiveNetworkInfo().isConnectedOrConnecting();
    }
}
