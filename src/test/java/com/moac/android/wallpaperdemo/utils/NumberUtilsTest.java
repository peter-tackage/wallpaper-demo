package com.moac.android.wallpaperdemo.utils;

import com.moac.android.wallpaperdemo.util.NumberUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class NumberUtilsTest {

    @Test
    public void test_nullInputShouldThrow() {
        Integer[] integers = null;
        try {
            NumberUtils.getRandomElement(integers);
            fail("Null array should throw IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            // Pass!
        }
    }

    @Test
    public void test_zeroLengthInputShouldThrow() {
        Integer[] integers = new Integer[0];
        try {
            NumberUtils.getRandomElement(integers);
            fail("Empty array should throw IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            // Pass!
        }
    }

    @Test
    public void test_oneElementInputReturnsElement() {
        Integer[] integers = { 1 };
        assertThat(NumberUtils.getRandomElement(integers), equalTo(1));
    }

    @Test
    public void test_moreThanOneLengthReturnsValue() {
        Integer[] integers = { 1, 2 };
        assertTrue(Arrays.asList(integers).contains(NumberUtils.getRandomElement(integers)));
    }

//    @Test
//    public void testit() {
//        NumberUtils.downSample(new float[]{ 1, 2, 1, 2, 1, 2, 1 }, 3);
//    }
}
