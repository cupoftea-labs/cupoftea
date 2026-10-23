package com.cupoftea.table.vector;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.NA;

/**
 * Vectorized temporal expression helper providing fluent date and time operations.
 * Operates on {@link LocalDate}, {@link LocalDateTime}, {@link Instant}, and {@link Date} columns.
 * Accessible via {@code df.col("date").dt} or {@code df.col("date").dt()}.
 */
public class TemporalAccessor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ColumnRef columnRef;
    private final ColumnVector vector;
    private final ZoneId zone;

    public TemporalAccessor(ColumnRef columnRef) {
        this(columnRef, columnRef != null ? columnRef.getVector() : null, ZoneOffset.UTC);
    }

    public TemporalAccessor(ColumnVector vector) {
        this(null, vector, ZoneOffset.UTC);
    }

    public TemporalAccessor(ColumnRef columnRef, ColumnVector vector, ZoneId zone) {
        this.columnRef = columnRef;
        this.vector = vector;
        this.zone = zone != null ? zone : ZoneOffset.UTC;
    }

    public ColumnVector getVector() {
        return this.vector;
    }

    public ColumnRef getColumnRef() {
        return this.columnRef;
    }

    public ZoneId getZone() {
        return this.zone;
    }

    public TemporalAccessor withZone(ZoneId zone) {
        return new TemporalAccessor(this.columnRef, this.vector, zone);
    }

    // ==========================================
    // Calendar Extraction Operations
    // ==========================================

    public IntColumnVector year() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractYear(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector month() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractMonth(val, zone));
                }
            }
        }
        return res;
    }

    public StringColumnVector monthName() {
        return monthName(Locale.ENGLISH);
    }

    public StringColumnVector monthName(Locale locale) {
        Locale loc = locale != null ? locale : Locale.ENGLISH;
        int size = vector.size();
        StringColumnVector res = new StringColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendString(extractMonthName(val, zone, loc));
                }
            }
        }
        return res;
    }

    public IntColumnVector dayOfMonth() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractDayOfMonth(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector dayOfWeek() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractDayOfWeek(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector dayOfYear() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractDayOfYear(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector quarter() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    int m = extractMonth(val, zone);
                    res.appendInt(((m - 1) / 3) + 1);
                }
            }
        }
        return res;
    }

    // ==========================================
    // Time Extraction Operations
    // ==========================================

    public IntColumnVector hour() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractHour(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector minute() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractMinute(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector second() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractSecond(val, zone));
                }
            }
        }
        return res;
    }

    public IntColumnVector nano() {
        int size = vector.size();
        IntColumnVector res = new IntColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendInt(extractNano(val, zone));
                }
            }
        }
        return res;
    }

    // ==========================================
    // Date Arithmetic Operations
    // ==========================================

    public ColumnVector plusDays(long days) {
        return plusDays(days, false);
    }

    public ColumnVector plusDaysInPlace(long days) {
        return plusDays(days, true);
    }

    public ColumnVector plusDays(long days, boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyPlusDays(val, days));
    }

    public ColumnVector minusDays(long days) {
        return plusDays(-days, false);
    }

    public ColumnVector minusDaysInPlace(long days) {
        return plusDays(-days, true);
    }

    public ColumnVector plusMonths(long months) {
        return plusMonths(months, false);
    }

    public ColumnVector plusMonthsInPlace(long months) {
        return plusMonths(months, true);
    }

    public ColumnVector plusMonths(long months, boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyPlusMonths(val, months, z));
    }

    public ColumnVector minusMonths(long months) {
        return plusMonths(-months, false);
    }

    public ColumnVector minusMonthsInPlace(long months) {
        return plusMonths(-months, true);
    }

    public ColumnVector plusYears(long years) {
        return plusYears(years, false);
    }

    public ColumnVector plusYearsInPlace(long years) {
        return plusYears(years, true);
    }

    public ColumnVector plusYears(long years, boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyPlusYears(val, years, z));
    }

    public ColumnVector minusYears(long years) {
        return plusYears(-years, false);
    }

    public ColumnVector minusYearsInPlace(long years) {
        return plusYears(-years, true);
    }

    public ColumnVector plusHours(long hours) {
        return plusHours(hours, false);
    }

    public ColumnVector plusHoursInPlace(long hours) {
        return plusHours(hours, true);
    }

    public ColumnVector plusHours(long hours, boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyPlusHours(val, hours));
    }

    public ColumnVector minusHours(long hours) {
        return plusHours(-hours, false);
    }

    public ColumnVector minusHoursInPlace(long hours) {
        return plusHours(-hours, true);
    }

    // ==========================================
    // Difference Calculation Operations
    // ==========================================

    public LongColumnVector daysBetween(String otherCol) {
        ColumnVector other = resolveColumn(otherCol);
        return daysBetween(other);
    }

    public LongColumnVector daysBetween(ColumnVector other) {
        Objects.requireNonNull(other, "other ColumnVector cannot be null");
        int size = vector.size();
        if (other.size() != size) {
            throw new SchemaMismatchException("Vector sizes do not match: " + size + " vs " + other.size());
        }
        LongColumnVector res = new LongColumnVector(vector.getName() + "_days_between", size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i) || other.isNull(i)) {
                res.appendNull();
                continue;
            }
            Object v1 = vector.getObject(i);
            Object v2 = other.getObject(i);
            if (v1 == null || v2 == null || NA.isNA(v1) || NA.isNA(v2)) {
                res.appendNull();
                continue;
            }
            res.appendLong(computeDaysBetween(v1, v2, zone));
        }
        return res;
    }

    public LongColumnVector daysBetween(Temporal other) {
        Objects.requireNonNull(other, "other Temporal cannot be null");
        int size = vector.size();
        LongColumnVector res = new LongColumnVector(vector.getName() + "_days_between", size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
                continue;
            }
            Object v1 = vector.getObject(i);
            if (v1 == null || NA.isNA(v1)) {
                res.appendNull();
                continue;
            }
            res.appendLong(computeDaysBetween(v1, other, zone));
        }
        return res;
    }

    public LongColumnVector hoursBetween(String otherCol) {
        ColumnVector other = resolveColumn(otherCol);
        return hoursBetween(other);
    }

    public LongColumnVector hoursBetween(ColumnVector other) {
        Objects.requireNonNull(other, "other ColumnVector cannot be null");
        int size = vector.size();
        if (other.size() != size) {
            throw new SchemaMismatchException("Vector sizes do not match: " + size + " vs " + other.size());
        }
        LongColumnVector res = new LongColumnVector(vector.getName() + "_hours_between", size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i) || other.isNull(i)) {
                res.appendNull();
                continue;
            }
            Object v1 = vector.getObject(i);
            Object v2 = other.getObject(i);
            if (v1 == null || v2 == null || NA.isNA(v1) || NA.isNA(v2)) {
                res.appendNull();
                continue;
            }
            res.appendLong(computeHoursBetween(v1, v2, zone));
        }
        return res;
    }

    public LongColumnVector hoursBetween(Temporal other) {
        Objects.requireNonNull(other, "other Temporal cannot be null");
        int size = vector.size();
        LongColumnVector res = new LongColumnVector(vector.getName() + "_hours_between", size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
                continue;
            }
            Object v1 = vector.getObject(i);
            if (v1 == null || NA.isNA(v1)) {
                res.appendNull();
                continue;
            }
            res.appendLong(computeHoursBetween(v1, other, zone));
        }
        return res;
    }

    // ==========================================
    // Formatting & Parsing Operations
    // ==========================================

    public StringColumnVector format(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        return format(DateTimeFormatter.ofPattern(pattern));
    }

    public StringColumnVector format(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter cannot be null");
        int size = vector.size();
        StringColumnVector res = new StringColumnVector(vector.getName(), size, true);
        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                res.appendNull();
            } else {
                Object val = vector.getObject(i);
                if (val == null || NA.isNA(val)) {
                    res.appendNull();
                } else {
                    res.appendString(applyFormat(val, formatter, zone));
                }
            }
        }
        return res;
    }

    public ObjectColumnVector parseLocalDate(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
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

    public ObjectColumnVector parseLocalDateTime(String pattern) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
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

    public ObjectColumnVector parseInstant() {
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
                    res.appendObject(Instant.parse(s.trim()));
                }
            }
        }
        return res;
    }

    public ObjectColumnVector parseInstant(DateTimeFormatter formatter) {
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
    // Truncation / Flooring Operations
    // ==========================================

    public ColumnVector startOfMonth() {
        return startOfMonth(false);
    }

    public ColumnVector startOfMonthInPlace() {
        return startOfMonth(true);
    }

    public ColumnVector startOfMonth(boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyStartOfMonth(val, z));
    }

    public ColumnVector startOfYear() {
        return startOfYear(false);
    }

    public ColumnVector startOfYearInPlace() {
        return startOfYear(true);
    }

    public ColumnVector startOfYear(boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyStartOfYear(val, z));
    }

    public ColumnVector startOfDay() {
        return startOfDay(false);
    }

    public ColumnVector startOfDayInPlace() {
        return startOfDay(true);
    }

    public ColumnVector startOfDay(boolean inPlace) {
        return applyArithmetic(inPlace, (val, z) -> applyStartOfDay(val, z));
    }

    public ColumnVector truncateTo(ChronoUnit unit) {
        return truncateTo(unit, false);
    }

    public ColumnVector truncateToInPlace(ChronoUnit unit) {
        return truncateTo(unit, true);
    }

    public ColumnVector truncateTo(ChronoUnit unit, boolean inPlace) {
        Objects.requireNonNull(unit, "unit cannot be null");
        return applyArithmetic(inPlace, (val, z) -> applyTruncateTo(val, unit, z));
    }

    // ==========================================
    // Internal Helper Utilities
    // ==========================================

    @FunctionalInterface
    private interface TemporalUnaryOp {
        Object apply(Object val, ZoneId zone);
    }

    private ColumnVector resolveColumn(String otherCol) {
        Objects.requireNonNull(otherCol, "otherCol cannot be null");
        if (columnRef != null && columnRef.getDataFrame() != null) {
            ColumnVector other = columnRef.getDataFrame().getColumnVector(otherCol);
            if (other != null) {
                return other;
            }
        }
        throw new IllegalArgumentException("Column '" + otherCol + "' could not be resolved from DataFrame");
    }

    private Class<?> detectTemporalType() {
        Class<?> type = vector.getType();
        if (type != null && type != Object.class) {
            return type;
        }
        for (int i = 0; i < vector.size(); i++) {
            if (!vector.isNull(i)) {
                Object val = vector.getObject(i);
                if (val != null && !NA.isNA(val)) {
                    return val.getClass();
                }
            }
        }
        return LocalDate.class;
    }

    private ColumnVector applyArithmetic(boolean inPlace, TemporalUnaryOp op) {
        int size = vector.size();
        Class<?> targetType = detectTemporalType();
        ColumnVector target;
        if (inPlace) {
            target = vector;
        } else {
            target = new ObjectColumnVector(vector.getName(), targetType, size, vector.isNullable());
        }

        for (int i = 0; i < size; i++) {
            if (vector.isNull(i)) {
                if (inPlace) {
                    target.setNull(i);
                } else {
                    target.appendNull();
                }
                continue;
            }
            Object val = vector.getObject(i);
            if (val == null || NA.isNA(val)) {
                if (inPlace) {
                    target.setNull(i);
                } else {
                    target.appendNull();
                }
                continue;
            }
            Object updated = op.apply(val, zone);
            if (inPlace) {
                target.setObject(i, updated);
            } else {
                target.appendObject(updated);
            }
        }
        return target;
    }

    private static int extractYear(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.getYear();
        if (val instanceof LocalDateTime ldt) return ldt.getYear();
        if (val instanceof Instant inst) return inst.atZone(zone).getYear();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getYear();
        if (val instanceof ZonedDateTime zdt) return zdt.getYear();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractMonth(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.getMonthValue();
        if (val instanceof LocalDateTime ldt) return ldt.getMonthValue();
        if (val instanceof Instant inst) return inst.atZone(zone).getMonthValue();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getMonthValue();
        if (val instanceof ZonedDateTime zdt) return zdt.getMonthValue();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static String extractMonthName(Object val, ZoneId zone, Locale locale) {
        if (val instanceof LocalDate ld) return ld.getMonth().getDisplayName(TextStyle.FULL, locale);
        if (val instanceof LocalDateTime ldt) return ldt.getMonth().getDisplayName(TextStyle.FULL, locale);
        if (val instanceof Instant inst) return inst.atZone(zone).getMonth().getDisplayName(TextStyle.FULL, locale);
        if (val instanceof Date d) return d.toInstant().atZone(zone).getMonth().getDisplayName(TextStyle.FULL, locale);
        if (val instanceof ZonedDateTime zdt) return zdt.getMonth().getDisplayName(TextStyle.FULL, locale);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractDayOfMonth(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.getDayOfMonth();
        if (val instanceof LocalDateTime ldt) return ldt.getDayOfMonth();
        if (val instanceof Instant inst) return inst.atZone(zone).getDayOfMonth();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getDayOfMonth();
        if (val instanceof ZonedDateTime zdt) return zdt.getDayOfMonth();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractDayOfWeek(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.getDayOfWeek().getValue();
        if (val instanceof LocalDateTime ldt) return ldt.getDayOfWeek().getValue();
        if (val instanceof Instant inst) return inst.atZone(zone).getDayOfWeek().getValue();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getDayOfWeek().getValue();
        if (val instanceof ZonedDateTime zdt) return zdt.getDayOfWeek().getValue();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractDayOfYear(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.getDayOfYear();
        if (val instanceof LocalDateTime ldt) return ldt.getDayOfYear();
        if (val instanceof Instant inst) return inst.atZone(zone).getDayOfYear();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getDayOfYear();
        if (val instanceof ZonedDateTime zdt) return zdt.getDayOfYear();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractHour(Object val, ZoneId zone) {
        if (val instanceof LocalDate) return 0;
        if (val instanceof LocalDateTime ldt) return ldt.getHour();
        if (val instanceof Instant inst) return inst.atZone(zone).getHour();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getHour();
        if (val instanceof ZonedDateTime zdt) return zdt.getHour();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractMinute(Object val, ZoneId zone) {
        if (val instanceof LocalDate) return 0;
        if (val instanceof LocalDateTime ldt) return ldt.getMinute();
        if (val instanceof Instant inst) return inst.atZone(zone).getMinute();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getMinute();
        if (val instanceof ZonedDateTime zdt) return zdt.getMinute();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractSecond(Object val, ZoneId zone) {
        if (val instanceof LocalDate) return 0;
        if (val instanceof LocalDateTime ldt) return ldt.getSecond();
        if (val instanceof Instant inst) return inst.atZone(zone).getSecond();
        if (val instanceof Date d) return d.toInstant().atZone(zone).getSecond();
        if (val instanceof ZonedDateTime zdt) return zdt.getSecond();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static int extractNano(Object val, ZoneId zone) {
        if (val instanceof LocalDate) return 0;
        if (val instanceof LocalDateTime ldt) return ldt.getNano();
        if (val instanceof Instant inst) return inst.atZone(zone).getNano();
        if (val instanceof Date) return 0;
        if (val instanceof ZonedDateTime zdt) return zdt.getNano();
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyPlusDays(Object val, long days) {
        if (val instanceof LocalDate ld) return ld.plusDays(days);
        if (val instanceof LocalDateTime ldt) return ldt.plusDays(days);
        if (val instanceof Instant inst) return inst.plus(days, ChronoUnit.DAYS);
        if (val instanceof Date d) return new Date(d.getTime() + days * 86_400_000L);
        if (val instanceof ZonedDateTime zdt) return zdt.plusDays(days);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyPlusMonths(Object val, long months, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.plusMonths(months);
        if (val instanceof LocalDateTime ldt) return ldt.plusMonths(months);
        if (val instanceof Instant inst) return inst.atZone(zone).plusMonths(months).toInstant();
        if (val instanceof Date d) return Date.from(d.toInstant().atZone(zone).plusMonths(months).toInstant());
        if (val instanceof ZonedDateTime zdt) return zdt.plusMonths(months);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyPlusYears(Object val, long years, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.plusYears(years);
        if (val instanceof LocalDateTime ldt) return ldt.plusYears(years);
        if (val instanceof Instant inst) return inst.atZone(zone).plusYears(years).toInstant();
        if (val instanceof Date d) return Date.from(d.toInstant().atZone(zone).plusYears(years).toInstant());
        if (val instanceof ZonedDateTime zdt) return zdt.plusYears(years);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyPlusHours(Object val, long hours) {
        if (val instanceof LocalDateTime ldt) return ldt.plusHours(hours);
        if (val instanceof Instant inst) return inst.plus(hours, ChronoUnit.HOURS);
        if (val instanceof LocalDate ld) return ld.atStartOfDay().plusHours(hours);
        if (val instanceof Date d) return new Date(d.getTime() + hours * 3_600_000L);
        if (val instanceof ZonedDateTime zdt) return zdt.plusHours(hours);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyStartOfMonth(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.withDayOfMonth(1);
        if (val instanceof LocalDateTime ldt) return ldt.withDayOfMonth(1).toLocalDate().atStartOfDay();
        if (val instanceof Instant inst) return inst.atZone(zone).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        if (val instanceof Date d) return Date.from(d.toInstant().atZone(zone).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant());
        if (val instanceof ZonedDateTime zdt) return zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyStartOfYear(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld.withDayOfYear(1);
        if (val instanceof LocalDateTime ldt) return ldt.withDayOfYear(1).toLocalDate().atStartOfDay();
        if (val instanceof Instant inst) return inst.atZone(zone).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        if (val instanceof Date d) return Date.from(d.toInstant().atZone(zone).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant());
        if (val instanceof ZonedDateTime zdt) return zdt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyStartOfDay(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof LocalDateTime ldt) return ldt.toLocalDate().atStartOfDay();
        if (val instanceof Instant inst) return inst.atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant();
        if (val instanceof Date d) return Date.from(d.toInstant().atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant());
        if (val instanceof ZonedDateTime zdt) return zdt.truncatedTo(ChronoUnit.DAYS);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static Object applyTruncateTo(Object val, ChronoUnit unit, ZoneId zone) {
        if (val instanceof LocalDateTime ldt) return ldt.truncatedTo(unit);
        if (val instanceof Instant inst) return inst.truncatedTo(unit);
        if (val instanceof Date d) return Date.from(d.toInstant().truncatedTo(unit));
        if (val instanceof ZonedDateTime zdt) return zdt.truncatedTo(unit);
        if (val instanceof LocalDate ld) {
            if (unit == ChronoUnit.DAYS) return ld;
            if (unit == ChronoUnit.MONTHS) return ld.withDayOfMonth(1);
            if (unit == ChronoUnit.YEARS) return ld.withDayOfYear(1);
            return ld.atStartOfDay().truncatedTo(unit);
        }
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static String applyFormat(Object val, DateTimeFormatter formatter, ZoneId zone) {
        if (val instanceof LocalDate ld) return formatter.format(ld);
        if (val instanceof LocalDateTime ldt) return formatter.format(ldt);
        if (val instanceof Instant inst) return formatter.withZone(zone).format(inst);
        if (val instanceof Date d) return formatter.withZone(zone).format(d.toInstant());
        if (val instanceof ZonedDateTime zdt) return formatter.format(zdt);
        throw new IllegalArgumentException("Unsupported temporal type: " + val.getClass());
    }

    private static LocalDate toLocalDate(Object val, ZoneId zone) {
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (val instanceof Instant inst) return inst.atZone(zone).toLocalDate();
        if (val instanceof Date d) return d.toInstant().atZone(zone).toLocalDate();
        if (val instanceof ZonedDateTime zdt) return zdt.toLocalDate();
        throw new IllegalArgumentException("Cannot convert to LocalDate: " + val.getClass());
    }

    private static Instant toInstant(Object val, ZoneId zone) {
        if (val instanceof Instant inst) return inst;
        if (val instanceof LocalDateTime ldt) return ldt.atZone(zone).toInstant();
        if (val instanceof LocalDate ld) return ld.atStartOfDay(zone).toInstant();
        if (val instanceof Date d) return d.toInstant();
        if (val instanceof ZonedDateTime zdt) return zdt.toInstant();
        throw new IllegalArgumentException("Cannot convert to Instant: " + val.getClass());
    }

    public static long computeDaysBetween(Object v1, Object v2, ZoneId zone) {
        LocalDate d1 = toLocalDate(v1, zone);
        LocalDate d2 = toLocalDate(v2, zone);
        return ChronoUnit.DAYS.between(d1, d2);
    }

    public static long computeHoursBetween(Object v1, Object v2, ZoneId zone) {
        Instant i1 = toInstant(v1, zone);
        Instant i2 = toInstant(v2, zone);
        return ChronoUnit.HOURS.between(i1, i2);
    }
}
