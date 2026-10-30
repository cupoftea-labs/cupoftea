package com.cupoftea.table.vector;

import java.util.BitSet;

import com.cupoftea.table.Categorical;

/**
 * Alias subclass of {@link DictionaryColumnVector} for Pandas-style Categorical column nomenclature.
 */
public class CategoricalColumnVector extends DictionaryColumnVector {

    private static final long serialVersionUID = 1L;

    public CategoricalColumnVector(String name) {
        super(name);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, String... values) {
        super(name, Math.max(DEFAULT_INITIAL_CAPACITY, values != null ? values.length : 0), true, false);
        this.type = Categorical.class;
        if (values != null) {
            for (String v : values) {
                appendString(v);
            }
        }
    }

    public CategoricalColumnVector(String name, int initialCapacity, boolean nullable) {
        super(name, initialCapacity, nullable);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, int initialCapacity, boolean nullable, boolean useShortCodes) {
        super(name, initialCapacity, nullable, useShortCodes);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, String[] dictionary, int[] codes, BitSet nulls, int size) {
        super(name, dictionary, codes, nulls, size);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, boolean nullable, String[] dictionary, int[] codes, BitSet nulls, int size) {
        super(name, nullable, dictionary, codes, nulls, size);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, String[] dictionary, short[] shortCodes, BitSet nulls, int size) {
        super(name, dictionary, shortCodes, nulls, size);
        this.type = Categorical.class;
    }

    public CategoricalColumnVector(String name, boolean nullable, String[] dictionary, short[] shortCodes, BitSet nulls, int size) {
        super(name, nullable, dictionary, shortCodes, nulls, size);
        this.type = Categorical.class;
    }
}
