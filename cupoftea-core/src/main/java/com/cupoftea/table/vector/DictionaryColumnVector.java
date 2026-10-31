package com.cupoftea.table.vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import com.cupoftea.table.NA;

/**
 * Dictionary / Categorical encoded columnar vector for repeated string values.
 * <p>
 * Stores unique strings once in a contiguous {@code String[]} lookup dictionary and represents
 * column entries as primitive integer codes ({@code int[]} or {@code short[]}).
 * <p>
 * Accelerates grouping, hash joins, sorting, and filtering operations by comparing and indexing
 * primitive integer codes instead of performing full string equality and string hashing per row.
 */
public class DictionaryColumnVector extends ColumnVector {

    private static final long serialVersionUID = 1L;

    public static final int NULL_CODE = -1;
    public static final short NULL_SHORT_CODE = -1;

    private String[] dictionary;
    private int dictionarySize;
    private final Map<String, Integer> dictionaryIndex;

    private int[] codes;
    private short[] shortCodes;
    private boolean shortStorage;

    private transient int[] categoryRanks;

    public DictionaryColumnVector(String name) {
        this(name, DEFAULT_INITIAL_CAPACITY, true, false);
    }

    public DictionaryColumnVector(String name, int initialCapacity, boolean nullable) {
        this(name, initialCapacity, nullable, false);
    }

    public DictionaryColumnVector(String name, int initialCapacity, boolean nullable, boolean useShortCodes) {
        super(name, String.class, nullable, initialCapacity);
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity);
        this.dictionary = new String[DEFAULT_INITIAL_CAPACITY];
        this.dictionarySize = 0;
        this.dictionaryIndex = new HashMap<>(DEFAULT_INITIAL_CAPACITY * 2);
        this.shortStorage = useShortCodes;
        if (useShortCodes) {
            this.shortCodes = new short[cap];
            Arrays.fill(this.shortCodes, NULL_SHORT_CODE);
        } else {
            this.codes = new int[cap];
            Arrays.fill(this.codes, NULL_CODE);
        }
    }

    public DictionaryColumnVector(String name, String[] dictionary, int[] codes, BitSet nulls, int size) {
        this(name, true, dictionary, codes, nulls, size);
    }

    public DictionaryColumnVector(String name, boolean nullable, String[] dictionary, int[] codes, BitSet nulls, int size) {
        super(name, String.class, nullable, codes != null ? codes.length : DEFAULT_INITIAL_CAPACITY);
        Objects.requireNonNull(dictionary, "Dictionary lookup array cannot be null");
        Objects.requireNonNull(codes, "Codes array cannot be null");
        this.dictionary = Arrays.copyOf(dictionary, Math.max(DEFAULT_INITIAL_CAPACITY, dictionary.length));
        this.dictionarySize = dictionary.length;
        this.dictionaryIndex = new HashMap<>(Math.max(16, dictionary.length * 2));
        for (int i = 0; i < dictionary.length; i++) {
            if (dictionary[i] != null) {
                this.dictionaryIndex.put(dictionary[i], i);
            }
        }
        this.shortStorage = false;
        this.codes = codes;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    public DictionaryColumnVector(String name, String[] dictionary, short[] shortCodes, BitSet nulls, int size) {
        this(name, true, dictionary, shortCodes, nulls, size);
    }

    public DictionaryColumnVector(String name, boolean nullable, String[] dictionary, short[] shortCodes, BitSet nulls, int size) {
        super(name, String.class, nullable, shortCodes != null ? shortCodes.length : DEFAULT_INITIAL_CAPACITY);
        Objects.requireNonNull(dictionary, "Dictionary lookup array cannot be null");
        Objects.requireNonNull(shortCodes, "Short codes array cannot be null");
        if (dictionary.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Dictionary size (" + dictionary.length + ") exceeds Short.MAX_VALUE (" + Short.MAX_VALUE + ")"
            );
        }
        this.dictionary = Arrays.copyOf(dictionary, Math.max(DEFAULT_INITIAL_CAPACITY, dictionary.length));
        this.dictionarySize = dictionary.length;
        this.dictionaryIndex = new HashMap<>(Math.max(16, dictionary.length * 2));
        for (int i = 0; i < dictionary.length; i++) {
            if (dictionary[i] != null) {
                this.dictionaryIndex.put(dictionary[i], i);
            }
        }
        this.shortStorage = true;
        this.shortCodes = shortCodes;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
    }

    /**
     * Package-private constructor that shares or clones an existing dictionary structure.
     */
    DictionaryColumnVector(
            String name,
            boolean nullable,
            String[] dictionary,
            int dictionarySize,
            Map<String, Integer> dictionaryIndex,
            int[] codes,
            short[] shortCodes,
            boolean shortStorage,
            BitSet nulls,
            int size,
            int[] categoryRanks) {
        super(name, String.class, nullable, Math.max(DEFAULT_INITIAL_CAPACITY, size));
        this.dictionary = Arrays.copyOf(dictionary, Math.max(DEFAULT_INITIAL_CAPACITY, dictionarySize));
        this.dictionarySize = dictionarySize;
        this.dictionaryIndex = new HashMap<>(dictionaryIndex);
        this.codes = codes;
        this.shortCodes = shortCodes;
        this.shortStorage = shortStorage;
        if (nulls != null) {
            this.nulls.or(nulls);
        }
        this.size = size;
        this.categoryRanks = categoryRanks != null ? Arrays.copyOf(categoryRanks, categoryRanks.length) : null;
    }

    // ==========================================
    // Static Factory Methods
    // ==========================================

    /**
     * Creates a {@link DictionaryColumnVector} from an array of strings.
     */
    public static DictionaryColumnVector from(String name, String... values) {
        return from(name, false, values);
    }

    /**
     * Alias for {@link #from(String, String...)}.
     */
    public static DictionaryColumnVector fromStrings(String name, String... values) {
        return from(name, false, values);
    }

    /**
     * Alias for {@link #from(String, boolean, String...)}.
     */
    public static DictionaryColumnVector fromStrings(String name, String[] values, boolean useShortCodes) {
        return from(name, useShortCodes, values);
    }

    /**
     * Creates a {@link DictionaryColumnVector} from an array of strings, optionally using {@code short[]} codes.
     */
    public static DictionaryColumnVector from(String name, boolean useShortCodes, String... values) {
        int len = values != null ? values.length : 0;
        DictionaryColumnVector vec = new DictionaryColumnVector(name, Math.max(16, len), true, useShortCodes);
        if (values != null) {
            for (String v : values) {
                vec.appendString(v);
            }
        }
        return vec;
    }

    /**
     * Creates a {@link DictionaryColumnVector} from a collection of strings.
     */
    public static DictionaryColumnVector from(String name, Collection<String> values) {
        return from(name, false, values);
    }

    /**
     * Creates a {@link DictionaryColumnVector} from a collection of strings, optionally using {@code short[]} codes.
     */
    public static DictionaryColumnVector from(String name, boolean useShortCodes, Collection<String> values) {
        int len = values != null ? values.size() : 0;
        DictionaryColumnVector vec = new DictionaryColumnVector(name, Math.max(16, len), true, useShortCodes);
        if (values != null) {
            for (String v : values) {
                vec.appendString(v);
            }
        }
        return vec;
    }

    /**
     * Encodes an existing {@link StringColumnVector} into a {@link DictionaryColumnVector}.
     */
    public static DictionaryColumnVector fromStringVector(StringColumnVector src) {
        return fromStringVector(src, false);
    }

    /**
     * Encodes an existing {@link StringColumnVector} into a {@link DictionaryColumnVector}.
     */
    public static DictionaryColumnVector fromStringVector(StringColumnVector src, boolean useShortCodes) {
        Objects.requireNonNull(src, "Source StringColumnVector cannot be null");
        int n = src.size();
        DictionaryColumnVector dictVec = new DictionaryColumnVector(src.getName(), Math.max(16, n), src.isNullable(), useShortCodes);
        boolean anyNulls = src.hasNulls();
        BitSet srcNulls = src.getNulls();

        byte[] values = src.getValues();
        int[] offsets = src.getOffsets();

        // Optimized open-addressing byte slice map for zero-allocation dictionary encoding
        int cap = 1024;
        int mask = cap - 1;
        int[] tableHash = new int[cap];
        int[] tableRow = new int[cap];
        int[] tableCode = new int[cap];
        Arrays.fill(tableRow, -1);
        int tableSize = 0;
        int threshold = (int) (cap * 0.70f);

        for (int i = 0; i < n; i++) {
            if (anyNulls && srcNulls.get(i)) {
                dictVec.appendNull();
                continue;
            }
            int start = offsets[i];
            int len = offsets[i + 1] - start;
            if (len == 0) {
                dictVec.appendString("");
                continue;
            }

            int h = StringColumnVector.hashBytes(values, start, len);

            int pos = (h ^ (h >>> 16)) & mask;
            int foundCode = -1;
            while (true) {
                int repRow = tableRow[pos];
                if (repRow == -1) {
                    // Not found, insert new entry
                    String val = new String(values, start, len, java.nio.charset.StandardCharsets.UTF_8);
                    int code = dictVec.getOrInsertCode(val);
                    tableRow[pos] = i;
                    tableHash[pos] = h;
                    tableCode[pos] = code;
                    tableSize++;
                    dictVec.setCodeInternal(i, code);
                    dictVec.nulls.clear(i);
                    dictVec.size++;

                    if (tableSize >= threshold) {
                        int newCap = cap << 1;
                        int newMask = newCap - 1;
                        int[] newTableHash = new int[newCap];
                        int[] newTableRow = new int[newCap];
                        int[] newTableCode = new int[newCap];
                        Arrays.fill(newTableRow, -1);
                        for (int t = 0; t < cap; t++) {
                            if (tableRow[t] != -1) {
                                int nh = tableHash[t];
                                int npos = (nh ^ (nh >>> 16)) & newMask;
                                while (newTableRow[npos] != -1) {
                                    npos = (npos + 1) & newMask;
                                }
                                newTableRow[npos] = tableRow[t];
                                newTableHash[npos] = nh;
                                newTableCode[npos] = tableCode[t];
                            }
                        }
                        cap = newCap;
                        mask = newMask;
                        tableHash = newTableHash;
                        tableRow = newTableRow;
                        tableCode = newTableCode;
                        threshold = (int) (newCap * 0.70f);
                    }
                    break;
                }
                if (tableHash[pos] == h) {
                    int repStart = offsets[repRow];
                    int repLen = offsets[repRow + 1] - repStart;
                    if (len == repLen && Arrays.equals(values, start, start + len, values, repStart, repStart + repLen)) {
                        foundCode = tableCode[pos];
                        dictVec.setCodeInternal(i, foundCode);
                        dictVec.nulls.clear(i);
                        dictVec.size++;
                        break;
                    }
                }
                pos = (pos + 1) & mask;
            }
        }
        return dictVec;
    }

    /**
     * Converts any {@link ColumnVector} into a {@link DictionaryColumnVector}.
     */
    public static DictionaryColumnVector fromVector(ColumnVector src) {
        return fromVector(src, false);
    }

    /**
     * Converts any {@link ColumnVector} into a {@link DictionaryColumnVector}.
     */
    public static DictionaryColumnVector fromVector(ColumnVector src, boolean useShortCodes) {
        Objects.requireNonNull(src, "Source ColumnVector cannot be null");
        if (src instanceof DictionaryColumnVector dictSrc) {
            DictionaryColumnVector copy = (DictionaryColumnVector) dictSrc.copy();
            if (useShortCodes && !copy.shortStorage && copy.dictionarySize <= Short.MAX_VALUE) {
                copy.compactToShortCodes();
            } else if (!useShortCodes && copy.shortStorage) {
                copy.toIntCodes();
            }
            return copy;
        }
        if (src instanceof StringColumnVector strSrc) {
            return fromStringVector(strSrc, useShortCodes);
        }
        int n = src.size();
        DictionaryColumnVector dictVec = new DictionaryColumnVector(src.getName(), Math.max(16, n), src.isNullable(), useShortCodes);
        for (int i = 0; i < n; i++) {
            if (src.isNull(i)) {
                dictVec.appendNull();
            } else {
                Object val = src.getObject(i);
                if (val == null || NA.isNA(val)) {
                    dictVec.appendNull();
                } else {
                    dictVec.appendString(val.toString());
                }
            }
        }
        return dictVec;
    }

    // ==========================================
    // Dictionary & Code Accessors
    // ==========================================

    @Override
    public boolean isDictionaryEncoded() {
        return true;
    }

    @Override
    public DictionaryColumnVector toDictionary() {
        return this;
    }

    @Override
    public DictionaryColumnVector toDictionary(boolean useShortCodes) {
        if (useShortCodes && !this.shortStorage && this.dictionarySize <= Short.MAX_VALUE) {
            return compactToShortCodes();
        } else if (!useShortCodes && this.shortStorage) {
            return toIntCodes();
        }
        return this;
    }

    @Override
    public DictionaryColumnVector toCategorical() {
        return this;
    }

    /**
     * Returns the number of unique strings currently stored in the lookup dictionary.
     */
    public int getDictionarySize() {
        return dictionarySize;
    }

    /**
     * Alias for {@link #getDictionarySize()}.
     */
    public int getCardinality() {
        return dictionarySize;
    }

    /**
     * Alias for {@link #getDictionarySize()}.
     */
    public int categoryCount() {
        return dictionarySize;
    }

    /**
     * Returns a copy of the lookup dictionary containing the unique strings {@code [0, dictionarySize)}.
     */
    public String[] getDictionary() {
        return Arrays.copyOf(dictionary, dictionarySize);
    }

    /**
     * Returns the internal lookup array directly (entries valid up to {@link #getDictionarySize()}).
     */
    public String[] getRawDictionary() {
        return dictionary;
    }

    /**
     * Returns an unmodifiable list of the unique category strings in dictionary code order.
     */
    public List<String> getCategories() {
        return Collections.unmodifiableList(Arrays.asList(getDictionary()));
    }

    /**
     * Returns {@code true} if column entries are currently stored as primitive {@code short[]} codes.
     */
    public boolean isShortEncoded() {
        return shortStorage;
    }

    /**
     * Alias for {@link #isShortEncoded()}.
     */
    public boolean usesShortCodes() {
        return shortStorage;
    }

    /**
     * Alias for {@link #isShortEncoded()}.
     */
    public boolean isShortCodeStorage() {
        return shortStorage;
    }

    /**
     * Returns {@code true} if the given category string is present in the dictionary.
     */
    public boolean containsCategory(String value) {
        return value != null && dictionaryIndex.containsKey(value);
    }

    /**
     * Looks up the integer dictionary code for the given string, or {@code -1} if not present in the dictionary.
     */
    public int encode(String value) {
        if (value == null) {
            return NULL_CODE;
        }
        Integer code = dictionaryIndex.get(value);
        return code != null ? code : NULL_CODE;
    }

    /**
     * Alias for {@link #encode(String)}.
     */
    public int getCodeForValue(String value) {
        return encode(value);
    }

    /**
     * Alias for {@link #encode(String)}.
     */
    public int indexOfCategory(String value) {
        return encode(value);
    }

    /**
     * Returns the existing dictionary code for {@code value}, or inserts {@code value} into the dictionary
     * and returns the newly assigned code.
     */
    public int getOrInsertCode(String value) {
        if (value == null) {
            return NULL_CODE;
        }
        Integer existing = dictionaryIndex.get(value);
        if (existing != null) {
            return existing;
        }
        int newCode = dictionarySize;
        if (newCode >= dictionary.length) {
            dictionary = Arrays.copyOf(dictionary, Math.max(DEFAULT_INITIAL_CAPACITY, dictionary.length * 2));
        }
        dictionary[newCode] = value;
        dictionarySize++;
        dictionaryIndex.put(value, newCode);
        categoryRanks = null;

        if (shortStorage && newCode > Short.MAX_VALUE) {
            promoteShortCodesToInt();
        }
        return newCode;
    }

    /**
     * Decodes a dictionary code back into its {@link String} value, or {@code null} if {@code code < 0}.
     */
    public String decode(int code) {
        if (code < 0) {
            return null;
        }
        if (code >= dictionarySize) {
            throw new IndexOutOfBoundsException(
                "Dictionary code " + code + " out of bounds for dictionary size " + dictionarySize
            );
        }
        return dictionary[code];
    }

    /**
     * Alias for {@link #decode(int)}.
     */
    public String getCategory(int code) {
        return decode(code);
    }

    /**
     * Returns the integer dictionary code at {@code row}, or {@code -1} if the cell is null/NA.
     */
    public int getCode(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return NULL_CODE;
        }
        return shortStorage ? shortCodes[row] : codes[row];
    }

    /**
     * Unchecked internal helper returning code at {@code row} without bounds or null BitSet check.
     */
    public int getCodeUnchecked(int row) {
        return shortStorage ? shortCodes[row] : codes[row];
    }

    /**
     * Returns the {@code short} dictionary code at {@code row}, or {@code -1} if the cell is null/NA.
     */
    public short getShortCode(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return NULL_SHORT_CODE;
        }
        if (shortStorage) {
            return shortCodes[row];
        }
        int c = codes[row];
        if (c > Short.MAX_VALUE) {
            throw new IllegalStateException("Code " + c + " at row " + row + " exceeds Short.MAX_VALUE");
        }
        return (short) c;
    }

    /**
     * Sets the dictionary code directly at {@code row}. A negative code marks the row as null.
     */
    public void setCode(int row, int code) {
        checkRowBounds(row);
        if (code < 0) {
            setCodeInternal(row, NULL_CODE);
            nulls.set(row);
        } else {
            if (code >= dictionarySize) {
                throw new IndexOutOfBoundsException(
                    "Dictionary code " + code + " out of bounds for dictionary size " + dictionarySize
                );
            }
            setCodeInternal(row, code);
            nulls.clear(row);
        }
    }

    /**
     * Appends a dictionary code directly. A negative code appends a null cell.
     */
    public void appendCode(int code) {
        if (code < 0) {
            appendNull();
        } else {
            if (code >= dictionarySize) {
                throw new IndexOutOfBoundsException(
                    "Dictionary code " + code + " out of bounds for dictionary size " + dictionarySize
                );
            }
            ensureCapacity(size + 1);
            setCodeInternal(size, code);
            nulls.clear(size);
            size++;
        }
    }

    /**
     * Returns a copy of the column's integer codes of length {@code size()} (with {@code -1} for null cells).
     */
    public int[] getCodes() {
        int[] result = new int[size];
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            for (int i = 0; i < size; i++) {
                result[i] = (anyNulls && nulls.get(i)) ? NULL_CODE : shortCodes[i];
            }
        } else {
            System.arraycopy(codes, 0, result, 0, size);
            if (anyNulls) {
                for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
                    result[i] = NULL_CODE;
                }
            }
        }
        return result;
    }

    /**
     * Alias for {@link #getCodes()}.
     */
    public int[] getIntCodes() {
        return getCodes();
    }

    /**
     * Alias for {@link #getCodes()}.
     */
    public int[] toCodeArray() {
        return getCodes();
    }

    /**
     * Returns the backing {@code int[]} codes array (if in {@code int[]} mode) or a materialized {@code int[]} copy.
     */
    public int[] getRawCodes() {
        if (!shortStorage) {
            return codes;
        }
        return getCodes();
    }

    /**
     * Returns a copy of the column's {@code short[]} codes of length {@code size()} (with {@code -1} for null cells).
     *
     * @throws IllegalStateException if dictionary size exceeds {@link Short#MAX_VALUE}
     */
    public short[] getShortCodes() {
        if (dictionarySize > Short.MAX_VALUE) {
            throw new IllegalStateException(
                "Cannot represent codes as short[] when dictionary size (" + dictionarySize + ") > Short.MAX_VALUE"
            );
        }
        short[] result = new short[size];
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            System.arraycopy(shortCodes, 0, result, 0, size);
            if (anyNulls) {
                for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
                    result[i] = NULL_SHORT_CODE;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                result[i] = (anyNulls && nulls.get(i)) ? NULL_SHORT_CODE : (short) codes[i];
            }
        }
        return result;
    }

    /**
     * Alias for {@link #getShortCodes()}.
     */
    public short[] toShortCodeArray() {
        return getShortCodes();
    }

    /**
     * Returns the backing {@code short[]} codes array if in short mode, or converts to {@code short[]} if cardinality fits.
     */
    public short[] getRawShortCodes() {
        if (shortStorage) {
            return shortCodes;
        }
        return getShortCodes();
    }

    /**
     * Compacts internal code storage from {@code int[]} to {@code short[]} in-place if {@code dictionarySize <= Short.MAX_VALUE}.
     */
    public DictionaryColumnVector compactToShortCodes() {
        if (shortStorage) {
            return this;
        }
        if (dictionarySize > Short.MAX_VALUE) {
            throw new IllegalStateException(
                "Cannot compact to short[] codes: dictionary size (" + dictionarySize + ") exceeds Short.MAX_VALUE"
            );
        }
        short[] newShortCodes = new short[Math.max(DEFAULT_INITIAL_CAPACITY, size)];
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            newShortCodes[i] = (anyNulls && nulls.get(i)) ? NULL_SHORT_CODE : (short) codes[i];
        }
        this.shortCodes = newShortCodes;
        this.codes = null;
        this.shortStorage = true;
        return this;
    }

    /**
     * Alias for {@link #compactToShortCodes()}.
     */
    public DictionaryColumnVector toShortCodes() {
        return compactToShortCodes();
    }

    /**
     * Converts internal code storage from {@code short[]} to {@code int[]} in-place.
     */
    public DictionaryColumnVector toIntCodes() {
        if (!shortStorage) {
            return this;
        }
        promoteShortCodesToInt();
        return this;
    }

    /**
     * Alias for {@link #toIntCodes()}.
     */
    public DictionaryColumnVector expandToIntCodes() {
        return toIntCodes();
    }

    /**
     * Prunes any unused dictionary entries (e.g., after filtering or slicing) and remaps codes contiguously to {@code [0, newSize)}.
     */
    public DictionaryColumnVector compactDictionary() {
        if (dictionarySize == 0 || size == 0) {
            Arrays.fill(dictionary, 0, dictionarySize, null);
            dictionarySize = 0;
            dictionaryIndex.clear();
            categoryRanks = null;
            return this;
        }

        BitSet usedCodes = new BitSet(dictionarySize);
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int c = getCodeUnchecked(i);
                if (c >= 0) {
                    usedCodes.set(c);
                }
            }
        }

        int newDictSize = usedCodes.cardinality();
        if (newDictSize == dictionarySize) {
            return this;
        }

        String[] newDict = new String[Math.max(DEFAULT_INITIAL_CAPACITY, newDictSize)];
        int[] oldToNew = new int[dictionarySize];
        Arrays.fill(oldToNew, NULL_CODE);
        dictionaryIndex.clear();

        int nextCode = 0;
        for (int oldCode = usedCodes.nextSetBit(0); oldCode >= 0; oldCode = usedCodes.nextSetBit(oldCode + 1)) {
            String val = dictionary[oldCode];
            newDict[nextCode] = val;
            oldToNew[oldCode] = nextCode;
            dictionaryIndex.put(val, nextCode);
            nextCode++;
        }

        for (int i = 0; i < size; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int oldCode = getCodeUnchecked(i);
                if (oldCode >= 0) {
                    setCodeInternal(i, oldToNew[oldCode]);
                }
            }
        }

        this.dictionary = newDict;
        this.dictionarySize = newDictSize;
        this.categoryRanks = null;
        return this;
    }

    /**
     * Decodes this dictionary vector into a standard {@link StringColumnVector}.
     */
    public StringColumnVector toStringColumnVector() {
        String[] decoded = new String[Math.max(DEFAULT_INITIAL_CAPACITY, size)];
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            for (int i = 0; i < size; i++) {
                if (!anyNulls || !nulls.get(i)) {
                    short c = shortCodes[i];
                    if (c >= 0) {
                        decoded[i] = dictionary[c];
                    }
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (!anyNulls || !nulls.get(i)) {
                    int c = codes[i];
                    if (c >= 0) {
                        decoded[i] = dictionary[c];
                    }
                }
            }
        }
        BitSet copyNulls = new BitSet(size);
        copyNulls.or(nulls);
        return new StringColumnVector(name, nullable, decoded, copyNulls, size);
    }

    /**
     * Alias for {@link #toStringColumnVector()}.
     */
    public StringColumnVector decode() {
        return toStringColumnVector();
    }

    /**
     * Extracts decoded strings for rows {@code [0, size)}.
     */
    public String[] toStringArray() {
        String[] result = new String[size];
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int c = getCodeUnchecked(i);
                if (c >= 0) {
                    result[i] = dictionary[c];
                }
            }
        }
        return result;
    }

    // ==========================================
    // Internal Storage Management
    // ==========================================

    private void promoteShortCodesToInt() {
        int cap = shortCodes != null ? shortCodes.length : Math.max(DEFAULT_INITIAL_CAPACITY, size);
        int[] newCodes = new int[cap];
        Arrays.fill(newCodes, NULL_CODE);
        if (shortCodes != null) {
            for (int i = 0; i < size; i++) {
                newCodes[i] = shortCodes[i];
            }
        }
        this.codes = newCodes;
        this.shortCodes = null;
        this.shortStorage = false;
    }

    private void ensureCapacity(int minCapacity) {
        if (shortStorage) {
            if (minCapacity > shortCodes.length) {
                int oldLen = shortCodes.length;
                int newCap = Math.max(oldLen * 2, minCapacity);
                shortCodes = Arrays.copyOf(shortCodes, newCap);
                Arrays.fill(shortCodes, oldLen, newCap, NULL_SHORT_CODE);
            }
        } else {
            if (minCapacity > codes.length) {
                int oldLen = codes.length;
                int newCap = Math.max(oldLen * 2, minCapacity);
                codes = Arrays.copyOf(codes, newCap);
                Arrays.fill(codes, oldLen, newCap, NULL_CODE);
            }
        }
    }

    private void setCodeInternal(int row, int code) {
        if (shortStorage) {
            if (code > Short.MAX_VALUE) {
                promoteShortCodesToInt();
                codes[row] = code;
            } else {
                shortCodes[row] = (short) code;
            }
        } else {
            codes[row] = code;
        }
    }

    boolean sharesDictionaryStructure(DictionaryColumnVector other) {
        if (this.dictionarySize != other.dictionarySize) {
            return false;
        }
        if (this.dictionary == other.dictionary) {
            return true;
        }
        for (int i = 0; i < dictionarySize; i++) {
            if (!Objects.equals(this.dictionary[i], other.dictionary[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ColumnVector createEmptyWithSameEncoding(int initialCapacity) {
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, initialCapacity);
        int[] newCodes = shortStorage ? null : new int[cap];
        short[] newShortCodes = shortStorage ? new short[cap] : null;
        if (newCodes != null) {
            Arrays.fill(newCodes, NULL_CODE);
        }
        if (newShortCodes != null) {
            Arrays.fill(newShortCodes, NULL_SHORT_CODE);
        }
        return new DictionaryColumnVector(
            this.name,
            this.nullable,
            this.dictionary,
            this.dictionarySize,
            this.dictionaryIndex,
            newCodes,
            newShortCodes,
            this.shortStorage,
            null,
            0,
            this.categoryRanks
        );
    }

    // ==========================================
    // ColumnVector Core Contract Implementations
    // ==========================================

    @Override
    public void setSizeUnchecked(int newSize) {
        ensureCapacity(newSize);
        this.size = newSize;
    }

    @Override
    public String getString(int row) {
        checkRowBounds(row);
        if (nulls.get(row)) {
            return null;
        }
        int code = getCodeUnchecked(row);
        return code >= 0 ? dictionary[code] : null;
    }

    @Override
    public void setString(int row, String val) {
        checkRowBounds(row);
        if (val == null) {
            setCodeInternal(row, NULL_CODE);
            nulls.set(row);
        } else {
            int code = getOrInsertCode(val);
            setCodeInternal(row, code);
            nulls.clear(row);
        }
    }

    @Override
    public void appendString(String val) {
        ensureCapacity(size + 1);
        if (val == null) {
            setCodeInternal(size, NULL_CODE);
            nulls.set(size);
        } else {
            int code = getOrInsertCode(val);
            setCodeInternal(size, code);
            nulls.clear(size);
        }
        size++;
    }

    @Override
    public Object getObject(int row) {
        return getString(row);
    }

    @Override
    public void setObject(int row, Object value) {
        checkRowBounds(row);
        if (NA.isNA(value)) {
            setCodeInternal(row, NULL_CODE);
            nulls.set(row);
        } else {
            int code = getOrInsertCode(value.toString());
            setCodeInternal(row, code);
            nulls.clear(row);
        }
    }

    @Override
    protected void setObjectValueOnly(int row, Object value) {
        if (value != null && !NA.isNA(value)) {
            int code = encode(value.toString());
            if (code >= 0) {
                setCodeInternal(row, code);
            } else {
                synchronized (this) {
                    code = getOrInsertCode(value.toString());
                    setCodeInternal(row, code);
                }
            }
        }
    }

    @Override
    public void appendObject(Object value) {
        if (NA.isNA(value)) {
            appendNull();
        } else {
            appendString(value.toString());
        }
    }

    @Override
    public void appendNull() {
        ensureCapacity(size + 1);
        setCodeInternal(size, NULL_CODE);
        nulls.set(size);
        size++;
    }

    @Override
    public void remove(int row) {
        checkRowBounds(row);
        int numMoved = size - row - 1;
        if (shortStorage) {
            if (numMoved > 0) {
                System.arraycopy(shortCodes, row + 1, shortCodes, row, numMoved);
            }
            shortCodes[size - 1] = NULL_SHORT_CODE;
        } else {
            if (numMoved > 0) {
                System.arraycopy(codes, row + 1, codes, row, numMoved);
            }
            codes[size - 1] = NULL_CODE;
        }

        for (int i = row; i < size - 1; i++) {
            if (nulls.get(i + 1)) {
                nulls.set(i);
            } else {
                nulls.clear(i);
            }
        }
        nulls.clear(size - 1);
        size--;
    }

    @Override
    public DictionaryColumnVector copy() {
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, size);
        int[] copyCodes = shortStorage ? null : Arrays.copyOf(codes, cap);
        short[] copyShortCodes = shortStorage ? Arrays.copyOf(shortCodes, cap) : null;
        return new DictionaryColumnVector(
            name,
            nullable,
            dictionary,
            dictionarySize,
            dictionaryIndex,
            copyCodes,
            copyShortCodes,
            shortStorage,
            nulls,
            size,
            categoryRanks
        );
    }

    @Override
    public DictionaryColumnVector slice(int from, int to) {
        checkSliceBounds(from, to);
        int len = to - from;
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, len);
        int[] sliceCodes = null;
        short[] sliceShortCodes = null;
        if (shortStorage) {
            sliceShortCodes = new short[cap];
            Arrays.fill(sliceShortCodes, len, cap, NULL_SHORT_CODE);
            System.arraycopy(shortCodes, from, sliceShortCodes, 0, len);
        } else {
            sliceCodes = new int[cap];
            Arrays.fill(sliceCodes, len, cap, NULL_CODE);
            System.arraycopy(codes, from, sliceCodes, 0, len);
        }
        BitSet sliceNulls = new BitSet(len);
        if (hasNulls()) {
            for (int i = 0; i < len; i++) {
                if (nulls.get(from + i)) {
                    sliceNulls.set(i);
                }
            }
        }
        return new DictionaryColumnVector(
            name,
            nullable,
            dictionary,
            dictionarySize,
            dictionaryIndex,
            sliceCodes,
            sliceShortCodes,
            shortStorage,
            sliceNulls,
            len,
            categoryRanks
        );
    }

    @Override
    public void fillna(Object value) {
        if (NA.isNA(value) || !hasNulls()) {
            return;
        }
        int fillCode = getOrInsertCode(value.toString());
        if (shortStorage) {
            short sc = (short) fillCode;
            for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
                shortCodes[i] = sc;
            }
        } else {
            for (int i = nulls.nextSetBit(0); i >= 0 && i < size; i = nulls.nextSetBit(i + 1)) {
                codes[i] = fillCode;
            }
        }
        nulls.clear();
    }

    @Override
    public void permute(int[] order) {
        BitSet newNulls = new BitSet(size);
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            short[] newShortCodes = new short[Math.max(DEFAULT_INITIAL_CAPACITY, size)];
            for (int i = 0; i < size; i++) {
                int src = order[i];
                newShortCodes[i] = shortCodes[src];
                if (anyNulls && nulls.get(src)) {
                    newNulls.set(i);
                }
            }
            this.shortCodes = newShortCodes;
        } else {
            int[] newCodes = new int[Math.max(DEFAULT_INITIAL_CAPACITY, size)];
            for (int i = 0; i < size; i++) {
                int src = order[i];
                newCodes[i] = codes[src];
                if (anyNulls && nulls.get(src)) {
                    newNulls.set(i);
                }
            }
            this.codes = newCodes;
        }
        this.nulls.clear();
        this.nulls.or(newNulls);
    }

    @Override
    public DictionaryColumnVector filter(BitSet retainMask, int retainedCount) {
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, retainedCount);
        BitSet newNulls = new BitSet(retainedCount);
        boolean anyNulls = hasNulls();
        int dst = 0;
        if (shortStorage) {
            short[] newShortCodes = new short[cap];
            Arrays.fill(newShortCodes, retainedCount, cap, NULL_SHORT_CODE);
            for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
                newShortCodes[dst] = shortCodes[i];
                if (anyNulls && nulls.get(i)) {
                    newNulls.set(dst);
                }
                dst++;
            }
            return new DictionaryColumnVector(
                name, nullable, dictionary, dictionarySize, dictionaryIndex,
                null, newShortCodes, true, newNulls, retainedCount, categoryRanks
            );
        } else {
            int[] newCodes = new int[cap];
            Arrays.fill(newCodes, retainedCount, cap, NULL_CODE);
            for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
                newCodes[dst] = codes[i];
                if (anyNulls && nulls.get(i)) {
                    newNulls.set(dst);
                }
                dst++;
            }
            return new DictionaryColumnVector(
                name, nullable, dictionary, dictionarySize, dictionaryIndex,
                newCodes, null, false, newNulls, retainedCount, categoryRanks
            );
        }
    }

    @Override
    public void filterInPlace(BitSet retainMask, int retainedCount) {
        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, retainedCount);
        BitSet newNulls = new BitSet(retainedCount);
        boolean anyNulls = hasNulls();
        int dst = 0;
        if (shortStorage) {
            short[] newShortCodes = new short[cap];
            Arrays.fill(newShortCodes, retainedCount, cap, NULL_SHORT_CODE);
            for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
                newShortCodes[dst] = shortCodes[i];
                if (anyNulls && nulls.get(i)) {
                    newNulls.set(dst);
                }
                dst++;
            }
            this.shortCodes = newShortCodes;
        } else {
            int[] newCodes = new int[cap];
            Arrays.fill(newCodes, retainedCount, cap, NULL_CODE);
            for (int i = retainMask.nextSetBit(0); i >= 0 && i < size; i = retainMask.nextSetBit(i + 1)) {
                newCodes[dst] = codes[i];
                if (anyNulls && nulls.get(i)) {
                    newNulls.set(dst);
                }
                dst++;
            }
            this.codes = newCodes;
        }
        this.nulls.clear();
        this.nulls.or(newNulls);
        this.size = retainedCount;
    }

    @Override
    public void copyIndexedValuesOnly(ColumnVector src, int[] srcIndices, int count, int dstOffset) {
        if (src instanceof DictionaryColumnVector dictSrc && sharesDictionaryStructure(dictSrc)) {
            if (this.shortStorage && dictSrc.shortStorage) {
                short[] srcArr = dictSrc.shortCodes;
                short[] dstArr = this.shortCodes;
                for (int i = 0; i < count; i++) {
                    dstArr[dstOffset + i] = srcArr[srcIndices[i]];
                }
                return;
            } else if (!this.shortStorage && !dictSrc.shortStorage) {
                int[] srcArr = dictSrc.codes;
                int[] dstArr = this.codes;
                for (int i = 0; i < count; i++) {
                    dstArr[dstOffset + i] = srcArr[srcIndices[i]];
                }
                return;
            } else if (!this.shortStorage && dictSrc.shortStorage) {
                short[] srcArr = dictSrc.shortCodes;
                int[] dstArr = this.codes;
                for (int i = 0; i < count; i++) {
                    dstArr[dstOffset + i] = srcArr[srcIndices[i]];
                }
                return;
            }
        }
        super.copyIndexedValuesOnly(src, srcIndices, count, dstOffset);
    }

    // ==========================================
    // Code-Accelerated Comparisons & Min / Max
    // ==========================================

    /**
     * Computes (and caches) the lexicographical rank {@code [0, dictionarySize)} for each dictionary code.
     * Comparing any two dictionary codes {@code codeA} and {@code codeB} then reduces to
     * {@code Integer.compare(ranks[codeA], ranks[codeB])}.
     */
    public int[] ensureCategoryRanks() {
        int[] ranks = this.categoryRanks;
        if (ranks != null && ranks.length == dictionarySize) {
            return ranks;
        }
        if (dictionarySize == 0) {
            ranks = new int[0];
            this.categoryRanks = ranks;
            return ranks;
        }
        Integer[] order = new Integer[dictionarySize];
        for (int i = 0; i < dictionarySize; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> dictionary[a].compareTo(dictionary[b]));
        ranks = new int[dictionarySize];
        for (int r = 0; r < dictionarySize; r++) {
            ranks[order[r]] = r;
        }
        this.categoryRanks = ranks;
        return ranks;
    }

    @Override
    public int compareRows(int rowA, int rowB) {
        boolean nullA = nulls.get(rowA);
        boolean nullB = nulls.get(rowB);
        if (nullA && nullB) return 0;
        if (nullA) return 1;
        if (nullB) return -1;
        int codeA = getCodeUnchecked(rowA);
        int codeB = getCodeUnchecked(rowB);
        if (codeA == codeB) {
            return 0;
        }
        int[] ranks = ensureCategoryRanks();
        return Integer.compare(ranks[codeA], ranks[codeB]);
    }

    @Override
    public Object min(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to || dictionarySize == 0) {
            return null;
        }
        int[] ranks = ensureCategoryRanks();
        int bestCode = NULL_CODE;
        int bestRank = Integer.MAX_VALUE;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int code = getCodeUnchecked(i);
                if (code >= 0) {
                    int r = ranks[code];
                    if (r < bestRank) {
                        bestRank = r;
                        bestCode = code;
                        if (bestRank == 0) {
                            break;
                        }
                    }
                }
            }
        }
        return bestCode >= 0 ? dictionary[bestCode] : null;
    }

    @Override
    public Object min(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return min(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> min(start, end)
        );
        String best = null;
        for (Object p : partials) {
            if (p != null) {
                String v = (String) p;
                if (best == null || v.compareTo(best) < 0) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object minIndexed(int[] indices, int count) {
        if (count <= 0 || dictionarySize == 0) {
            return null;
        }
        int[] ranks = ensureCategoryRanks();
        int bestCode = NULL_CODE;
        int bestRank = Integer.MAX_VALUE;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                int code = getCodeUnchecked(row);
                if (code >= 0) {
                    int r = ranks[code];
                    if (r < bestRank) {
                        bestRank = r;
                        bestCode = code;
                        if (bestRank == 0) {
                            break;
                        }
                    }
                }
            }
        }
        return bestCode >= 0 ? dictionary[bestCode] : null;
    }

    @Override
    public Object max(int from, int to) {
        checkSliceBounds(from, to);
        if (from == to || dictionarySize == 0) {
            return null;
        }
        int[] ranks = ensureCategoryRanks();
        int maxRankTarget = dictionarySize - 1;
        int bestCode = NULL_CODE;
        int bestRank = -1;
        boolean anyNulls = hasNulls();
        for (int i = from; i < to; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int code = getCodeUnchecked(i);
                if (code >= 0) {
                    int r = ranks[code];
                    if (r > bestRank) {
                        bestRank = r;
                        bestCode = code;
                        if (bestRank == maxRankTarget) {
                            break;
                        }
                    }
                }
            }
        }
        return bestCode >= 0 ? dictionary[bestCode] : null;
    }

    @Override
    public Object max(ExecutorService executor, int parallelThreshold) {
        if (size < parallelThreshold || size <= 1) {
            return max(0, size);
        }
        List<Object> partials = ParallelVectorEngine.executeChunks(
            size, executor, (chunkIdx, start, end) -> max(start, end)
        );
        String best = null;
        for (Object p : partials) {
            if (p != null) {
                String v = (String) p;
                if (best == null || v.compareTo(best) > 0) {
                    best = v;
                }
            }
        }
        return best;
    }

    @Override
    public Object maxIndexed(int[] indices, int count) {
        if (count <= 0 || dictionarySize == 0) {
            return null;
        }
        int[] ranks = ensureCategoryRanks();
        int maxRankTarget = dictionarySize - 1;
        int bestCode = NULL_CODE;
        int bestRank = -1;
        boolean anyNulls = hasNulls();
        for (int i = 0; i < count; i++) {
            int row = indices[i];
            if (!anyNulls || !nulls.get(row)) {
                int code = getCodeUnchecked(row);
                if (code >= 0) {
                    int r = ranks[code];
                    if (r > bestRank) {
                        bestRank = r;
                        bestCode = code;
                        if (bestRank == maxRankTarget) {
                            break;
                        }
                    }
                }
            }
        }
        return bestCode >= 0 ? dictionary[bestCode] : null;
    }

    @Override
    public void clear() {
        this.size = 0;
        this.nulls.clear();
        Arrays.fill(dictionary, 0, dictionarySize, null);
        this.dictionarySize = 0;
        this.dictionaryIndex.clear();
        this.categoryRanks = null;
        if (shortStorage) {
            Arrays.fill(shortCodes, NULL_SHORT_CODE);
        } else {
            Arrays.fill(codes, NULL_CODE);
        }
    }

    // ==========================================
    // Code-Accelerated Filtering & Predicates
    // ==========================================

    /**
     * Evaluates equality against {@code targetValue} by looking up its integer dictionary code once
     * and comparing primitive codes across rows.
     */
    public BooleanColumnVector eq(String targetValue) {
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        int targetCode = encode(targetValue);
        boolean anyNulls = hasNulls();
        if (targetValue == null || targetCode < 0) {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(false);
                }
            }
            return res;
        }
        if (shortStorage) {
            short tc = (short) targetCode;
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(shortCodes[i] == tc);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(codes[i] == targetCode);
                }
            }
        }
        return res;
    }

    /**
     * Alias for {@link #eq(String)}.
     */
    public BooleanColumnVector equalTo(String targetValue) {
        return eq(targetValue);
    }

    /**
     * Evaluates inequality against {@code targetValue} using primitive integer code comparison.
     */
    public BooleanColumnVector notEq(String targetValue) {
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        int targetCode = encode(targetValue);
        boolean anyNulls = hasNulls();
        if (targetValue == null || targetCode < 0) {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(true);
                }
            }
            return res;
        }
        if (shortStorage) {
            short tc = (short) targetCode;
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(shortCodes[i] != tc);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    res.appendNull();
                } else {
                    res.appendBoolean(codes[i] != targetCode);
                }
            }
        }
        return res;
    }

    /**
     * Alias for {@link #notEq(String)}.
     */
    public BooleanColumnVector notEqualTo(String targetValue) {
        return notEq(targetValue);
    }

    /**
     * Evaluates set membership using a {@link BitSet} of dictionary codes instead of hashing strings per row.
     */
    public BooleanColumnVector isin(String... values) {
        return isin(values != null ? Arrays.asList(values) : Collections.emptyList());
    }

    /**
     * Evaluates set membership using a {@link BitSet} of dictionary codes instead of hashing strings per row.
     */
    public BooleanColumnVector isin(Collection<String> values) {
        Objects.requireNonNull(values, "values cannot be null");
        BitSet targetCodes = new BitSet(dictionarySize);
        for (String v : values) {
            if (v != null) {
                int c = encode(v);
                if (c >= 0) {
                    targetCodes.set(c);
                }
            }
        }
        return matchCodeBitSet(targetCodes);
    }

    /**
     * Returns a retention {@link BitSet} mask for rows matching {@code targetValue} by comparing primitive integer codes.
     */
    public BitSet computeEqualsMask(String targetValue) {
        BitSet mask = new BitSet(size);
        if (targetValue == null) {
            if (hasNulls()) {
                mask.or(nulls);
            }
            return mask;
        }
        int targetCode = encode(targetValue);
        if (targetCode < 0) {
            return mask; // Value not in dictionary: 0 rows match without scanning!
        }
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            short tc = (short) targetCode;
            for (int i = 0; i < size; i++) {
                if ((!anyNulls || !nulls.get(i)) && shortCodes[i] == tc) {
                    mask.set(i);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if ((!anyNulls || !nulls.get(i)) && codes[i] == targetCode) {
                    mask.set(i);
                }
            }
        }
        return mask;
    }

    /**
     * Returns a retention {@link BitSet} mask for rows NOT matching {@code targetValue} by comparing primitive integer codes.
     */
    public BitSet computeNotEqualsMask(String targetValue) {
        BitSet mask = new BitSet(size);
        if (targetValue == null) {
            mask.set(0, size);
            if (hasNulls()) {
                mask.andNot(nulls);
            }
            return mask;
        }
        int targetCode = encode(targetValue);
        if (targetCode < 0) {
            mask.set(0, size);
            return mask;
        }
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            short tc = (short) targetCode;
            for (int i = 0; i < size; i++) {
                if ((anyNulls && nulls.get(i)) || shortCodes[i] != tc) {
                    mask.set(i);
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if ((anyNulls && nulls.get(i)) || codes[i] != targetCode) {
                    mask.set(i);
                }
            }
        }
        return mask;
    }

    /**
     * Returns a retention {@link BitSet} mask for rows whose category is in {@code targetValues} using a code {@link BitSet}.
     */
    public BitSet computeInMask(Collection<?> targetValues) {
        BitSet mask = new BitSet(size);
        if (targetValues == null || targetValues.isEmpty()) {
            return mask;
        }
        BitSet targetCodes = new BitSet(dictionarySize);
        boolean matchNull = false;
        for (Object v : targetValues) {
            if (v == null || NA.isNA(v)) {
                matchNull = true;
            } else {
                int c = encode(v.toString());
                if (c >= 0) {
                    targetCodes.set(c);
                }
            }
        }
        boolean anyNulls = hasNulls();
        if (targetCodes.isEmpty() && !matchNull) {
            return mask;
        }
        for (int i = 0; i < size; i++) {
            if (anyNulls && nulls.get(i)) {
                if (matchNull) {
                    mask.set(i);
                }
            } else {
                int code = getCodeUnchecked(i);
                if (code >= 0 && targetCodes.get(code)) {
                    mask.set(i);
                }
            }
        }
        return mask;
    }

    /**
     * Filters this vector to rows equal to {@code targetValue} using integer code comparison.
     */
    public DictionaryColumnVector filterEquals(String targetValue) {
        BitSet mask = computeEqualsMask(targetValue);
        return filter(mask, mask.cardinality());
    }

    /**
     * Filters this vector to rows not equal to {@code targetValue} using integer code comparison.
     */
    public DictionaryColumnVector filterNotEquals(String targetValue) {
        BitSet mask = computeNotEqualsMask(targetValue);
        return filter(mask, mask.cardinality());
    }

    /**
     * Filters this vector to rows whose value is in {@code values} using integer code lookup.
     */
    public DictionaryColumnVector filterIn(String... values) {
        return filterIn(values != null ? Arrays.asList(values) : Collections.emptyList());
    }

    /**
     * Filters this vector to rows whose value is in {@code values} using integer code lookup.
     */
    public DictionaryColumnVector filterIn(Collection<String> values) {
        BitSet mask = computeInMask(values);
        return filter(mask, mask.cardinality());
    }

    /**
     * Evaluates {@code categoryPredicate} once per unique dictionary entry ({@code O(dictionarySize)})
     * and filters matching rows by integer code lookup.
     */
    public DictionaryColumnVector filterByCategory(Predicate<String> categoryPredicate) {
        Objects.requireNonNull(categoryPredicate, "categoryPredicate cannot be null");
        BitSet matchingCodes = new BitSet(dictionarySize);
        for (int c = 0; c < dictionarySize; c++) {
            if (categoryPredicate.test(dictionary[c])) {
                matchingCodes.set(c);
            }
        }
        BitSet rowMask = new BitSet(size);
        if (!matchingCodes.isEmpty()) {
            boolean anyNulls = hasNulls();
            for (int i = 0; i < size; i++) {
                if (!anyNulls || !nulls.get(i)) {
                    int code = getCodeUnchecked(i);
                    if (code >= 0 && matchingCodes.get(code)) {
                        rowMask.set(i);
                    }
                }
            }
        }
        return filter(rowMask, rowMask.cardinality());
    }

    /**
     * Counts the frequency of each non-null category in a single primitive integer-code pass.
     */
    public Map<String, Integer> valueCounts() {
        return valueCounts(false);
    }

    /**
     * Counts the frequency of each category in a single primitive integer-code pass, optionally including {@code null}.
     */
    public Map<String, Integer> valueCounts(boolean includeNulls) {
        int[] counts = new int[dictionarySize];
        int nullCnt = 0;
        boolean anyNulls = hasNulls();
        if (shortStorage) {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    nullCnt++;
                } else {
                    short c = shortCodes[i];
                    if (c >= 0) {
                        counts[c]++;
                    }
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (anyNulls && nulls.get(i)) {
                    nullCnt++;
                } else {
                    int c = codes[i];
                    if (c >= 0) {
                        counts[c]++;
                    }
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>(dictionarySize + 1);
        for (int c = 0; c < dictionarySize; c++) {
            if (counts[c] > 0) {
                result.put(dictionary[c], counts[c]);
            }
        }
        if (includeNulls && nullCnt > 0) {
            result.put(null, nullCnt);
        }
        return result;
    }

    private BooleanColumnVector matchCodeBitSet(BitSet matchingCodes) {
        BooleanColumnVector res = new BooleanColumnVector(name, size, true);
        boolean anyNulls = hasNulls();
        boolean noneMatch = matchingCodes.isEmpty();
        for (int i = 0; i < size; i++) {
            if (anyNulls && nulls.get(i)) {
                res.appendNull();
            } else if (noneMatch) {
                res.appendBoolean(false);
            } else {
                int code = getCodeUnchecked(i);
                res.appendBoolean(code >= 0 && matchingCodes.get(code));
            }
        }
        return res;
    }

    // ==========================================
    // Dictionary-Accelerated String Operations
    // ==========================================

    public BooleanColumnVector contains(CharSequence sub) {
        Objects.requireNonNull(sub, "sub cannot be null");
        BitSet matchingCodes = new BitSet(dictionarySize);
        for (int c = 0; c < dictionarySize; c++) {
            if (dictionary[c].contains(sub)) {
                matchingCodes.set(c);
            }
        }
        return matchCodeBitSet(matchingCodes);
    }

    public BooleanColumnVector startsWith(String prefix) {
        Objects.requireNonNull(prefix, "prefix cannot be null");
        BitSet matchingCodes = new BitSet(dictionarySize);
        for (int c = 0; c < dictionarySize; c++) {
            if (dictionary[c].startsWith(prefix)) {
                matchingCodes.set(c);
            }
        }
        return matchCodeBitSet(matchingCodes);
    }

    public BooleanColumnVector endsWith(String suffix) {
        Objects.requireNonNull(suffix, "suffix cannot be null");
        BitSet matchingCodes = new BitSet(dictionarySize);
        for (int c = 0; c < dictionarySize; c++) {
            if (dictionary[c].endsWith(suffix)) {
                matchingCodes.set(c);
            }
        }
        return matchCodeBitSet(matchingCodes);
    }

    public IntColumnVector length() {
        int[] dictLengths = new int[dictionarySize];
        for (int c = 0; c < dictionarySize; c++) {
            dictLengths[c] = dictionary[c].length();
        }
        IntColumnVector res = new IntColumnVector(name, size, true);
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            if (anyNulls && nulls.get(i)) {
                res.appendNull();
            } else {
                res.appendInt(dictLengths[getCodeUnchecked(i)]);
            }
        }
        return res;
    }

    public ObjectColumnVector split(String regex, int n) {
        Objects.requireNonNull(regex, "regex cannot be null");
        String[][] splitDict = new String[dictionarySize][];
        for (int c = 0; c < dictionarySize; c++) {
            splitDict[c] = dictionary[c].split(regex, n);
        }
        ObjectColumnVector res = new ObjectColumnVector(name, String[].class, size, true);
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            if (anyNulls && nulls.get(i)) {
                res.appendNull();
            } else {
                String[] proto = splitDict[getCodeUnchecked(i)];
                res.appendObject(Arrays.copyOf(proto, proto.length));
            }
        }
        return res;
    }

    public ObjectColumnVector split(String regex) {
        return split(regex, -1);
    }

    /**
     * Transforms the unique dictionary entries using {@code transformer} in {@code O(dictionarySize)} time
     * and returns a new deduplicated {@link DictionaryColumnVector}.
     */
    public DictionaryColumnVector transformCategories(StringAccessor.StringTransformer transformer) {
        return transformCategories(false, transformer);
    }

    public DictionaryColumnVector transformCategories(StringAccessor.StringTransformer transformer, boolean inPlace) {
        return transformCategories(inPlace, transformer);
    }

    /**
     * Transforms the unique dictionary entries using {@code transformer} in {@code O(dictionarySize)} time.
     * If {@code inPlace} is true, updates this vector's dictionary and remaps any merged categories.
     */
    public DictionaryColumnVector transformCategories(boolean inPlace, StringAccessor.StringTransformer transformer) {
        Objects.requireNonNull(transformer, "transformer cannot be null");
        String[] newDict = new String[Math.max(DEFAULT_INITIAL_CAPACITY, dictionarySize)];
        Map<String, Integer> newIndex = new HashMap<>(Math.max(16, dictionarySize * 2));
        int[] codeRemap = new int[dictionarySize];
        int newDictSize = 0;
        boolean anyMerged = false;

        for (int c = 0; c < dictionarySize; c++) {
            String transformed = transformer.transform(dictionary[c]);
            Integer existingCode = newIndex.get(transformed);
            if (existingCode != null) {
                codeRemap[c] = existingCode;
                anyMerged = true;
            } else {
                int assigned = newDictSize++;
                newDict[assigned] = transformed;
                newIndex.put(transformed, assigned);
                codeRemap[c] = assigned;
            }
        }

        if (inPlace) {
            this.dictionary = newDict;
            this.dictionarySize = newDictSize;
            this.dictionaryIndex.clear();
            this.dictionaryIndex.putAll(newIndex);
            this.categoryRanks = null;
            if (anyMerged) {
                boolean anyNulls = hasNulls();
                for (int i = 0; i < size; i++) {
                    if (!anyNulls || !nulls.get(i)) {
                        int oldCode = getCodeUnchecked(i);
                        if (oldCode >= 0) {
                            setCodeInternal(i, codeRemap[oldCode]);
                        }
                    }
                }
            }
            return this;
        }

        int cap = Math.max(DEFAULT_INITIAL_CAPACITY, size);
        BitSet copyNulls = new BitSet(size);
        copyNulls.or(nulls);
        boolean anyNulls = hasNulls();

        if (shortStorage) {
            short[] newShortCodes = new short[cap];
            Arrays.fill(newShortCodes, size, cap, NULL_SHORT_CODE);
            if (!anyMerged) {
                System.arraycopy(shortCodes, 0, newShortCodes, 0, size);
            } else {
                for (int i = 0; i < size; i++) {
                    if (anyNulls && nulls.get(i)) {
                        newShortCodes[i] = NULL_SHORT_CODE;
                    } else {
                        newShortCodes[i] = (short) codeRemap[shortCodes[i]];
                    }
                }
            }
            return new DictionaryColumnVector(
                name, nullable, newDict, newDictSize, newIndex,
                null, newShortCodes, true, copyNulls, size, null
            );
        } else {
            int[] newCodes = new int[cap];
            Arrays.fill(newCodes, size, cap, NULL_CODE);
            if (!anyMerged) {
                System.arraycopy(codes, 0, newCodes, 0, size);
            } else {
                for (int i = 0; i < size; i++) {
                    if (anyNulls && nulls.get(i)) {
                        newCodes[i] = NULL_CODE;
                    } else {
                        newCodes[i] = codeRemap[codes[i]];
                    }
                }
            }
            return new DictionaryColumnVector(
                name, nullable, newDict, newDictSize, newIndex,
                newCodes, null, false, copyNulls, size, null
            );
        }
    }

    /**
     * Evaluates {@code transformer} once per unique dictionary entry and produces a {@link StringColumnVector}
     * (compatible with {@link StringAccessor} return types). If {@code inPlace} is true, also updates this vector in-place.
     */
    public StringColumnVector applyStringTransform(boolean inPlace, StringAccessor.StringTransformer transformer) {
        if (inPlace) {
            transformCategories(true, transformer);
            return toStringColumnVector();
        }
        String[] transformedDict = new String[dictionarySize];
        for (int c = 0; c < dictionarySize; c++) {
            transformedDict[c] = transformer.transform(dictionary[c]);
        }
        String[] outData = new String[size];
        boolean anyNulls = hasNulls();
        for (int i = 0; i < size; i++) {
            if (!anyNulls || !nulls.get(i)) {
                int code = getCodeUnchecked(i);
                if (code >= 0) {
                    outData[i] = transformedDict[code];
                }
            }
        }
        BitSet copyNulls = new BitSet(size);
        copyNulls.or(nulls);
        return new StringColumnVector(name, nullable, outData, copyNulls, size);
    }

    public DictionaryColumnVector toLowerCaseCategory(boolean inPlace) {
        return transformCategories(inPlace, s -> s.toLowerCase(Locale.ROOT));
    }

    public DictionaryColumnVector toUpperCaseCategory(boolean inPlace) {
        return transformCategories(inPlace, s -> s.toUpperCase(Locale.ROOT));
    }

    public DictionaryColumnVector trimCategory(boolean inPlace) {
        return transformCategories(inPlace, String::trim);
    }

    public DictionaryColumnVector stripCategory(boolean inPlace) {
        return transformCategories(inPlace, String::strip);
    }
}
