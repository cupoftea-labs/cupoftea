package com.cupoftea.table.vector;

import java.util.Arrays;

/**
 * High-performance Introselect (hybrid Quickselect with fallback) for linear O(n)
 * rank selection, median, and quantile calculations on primitive double arrays.
 */
public final class Introselect {

    private static final int INSERTION_SORT_THRESHOLD = 16;

    private Introselect() {}

    /**
     * Selects the k-th smallest element (0-indexed) in array {@code a} between
     * {@code fromIndex} (inclusive) and {@code toIndex} (exclusive) in guaranteed O(n)
     * worst-case time.
     * Rearranges elements in {@code a[fromIndex..toIndex-1]} such that {@code a[k]}
     * contains the k-th element, all elements before k are &le; a[k], and all elements
     * after k are &ge; a[k].
     *
     * @param a the primitive double array to partition
     * @param fromIndex start index (inclusive)
     * @param toIndex end index (exclusive)
     * @param k target 0-indexed rank (must be in [fromIndex, toIndex - 1])
     * @return the value of the k-th smallest element
     */
    public static double select(double[] a, int fromIndex, int toIndex, int k) {
        if (a == null) {
            throw new IllegalArgumentException("Array cannot be null");
        }
        if (fromIndex < 0 || toIndex > a.length || fromIndex >= toIndex) {
            throw new IndexOutOfBoundsException("Invalid range [" + fromIndex + ", " + toIndex + ") for array length " + a.length);
        }
        if (k < fromIndex || k >= toIndex) {
            throw new IndexOutOfBoundsException("Target rank k=" + k + " out of range [" + fromIndex + ", " + toIndex + ")");
        }

        int maxDepth = 2 * (31 - Integer.numberOfLeadingZeros(toIndex - fromIndex));
        introselect(a, fromIndex, toIndex - 1, k, maxDepth);
        return a[k];
    }

    private static void introselect(double[] a, int left, int right, int k, int maxDepth) {
        while (right > left) {
            if (right - left <= INSERTION_SORT_THRESHOLD) {
                insertionSort(a, left, right);
                return;
            }
            if (maxDepth == 0) {
                Arrays.sort(a, left, right + 1);
                return;
            }
            maxDepth--;

            // Median-of-three pivot selection
            int mid = left + ((right - left) >>> 1);
            if (Double.compare(a[mid], a[left]) < 0) swap(a, left, mid);
            if (Double.compare(a[right], a[left]) < 0) swap(a, left, right);
            if (Double.compare(a[right], a[mid]) < 0) swap(a, mid, right);
            double pivot = a[mid];

            // 3-way Dutch National Flag partitioning
            int i = left;
            int j = left;
            int p = right;
            while (j <= p) {
                int cmp = Double.compare(a[j], pivot);
                if (cmp < 0) {
                    swap(a, i, j);
                    i++;
                    j++;
                } else if (cmp > 0) {
                    swap(a, j, p);
                    p--;
                } else {
                    j++;
                }
            }

            // [left .. i-1] are < pivot
            // [i .. p] are == pivot
            // [p+1 .. right] are > pivot
            if (k < i) {
                right = i - 1;
            } else if (k > p) {
                left = p + 1;
            } else {
                return; // k is within [i, p], already equal to pivot
            }
        }
    }

    private static void insertionSort(double[] a, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            double key = a[i];
            int j = i - 1;
            while (j >= left && Double.compare(a[j], key) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    private static void swap(double[] a, int i, int j) {
        double tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    /**
     * Calculates an arbitrary quantile q &isin; [0.0, 1.0] using linear interpolation
     * (matching pandas / numpy default) in linear O(n) time.
     * Note: this operation partially rearranges elements in {@code a[fromIndex..toIndex-1]}.
     *
     * @param a array containing data values
     * @param fromIndex start index (inclusive)
     * @param toIndex end index (exclusive)
     * @param q quantile fraction between 0.0 and 1.0
     * @return the interpolated quantile value, or {@link Double#NaN} if range is empty
     */
    public static double quantile(double[] a, int fromIndex, int toIndex, double q) {
        if (q < 0.0 || q > 1.0 || Double.isNaN(q)) {
            throw new IllegalArgumentException("Quantile q must be between 0.0 and 1.0, got: " + q);
        }
        int n = toIndex - fromIndex;
        if (n == 0) {
            return Double.NaN;
        }
        if (n == 1) {
            return a[fromIndex];
        }
        if (q == 0.0) {
            double min = a[fromIndex];
            for (int i = fromIndex + 1; i < toIndex; i++) {
                if (Double.compare(a[i], min) < 0) min = a[i];
            }
            return min;
        }
        if (q == 1.0) {
            double max = a[fromIndex];
            for (int i = fromIndex + 1; i < toIndex; i++) {
                if (Double.compare(a[i], max) > 0) max = a[i];
            }
            return max;
        }

        double pos = q * (n - 1);
        int i = (int) Math.floor(pos);
        double f = pos - i;
        int k = fromIndex + i;

        select(a, fromIndex, toIndex, k);
        double lower = a[k];
        if (f == 0.0 || k == toIndex - 1) {
            return lower;
        }

        // Find the minimum value in the right partition [k+1, toIndex) in linear O(n) time
        double upper = a[k + 1];
        for (int idx = k + 2; idx < toIndex; idx++) {
            if (Double.compare(a[idx], upper) < 0) {
                upper = a[idx];
            }
        }
        return lower + f * (upper - lower);
    }
}
