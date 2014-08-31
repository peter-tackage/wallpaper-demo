package com.moac.android.wallpaperdemo.util;

import android.test.AndroidTestCase;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Sadly not using JUnit 4
public class NumberUtilsTest extends AndroidTestCase {

    /**
     * Sanity check tests
     */

    public void test_nullInputShouldThrow() {
        try {
            NumberUtils.getRandomElement((Integer[]) null);
            fail("Null array should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Pass!
        }
    }

    public void test_zeroLengthInputShouldThrow() {
        Integer[] ints = new Integer[0];
        try {
            NumberUtils.getRandomElement(ints);
            fail("Empty array should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Pass!
        }
    }

    public void test_oneElementInputReturnsElement() {
        Integer[] ints = {1};
        assertThat(NumberUtils.getRandomElement(ints)).isEqualTo(1);
    }

    public void test_moreThanOneLengthReturnsValue() {
        Integer[] ints = {1, 2};
        assertThat(Arrays.asList(ints).contains(NumberUtils.getRandomElement(ints)));
    }
}
