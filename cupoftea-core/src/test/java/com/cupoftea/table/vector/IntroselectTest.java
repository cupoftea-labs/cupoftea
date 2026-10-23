package com.cupoftea.table.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class IntroselectTest {

    @Test
    @DisplayName("Single element array should return that element for any valid rank")
    void test_singleElement() {
        double[] a = new double[] { 42.0 };
        double val = Introselect.select(a, 0, 1, 0);
        assertThat(val).isEqualTo(42.0);
        assertThat(Introselect.quantile(a, 0, 1, 0.0)).isEqualTo(42.0);
        assertThat(Introselect.quantile(a, 0, 1, 0.5)).isEqualTo(42.0);
        assertThat(Introselect.quantile(a, 0, 1, 1.0)).isEqualTo(42.0);
    }

    @Test
    @DisplayName("Empty array should return Double.NaN for quantile and throw for select")
    void test_emptyArray() {
        double[] a = new double[0];
        assertThat(Introselect.quantile(a, 0, 0, 0.5)).isNaN();
        assertThatThrownBy(() -> Introselect.select(a, 0, 0, 0))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Invalid quantile fraction should throw IllegalArgumentException")
    void test_invalidQuantile() {
        double[] a = new double[] { 1.0, 2.0, 3.0 };
        assertThatThrownBy(() -> Introselect.quantile(a, 0, 3, -0.01))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Introselect.quantile(a, 0, 3, 1.01))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Introselect.quantile(a, 0, 3, Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Select on two-element array")
    void test_twoElements() {
        double[] a = new double[] { 9.0, 3.0 };
        assertThat(Introselect.select(a.clone(), 0, 2, 0)).isEqualTo(3.0);
        assertThat(Introselect.select(a.clone(), 0, 2, 1)).isEqualTo(9.0);
        assertThat(Introselect.quantile(a.clone(), 0, 2, 0.5)).isEqualTo(6.0);
    }

    @Test
    @DisplayName("Array with identical elements (zero variance)")
    void test_allDuplicates() {
        double[] a = new double[] { 5.0, 5.0, 5.0, 5.0, 5.0 };
        for (int k = 0; k < a.length; k++) {
            assertThat(Introselect.select(a.clone(), 0, a.length, k)).isEqualTo(5.0);
        }
        assertThat(Introselect.quantile(a.clone(), 0, a.length, 0.5)).isEqualTo(5.0);
        assertThat(Introselect.quantile(a.clone(), 0, a.length, 0.25)).isEqualTo(5.0);
        assertThat(Introselect.quantile(a.clone(), 0, a.length, 0.75)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Quantile interpolation matching pandas for known series")
    void test_quantileInterpolation() {
        // [10, 20, 30, 40]
        double[] a = new double[] { 40.0, 10.0, 30.0, 20.0 };
        assertThat(Introselect.quantile(a.clone(), 0, 4, 0.0)).isEqualTo(10.0);
        assertThat(Introselect.quantile(a.clone(), 0, 4, 0.25)).isEqualTo(17.5);
        assertThat(Introselect.quantile(a.clone(), 0, 4, 0.50)).isEqualTo(25.0);
        assertThat(Introselect.quantile(a.clone(), 0, 4, 0.75)).isEqualTo(32.5);
        assertThat(Introselect.quantile(a.clone(), 0, 4, 1.0)).isEqualTo(40.0);
    }

    @ParameterizedTest
    @ValueSource(ints = { 10, 50, 128, 500 })
    @DisplayName("Introselect should match Arrays.sort on arbitrary random arrays")
    void test_randomArraysVersusSort(int size) {
        Random rand = new Random(42 + size);
        double[] orig = new double[size];
        for (int i = 0; i < size; i++) {
            orig[i] = rand.nextDouble() * 1000.0;
        }

        double[] sorted = orig.clone();
        Arrays.sort(sorted);

        // Test every 5th rank
        for (int k = 0; k < size; k += Math.max(1, size / 10)) {
            double[] copy = orig.clone();
            double selected = Introselect.select(copy, 0, size, k);
            assertThat(selected).isEqualTo(sorted[k]);
        }

        // Test median
        double[] copyForMedian = orig.clone();
        double median = Introselect.quantile(copyForMedian, 0, size, 0.5);
        double expectedMedian = (size % 2 == 1)
            ? sorted[size / 2]
            : (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0;
        assertThat(median).isCloseTo(expectedMedian, offset(1e-9));
    }
}
