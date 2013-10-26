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

//    public static float[] downSample(float[] _values, int _columns) {
//        float[] result = new float[_columns];
//        int datapoints = _values.length / _columns;
//        System.out.println("Datapoint per column: " + datapoints);
//
//        System.out.println("Length is: " + _values.length);
//        for(int x = 0; x < _values.length; x+=datapoints) {
//            System.out.println("x is outer: " + x);
//            float block = 0f;
//            int size = _values.length % x == 0 ? datapoints : _values.length % x;
//            int end = x + size;
//            for(int y = x; y < end; y++) {
//                   System.out.println("Y is: " + y);
//                    block += _values[y];
//                    size++;
//            }
//            block = block / (float)size;
//            System.out.println("block avg: " + block);
//        }
//
//        return null;
//    }
//
//    public static float avg(float[] _vals) {
//       float total = 0;
//       for(int i =0; i < _vals.length; i++) {
//           total += _vals[i];
//       }
//       return total / (float)_vals.length;
//    }
}
