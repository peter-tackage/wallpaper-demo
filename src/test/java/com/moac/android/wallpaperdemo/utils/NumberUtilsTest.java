package com.moac.android.wallpaperdemo.utils;

import com.moac.android.wallpaperdemo.util.NumberUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class NumberUtilsTest {

    /**
     * Sanity check tests
     */

    @Test
    public void test_nullInputShouldThrow() {
        try {
            NumberUtils.getRandomElement((Integer[])null);
            fail("Null array should throw IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            // Pass!
        }
    }

    @Test
    public void test_zeroLengthInputShouldThrow() {
        Integer[] ints = new Integer[0];
        try {
            NumberUtils.getRandomElement(ints);
            fail("Empty array should throw IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            // Pass!
        }
    }

    @Test
    public void test_oneElementInputReturnsElement() {
        Integer[] ints = { 1 };
        assertThat(NumberUtils.getRandomElement(ints), equalTo(1));
    }

    @Test
    public void test_moreThanOneLengthReturnsValue() {
        Integer[] ints = { 1, 2 };
        assertTrue(Arrays.asList(ints).contains(NumberUtils.getRandomElement(ints)));
    }
}
