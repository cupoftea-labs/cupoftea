package com.cupoftea.table.vector;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.NA;

/**
 * Vectorized string expression helper providing fluent text transformations.
 * Accessible via {@code df.col("name").str} or {@code df.col("name").str()}.
 */
public class StringAccessor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ColumnRef columnRef;
    private final ColumnVector vector;

    public StringAccessor(ColumnRef columnRef) {
        this.columnRef = columnRef;
        this.vector = columnRef != null ? columnRef.getVector() : null;
    }

    public StringAccessor(ColumnVector vector) {
        this.columnRef = null;
        this.vector = Objects.requireNonNull(vector, "vector cannot be null");
    }

    public ColumnVector getVector() {
        return this.vector;
    }

    public ColumnRef getColumnRef() {
        return this.columnRef;
    }

    // ==========================================
    // String Transformations
    // ==========================================

    public StringColumnVector toLowerCase() {
        return toLowerCase(false);
    }

    public StringColumnVector toLowerCaseInPlace() {
        return toLowerCase(true);
    }

    public StringColumnVector toLowerCase(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.toLowerCase(inPlace);
        }
        return transformStrings(inPlace, s -> s.toLowerCase(Locale.ROOT));
    }

    public StringColumnVector toUpperCase() {
        return toUpperCase(false);
    }

    public StringColumnVector toUpperCaseInPlace() {
        return toUpperCase(true);
    }

    public StringColumnVector toUpperCase(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.toUpperCase(inPlace);
        }
        return transformStrings(inPlace, s -> s.toUpperCase(Locale.ROOT));
    }

    public StringColumnVector capitalize() {
        return capitalize(false);
    }

    public StringColumnVector capitalizeInPlace() {
        return capitalize(true);
    }

    public StringColumnVector capitalize(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.capitalize(inPlace);
        }
        return transformStrings(inPlace, StringAccessor::capitalizeString);
    }

    public StringColumnVector trim() {
        return trim(false);
    }

    public StringColumnVector trimInPlace() {
        return trim(true);
    }

    public StringColumnVector trim(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.trim(inPlace);
        }
        return transformStrings(inPlace, String::trim);
    }

    public StringColumnVector strip() {
        return strip(false);
    }

    public StringColumnVector stripInPlace() {
        return strip(true);
    }

    public StringColumnVector strip(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.strip(inPlace);
        }
        return transformStrings(inPlace, String::strip);
    }

    public StringColumnVector lstrip() {
        return lstrip(false);
    }

    public StringColumnVector lstripInPlace() {
        return lstrip(true);
    }

    public StringColumnVector lstrip(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.lstrip(inPlace);
        }
        return transformStrings(inPlace, String::stripLeading);
    }

    public StringColumnVector rstrip() {
        return rstrip(false);
    }

    public StringColumnVector rstripInPlace() {
        return rstrip(true);
    }

    public StringColumnVector rstrip(boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.rstrip(inPlace);
        }
        return transformStrings(inPlace, String::stripTrailing);
    }

    public BooleanColumnVector contains(CharSequence sub) {
        if (vector instanceof StringColumnVector scv) {
            return scv.contains(sub);
        }
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.contains(sub);
        }
        Objects.requireNonNull(sub, "Substring cannot be null");
        int size = vector.size();
        BooleanColumnVector res = new BooleanColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String str = vector.getString(i);
                if (str == null) {
                    res.appendNull();
                } else {
                    res.appendBoolean(str.contains(sub));
                }
            }
        }
        return res;
    }

    public BooleanColumnVector startsWith(String prefix) {
        if (vector instanceof StringColumnVector scv) {
            return scv.startsWith(prefix);
        }
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.startsWith(prefix);
        }
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        int size = vector.size();
        BooleanColumnVector res = new BooleanColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String str = vector.getString(i);
                if (str == null) {
                    res.appendNull();
                } else {
                    res.appendBoolean(str.startsWith(prefix));
                }
            }
        }
        return res;
    }

    public BooleanColumnVector endsWith(String suffix) {
        if (vector instanceof StringColumnVector scv) {
            return scv.endsWith(suffix);
        }
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.endsWith(suffix);
        }
        Objects.requireNonNull(suffix, "Suffix cannot be null");
        int size = vector.size();
        BooleanColumnVector res = new BooleanColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String str = vector.getString(i);
                if (str == null) {
                    res.appendNull();
                } else {
                    res.appendBoolean(str.endsWith(suffix));
                }
            }
        }
        return res;
    }

    public StringColumnVector replace(String target, String replacement) {
        return replace(target, replacement, false);
    }

    public StringColumnVector replaceInPlace(String target, String replacement) {
        return replace(target, replacement, true);
    }

    public StringColumnVector replace(String target, String replacement, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.replace(target, replacement, inPlace);
        }
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(replacement, "Replacement cannot be null");
        return transformStrings(inPlace, s -> s.replace(target, replacement));
    }

    public StringColumnVector replaceAll(String regex, String replacement) {
        return replaceAll(regex, replacement, false);
    }

    public StringColumnVector replaceAllInPlace(String regex, String replacement) {
        return replaceAll(regex, replacement, true);
    }

    public StringColumnVector replaceAll(String regex, String replacement, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.replaceAll(regex, replacement, inPlace);
        }
        Objects.requireNonNull(regex, "Regex cannot be null");
        Objects.requireNonNull(replacement, "Replacement cannot be null");
        return transformStrings(inPlace, s -> s.replaceAll(regex, replacement));
    }

    public ObjectColumnVector split(String regex, int n) {
        if (vector instanceof StringColumnVector scv) {
            return scv.split(regex, n);
        }
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.split(regex, n);
        }
        Objects.requireNonNull(regex, "Regex cannot be null");
        int size = vector.size();
        ObjectColumnVector res = new ObjectColumnVector(vector.getName(), String[].class, size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String s = vector.getString(i);
                if (s == null) {
                    res.appendNull();
                } else {
                    res.appendObject(s.split(regex, n));
                }
            }
        }
        return res;
    }

    public ObjectColumnVector split(String regex) {
        return split(regex, -1);
    }

    public StringColumnVector substring(int start, int end) {
        return substring(start, end, false);
    }

    public StringColumnVector substringInPlace(int start, int end) {
        return substring(start, end, true);
    }

    public StringColumnVector substring(int start, int end, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.substring(start, end, inPlace);
        }
        return transformStrings(inPlace, s -> sliceString(s, start, end));
    }

    public StringColumnVector substring(int start) {
        return substring(start, false);
    }

    public StringColumnVector substringInPlace(int start) {
        return substring(start, true);
    }

    public StringColumnVector substring(int start, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.substring(start, inPlace);
        }
        return transformStrings(inPlace, s -> sliceString(s, start));
    }

    public StringColumnVector slice(int start, int end) {
        return substring(start, end, false);
    }

    public StringColumnVector sliceInPlace(int start, int end) {
        return substring(start, end, true);
    }

    public StringColumnVector slice(int start) {
        return substring(start, false);
    }

    public StringColumnVector sliceInPlace(int start) {
        return substring(start, true);
    }

    public IntColumnVector length() {
        if (vector instanceof StringColumnVector scv) {
            return scv.length();
        }
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.length();
        }
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String s = vector.getString(i);
                if (s == null) {
                    res.appendNull();
                } else {
                    res.appendInt(s.length());
                }
            }
        }
        return res;
    }

    public StringColumnVector padLeft(int width, char pad) {
        return padLeft(width, pad, false);
    }

    public StringColumnVector padLeftInPlace(int width, char pad) {
        return padLeft(width, pad, true);
    }

    public StringColumnVector padLeft(int width, char pad, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.padLeft(width, pad, inPlace);
        }
        return transformStrings(inPlace, s -> padLeftString(s, width, pad));
    }

    public StringColumnVector padRight(int width, char pad) {
        return padRight(width, pad, false);
    }

    public StringColumnVector padRightInPlace(int width, char pad) {
        return padRight(width, pad, true);
    }

    public StringColumnVector padRight(int width, char pad, boolean inPlace) {
        if (vector instanceof StringColumnVector scv) {
            return scv.padRight(width, pad, inPlace);
        }
        return transformStrings(inPlace, s -> padRightString(s, width, pad));
    }

    // ==========================================
    // String to Date/Time Parsing Helpers
    // ==========================================

    public ObjectColumnVector toLocalDate() {
        return toLocalDate(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public ObjectColumnVector toLocalDate(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        return toLocalDate(DateTimeFormatter.ofPattern(pattern));
    }

    public ObjectColumnVector toLocalDate(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter cannot be null");
        int size = vector.size();
        ObjectColumnVector res = new ObjectColumnVector(vector.getName(), LocalDate.class, size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String s = vector.getString(i);
                if (s == null || NA.isNA(s)) {
                    res.appendNull();
                } else {
                    res.appendObject(LocalDate.parse(s.trim(), formatter));
                }
            }
        }
        return res;
    }

    public ObjectColumnVector toLocalDateTime() {
        return toLocalDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public ObjectColumnVector toLocalDateTime(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        return toLocalDateTime(DateTimeFormatter.ofPattern(pattern));
    }

    public ObjectColumnVector toLocalDateTime(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter cannot be null");
        int size = vector.size();
        ObjectColumnVector res = new ObjectColumnVector(vector.getName(), LocalDateTime.class, size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String s = vector.getString(i);
                if (s == null || NA.isNA(s)) {
                    res.appendNull();
                } else {
                    res.appendObject(LocalDateTime.parse(s.trim(), formatter));
                }
            }
        }
        return res;
    }

    public ObjectColumnVector toInstant() {
        return toInstant(DateTimeFormatter.ISO_INSTANT);
    }

    public ObjectColumnVector toInstant(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter cannot be null");
        int size = vector.size();
        ObjectColumnVector res = new ObjectColumnVector(vector.getName(), Instant.class, size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                String s = vector.getString(i);
                if (s == null || NA.isNA(s)) {
                    res.appendNull();
                } else {
                    res.appendObject(formatter.parse(s.trim(), Instant::from));
                }
            }
        }
        return res;
    }

    // ==========================================
    // Internal Helper Utilities
    // ==========================================

    @FunctionalInterface
    public interface StringTransformer {
        String transform(String input);
    }

    private StringColumnVector transformStrings(boolean inPlace, StringTransformer transformer) {
        if (vector instanceof DictionaryColumnVector dcv) {
            return dcv.applyStringTransform(inPlace, transformer);
        }
        int size = vector.size();
        StringColumnVector target;
        if (inPlace && vector instanceof StringColumnVector scv) {
            target = scv;
        } else {
            target = new StringColumnVector(vector.getName(), size, true);
        }
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                if (inPlace) {
                    vector.setNull(i);
                } else {
                    target.appendNull();
                }
                continue;
            }
            String s = vector.getString(i);
            if (s == null) {
                if (inPlace) {
                    vector.setNull(i);
                } else {
                    target.appendNull();
                }
            } else {
                String transformed = transformer.transform(s);
                if (inPlace) {
                    vector.setObject(i, transformed);
                } else {
                    target.appendString(transformed);
                }
            }
        }
        return (inPlace && vector instanceof StringColumnVector) ? (StringColumnVector) vector : target;
    }

    public static String capitalizeString(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int firstCodePoint = s.codePointAt(0);
        int charCount = Character.charCount(firstCodePoint);
        String first = new String(Character.toChars(Character.toUpperCase(firstCodePoint)));
        String rest = s.substring(charCount).toLowerCase(Locale.ROOT);
        return first + rest;
    }

    public static String sliceString(String str, int start, int end) {
        if (str == null) return null;
        int len = str.length();
        int s = start < 0 ? Math.max(0, len + start) : Math.min(start, len);
        int e = end < 0 ? Math.max(0, len + end) : Math.min(end, len);
        if (s >= e) {
            return "";
        }
        return str.substring(s, e);
    }

    public static String sliceString(String str, int start) {
        if (str == null) return null;
        int len = str.length();
        int s = start < 0 ? Math.max(0, len + start) : Math.min(start, len);
        return str.substring(s);
    }

    public static String padLeftString(String s, int width, char pad) {
        if (s == null) return null;
        int len = s.length();
        if (len >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        int padCount = width - len;
        for (int i = 0; i < padCount; i++) {
            sb.append(pad);
        }
        sb.append(s);
        return sb.toString();
    }

    public static String padRightString(String s, int width, char pad) {
        if (s == null) return null;
        int len = s.length();
        if (len >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        int padCount = width - len;
        for (int i = 0; i < padCount; i++) {
            sb.append(pad);
        }
        return sb.toString();
    }
}
