package com.moac.android.wallpaperdemo.util;

import java.util.List;
import java.util.Random;

public class NumberUtils {

    public static <T> T getRandomElement(T[] array) {
        if(array == null || array.length == 0)
            throw new IllegalArgumentException("Array length must be positive");
        if(array.length == 1)
            return array[0];

        Random random = new Random();
        int index = random.nextInt(array.length);
        return array[index];
    }

    public static <T> T getRandomElement(List<T> list) {
        if(list == null || list.size() == 0)
            throw new IllegalArgumentException("List length must be positive");
        if(list.size() == 1)
            return list.get(0);

        Random random = new Random();
        int index = random.nextInt(list.size());
        return list.get(index);
    }
}
