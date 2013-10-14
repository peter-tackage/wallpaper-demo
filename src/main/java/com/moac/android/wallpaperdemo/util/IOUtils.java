package com.moac.android.wallpaperdemo.util;

import java.io.IOException;
import java.io.InputStream;

public class IOUtils {

    public static void closeQuietly(InputStream _stream) {
        if(_stream != null) {
            try {
                _stream.close();
            } catch(IOException e) {
                // ignore
            }
        }
    }
}
