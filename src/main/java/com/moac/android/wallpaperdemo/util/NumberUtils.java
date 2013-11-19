package com.moac.android.wallpaperdemo.util;

import java.util.List;
import java.util.Random;

public class NumberUtils {

    public static <T> T getRandomElement(T[] _array) {
        if(_array == null || _array.length == 0)
            throw new IllegalArgumentException("Array length must be positive");
        if(_array.length == 1)
            return _array[0];

        Random random = new Random();
        int index = random.nextInt(_array.length);
        return _array[index];
    }

    public static <T> T getRandomElement(List<T> _list) {
        if(_list == null || _list.size() == 0)
            throw new IllegalArgumentException("List length must be positive");
        if(_list.size() == 1)
            return _list.get(0);

        Random random = new Random();
        int index = random.nextInt(_list.size());
        return _list.get(index);
    }
}
