package com.CupOfTea.table;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Aggregations {

    public static Aggregate sum(String column) {
        return new BaseAggregate(column, column + "Sum") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                BigDecimal sum = BigDecimal.ZERO;
                boolean hasNonNull = false;
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    BigDecimal bd = toBigDecimal(val);
                    if (bd != null) {
                        sum = sum.add(bd);
                        hasNonNull = true;
                    }
                }
                if (!hasNonNull) {
                    return null;
                }
                return convertFromBigDecimal(sum, colType);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                if (colType == null) return Double.class;
                if (colType == Integer.class || colType == Long.class || colType == Float.class ||
                    colType == Double.class || colType == BigDecimal.class) {
                    return colType;
                }
                return Double.class;
            }
        };
    }

    public static Aggregate mean(String column) {
        return createMeanAggregate(column, column + "Mean");
    }

    public static Aggregate average(String column) {
        return createMeanAggregate(column, column + "Average");
    }

    public static Aggregate avg(String column) {
        return createMeanAggregate(column, column + "Avg");
    }

    private static Aggregate createMeanAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                BigDecimal sum = BigDecimal.ZERO;
                long count = 0;
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    BigDecimal bd = toBigDecimal(val);
                    if (bd != null) {
                        sum = sum.add(bd);
                        count++;
                    }
                }
                if (count == 0) {
                    return null;
                }
                Class<?> colType = findColumnType(column, sourceHeaders);
                BigDecimal avg = sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
                if (colType == BigDecimal.class) {
                    return avg;
                }
                return avg.doubleValue();
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                if (colType == BigDecimal.class) {
                    return BigDecimal.class;
                }
                return Double.class;
            }
        };
    }

    public static Aggregate count(String column) {
        return new BaseAggregate(column, column + "Count") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                int count = 0;
                for (HashMap<String, Object> row : rows) {
                    if (row.get(column) != null) {
                        count++;
                    }
                }
                return count;
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Integer.class;
            }
        };
    }

    public static Aggregate count() {
        return new BaseAggregate(null, "count") {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                return rows.size();
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return Integer.class;
            }
        };
    }

    public static Aggregate min(String column) {
        return createMinAggregate(column, column + "Min");
    }

    public static Aggregate minimum(String column) {
        return createMinAggregate(column, column + "Minimum");
    }

    private static Aggregate createMinAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                List<Object> values = new ArrayList<>();
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null) {
                        values.add(val);
                    }
                }
                if (values.isEmpty()) return null;
                return computeMin(values);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate max(String column) {
        return createMaxAggregate(column, column + "Max");
    }

    public static Aggregate maximum(String column) {
        return createMaxAggregate(column, column + "Maximum");
    }

    private static Aggregate createMaxAggregate(String column, String defaultOutputName) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                List<Object> values = new ArrayList<>();
                for (HashMap<String, Object> row : rows) {
                    Object val = row.get(column);
                    if (val != null) {
                        values.add(val);
                    }
                }
                if (values.isEmpty()) return null;
                return computeMax(values);
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                Class<?> colType = findColumnType(column, sourceHeaders);
                return colType != null ? colType : Object.class;
            }
        };
    }

    public static Aggregate aggregate(String column, String separator) {
        return createListAgg(column, column + "Aggregate", separator);
    }

    public static Aggregate listAgg(String column, String separator) {
        return createListAgg(column, column + "ListAgg", separator);
    }

    private static Aggregate createListAgg(String column, String defaultOutputName, String separator) {
        return new BaseAggregate(column, defaultOutputName) {
            @Override
            public Object compute(List<HashMap<String, Object>> rows, List<Column> sourceHeaders) {
                return rows.stream()
                        .map(r -> r.get(column))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.joining(separator));
            }

            @Override
            public Class<?> getResultType(List<Column> sourceHeaders) {
                return String.class;
            }
        };
    }

    public static abstract class BaseAggregate implements Aggregate {
        protected final String column;
        protected String outputColumnName;

        public BaseAggregate(String column, String defaultOutputName) {
            this.column = column;
            this.outputColumnName = defaultOutputName;
        }

        @Override
        public String getColumn() {
            return column;
        }

        @Override
        public String getOutputColumnName() {
            return outputColumnName;
        }

        @Override
        public Aggregate as(String alias) {
            if (alias == null || alias.trim().isEmpty()) {
                throw new IllegalArgumentException("Alias cannot be null or empty");
            }
            this.outputColumnName = alias;
            return this;
        }
    }

    public static Class<?> findColumnType(String column, List<Column> sourceHeaders) {
        if (column == null || sourceHeaders == null) return null;
        for (Column col : sourceHeaders) {
            if (column.equals(col.columnName())) {
                return col.columnType();
            }
        }
        return null;
    }

    public static BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof BigInteger) return new BigDecimal((BigInteger) val);
        if (val instanceof Double || val instanceof Float) {
            return BigDecimal.valueOf(((Number) val).doubleValue());
        }
        if (val instanceof Number) {
            return BigDecimal.valueOf(((Number) val).longValue());
        }
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Object convertFromBigDecimal(BigDecimal val, Class<?> targetType) {
        if (val == null) return null;
        if (targetType == null) return val.doubleValue();
        if (targetType == Integer.class || targetType == int.class) {
            return val.intValue();
        }
        if (targetType == Long.class || targetType == long.class) {
            return val.longValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return val.floatValue();
        }
        if (targetType == BigDecimal.class) {
            return val;
        }
        return val.doubleValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object computeMin(List<Object> values) {
        if (values.isEmpty()) return null;
        Object first = values.get(0);
        if (first instanceof Number) {
            Object min = first;
            BigDecimal minBd = toBigDecimal(first);
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                BigDecimal vBd = toBigDecimal(v);
                if (vBd != null && (minBd == null || vBd.compareTo(minBd) < 0)) {
                    minBd = vBd;
                    min = v;
                }
            }
            return min;
        } else if (first instanceof Comparable) {
            Object min = first;
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                if (v instanceof Comparable && ((Comparable) v).compareTo(min) < 0) {
                    min = v;
                }
            }
            return min;
        }
        return first;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object computeMax(List<Object> values) {
        if (values.isEmpty()) return null;
        Object first = values.get(0);
        if (first instanceof Number) {
            Object max = first;
            BigDecimal maxBd = toBigDecimal(first);
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                BigDecimal vBd = toBigDecimal(v);
                if (vBd != null && (maxBd == null || vBd.compareTo(maxBd) > 0)) {
                    maxBd = vBd;
                    max = v;
                }
            }
            return max;
        } else if (first instanceof Comparable) {
            Object max = first;
            for (int i = 1; i < values.size(); i++) {
                Object v = values.get(i);
                if (v instanceof Comparable && ((Comparable) v).compareTo(max) > 0) {
                    max = v;
                }
            }
            return max;
        }
        return first;
    }
}
