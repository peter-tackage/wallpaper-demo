package com.moac.android.wallpaperdemo.util;

public class Preconditions {
    private Preconditions() {}
    // From Guava
    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }
}
