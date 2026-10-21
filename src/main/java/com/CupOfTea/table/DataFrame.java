package com.CupOfTea.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.exceptions.SchemaMismatchException;
import com.CupOfTea.table.converters.BigDecimalConverter;
import com.CupOfTea.table.converters.BooleanConverter;
import com.CupOfTea.table.converters.DateConverter;
import com.CupOfTea.table.converters.DoubleConverter;
import com.CupOfTea.table.converters.FloatConverter;
import com.CupOfTea.table.converters.InstantConverter;
import com.CupOfTea.table.converters.IntegerConverter;
import com.CupOfTea.table.converters.LocalDateConverter;
import com.CupOfTea.table.converters.LocalDateTimeConverter;
import com.CupOfTea.table.converters.LongConverter;
import com.CupOfTea.table.converters.StringConverter;
import com.CupOfTea.table.interfaces.IDataFrame;
import com.CupOfTea.table.interfaces.IDataTypeConverter;
import com.CupOfTea.utils.interfaces.IColumnCreator;
import com.CupOfTea.utils.interfaces.IFilter;
import com.CupOfTea.utils.interfaces.Order;
import com.CupOfTea.utils.interfaces.Orderer;

public class DataFrame extends IDataFrame {

    private static final IntegerConverter INTEGER_CONVERTER = new IntegerConverter();
    private static final DoubleConverter DOUBLE_CONVERTER = new DoubleConverter();
    private static final FloatConverter FLOAT_CONVERTER = new FloatConverter();
    private static final LongConverter LONG_CONVERTER = new LongConverter();
    private static final BooleanConverter BOOLEAN_CONVERTER = new BooleanConverter();
    private static final StringConverter STRING_CONVERTER = new StringConverter();
    private static final DateConverter DATE_CONVERTER = new DateConverter();
    private static final LocalDateConverter LOCAL_DATE_CONVERTER = new LocalDateConverter();
    private static final LocalDateTimeConverter LOCAL_DATE_TIME_CONVERTER = new LocalDateTimeConverter();
    private static final InstantConverter INSTANT_CONVERTER = new InstantConverter();
    private static final BigDecimalConverter BIG_DECIMAL_CONVERTER = new BigDecimalConverter();

    private static final Map<Class<?>, IDataTypeConverter<?>> CONVERTERS = Map.ofEntries(
        Map.entry(Integer.class, INTEGER_CONVERTER),
        Map.entry(int.class, INTEGER_CONVERTER),
        Map.entry(Double.class, DOUBLE_CONVERTER),
        Map.entry(double.class, DOUBLE_CONVERTER),
        Map.entry(Float.class, FLOAT_CONVERTER),
        Map.entry(float.class, FLOAT_CONVERTER),
        Map.entry(Long.class, LONG_CONVERTER),
        Map.entry(long.class, LONG_CONVERTER),
        Map.entry(Boolean.class, BOOLEAN_CONVERTER),
        Map.entry(boolean.class, BOOLEAN_CONVERTER),
        Map.entry(String.class, STRING_CONVERTER),
        Map.entry(Date.class, DATE_CONVERTER),
        Map.entry(LocalDate.class, LOCAL_DATE_CONVERTER),
        Map.entry(LocalDateTime.class, LOCAL_DATE_TIME_CONVERTER),
        Map.entry(Instant.class, INSTANT_CONVERTER),
        Map.entry(BigDecimal.class, BIG_DECIMAL_CONVERTER)
    );

    private final HashMap<String, IColumnCreator> columnCreators = new HashMap<>();
    private final HashSet<String> pendingTypeInferenceColumns = new HashSet<>();

    public DataFrame(){
        super();
    }

    public DataFrame(Schema schema){
        super(schema);
    }

    @Override
    public Schema getSchema() {
        return Schema.fromHeader(this.header);
    }

    @Override
    public IDataFrame setSchema(Schema schema) {
        if (schema == null) {
            return this;
        }
        ArrayList<Column> newHeaders = new ArrayList<>(schema.getColumns());
        this.setHeaders(newHeaders);
        for (Column col : schema.getColumns()) {
            if (col.columnType() != null && !this.rows.isEmpty()) {
                IDataTypeConverter<?> converter = CONVERTERS.get(col.columnType());
                if (converter != null) {
                    for (var row : this.rows) {
                        Object val = row.get(col.columnName());
                        if (val != null && !col.columnType().isInstance(val)) {
                            row.put(col.columnName(), converter.convert(val, val.getClass()));
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public ArrayList<Column> getHeaders() {
        return this.header.getHeader();
    }

    @Override
    public Column getColumnHeader(String columnName) {
        return this.header.get(columnName);
    }

    @Override
    public IDataFrame moveColumn(String columnName, int finalIndex) {
        this.header.moveColumn(columnName, finalIndex);
        return this;
    }

    @Override
    public IDataFrame moveColumn(int initialIndex, int finalIndex) {
        this.header.moveColumn(initialIndex, finalIndex);
        return this;
    }

    @Override
    public IDataFrame renameColumn(String oldName, String newName) {
        this.header.renameColumn(oldName, newName);
        this.renameRowKeys(oldName, newName);
        return this;
    }

    @Override
    public IDataFrame renameColumn(int index, String newName) {
        String oldName = this.header.get(index).columnName();
        this.header.renameColumn(index, newName);
        this.renameRowKeys(oldName, newName);
        return this;
    }

    private void renameRowKeys(String oldName, String newName) {
        if (oldName.equals(newName)) {
            return;
        }
        for (var row : this.rows) {
            row.put(newName, row.get(oldName));
            row.remove(oldName);
        }
        if (this.columnCreators.containsKey(oldName)) {
            this.columnCreators.put(newName, this.columnCreators.remove(oldName));
        }
        if (this.pendingTypeInferenceColumns.remove(oldName)) {
            this.pendingTypeInferenceColumns.add(newName);
        }
    }

    @Override
    public IDataFrame renameColumn(HashMap<String, String> name) {
        name.forEach((key, value) -> {
            this.renameColumn(key, value);
        });
        return this;
    }

    /*
     * @params columnName
     * The column will be created in the last position with null value
     */
    @Override
    public IDataFrame addColumn(String columnName) {
        int index = this.header.columnCount();
        this.addColumn(index, columnName);
        return this;
    }

    @Override
    public IDataFrame addColumn(String columnName, Class<?> columnType) {
        int index = this.header.columnCount();
        this.addColumn(index, columnName, columnType);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName) {
        this.addColumn(index, columnName, (Object) null);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, Class<?> columnType) {
        header.addColumn(index, new Column(columnName, columnType));
        for (HashMap<String, Object> row : this.rows) {
            row.put(columnName, null);
        }
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable Object data) {
        ArrayList<Object> dataArray = new ArrayList<>();
        for (@SuppressWarnings("unused") var row : this.rows) {
            dataArray.add(data);
        }
        this.addColumn(index, columnName, dataArray);
        return this;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable ArrayList<Object> data) {
        if (data == null) return this.addColumn(index, columnName, (Object) data);
        if (data.size() != this.size()) {
            throw new SchemaMismatchException("Column data size (" + data.size() + ") does not match DataFrame size (" + this.size() + ")");
        }

        Class<?> columnType = String.class;
        for (Object value : data) {
            if (value != null) {
                columnType = value.getClass();
                break;
            }
        }
        header.addColumn(index, new Column(columnName, columnType)); 
        int i = 0;
        for (HashMap<String, Object> row : this.rows) {
            row.put(columnName, data.get(i));
            i++;
        }
        return this;
    }

    /**
     * Create a new column whose values are defined by the Column Creator Function
     * The column will be added to the end of the Data Frame
     * 
     * @param columnName Name of the new Column
     * @param columnCreator Function that defines the values of the new column
     */
    @Override
    public IDataFrame addColumn(String columnName, IColumnCreator columnCreator) {
        return this.addColumn(this.header.columnCount(), columnName, (Class<?>) null, columnCreator);
    }

    @Override
    public IDataFrame addColumn(String columnName, Class<?> columnType, IColumnCreator columnCreator) {
        return this.addColumn(this.header.columnCount(), columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame addColumn(String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        return this.addColumn(this.header.columnCount(), columnName, columnType, columnCreator);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator) {
        return this.addColumn(index, columnName, (Class<?>) null, columnCreator);
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, IColumnCreator columnCreator, Class<?> columnType) {
        return this.addColumn(index, columnName, columnType, columnCreator);
    }

    @Override
    public DataFrame copy() {
        DataFrame copy = new DataFrame();
        copy.header = this.header.copy();
        copy.columnCreators.putAll(this.columnCreators);
        copy.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);
        for (var row : this.rows) {
            copy.rows.add(this.cloneRow(row));
        }
        return copy;
    }

    @Override
    public IDataFrame addColumn(int index, String columnName, @Nullable Class<?> columnType, IColumnCreator columnCreator) {
        Class<?> resolvedType = columnType;

        for (var row : this.rows) {
            Object value = columnCreator.createColumn(new Row(row));
            row.put(columnName, value);
            if (resolvedType == null && value != null) {
                resolvedType = value.getClass();
            }
        }

        if (resolvedType == null) {
            resolvedType = Object.class;
            this.pendingTypeInferenceColumns.add(columnName);
        } else {
            this.pendingTypeInferenceColumns.remove(columnName);
        }

        this.columnCreators.put(columnName, columnCreator);
        this.header.addColumn(index, new Column(columnName, resolvedType));
        return this;
    }

    @Override
    public IDataFrame removeColumn(String columnName) {
        this.header.removeColumn(columnName);
        this.columnCreators.remove(columnName);
        this.pendingTypeInferenceColumns.remove(columnName);
        for(var row : this.rows) {
            row.remove(columnName);
        }

        return this;
    }

    @Override
    public IDataFrame removeColumn(int index){
        // ToDo tratar out of bond?
        String columnName = this.header.get(index).columnName();
        return this.removeColumn(columnName);
    }

    @Override
    public IDataFrame alterColumnType(String columnName, Class<?> newColumnType) {
        Column column = this.getColumnHeader(columnName);
        if (column == null) {
            throw new SchemaMismatchException("Column not found: " + columnName);
        }
        Class<?> oldColumnType = column.columnType();

        IDataTypeConverter<?> converter = CONVERTERS.get(newColumnType);
        if (converter == null) {
            throw new ConversionException("Column Type not Suported for Conversion");
        }
        
        for(var row : this.rows){
            Object value = row.get(columnName);
            if(value == null) continue;
            Object convertedValue = converter.convert(value, oldColumnType);
            row.put(columnName, convertedValue);
        }
            
        this.pendingTypeInferenceColumns.remove(columnName);
        this.header.alterColumnType(columnName, newColumnType);

        return this;
    }

    @Override
    public IDataFrame addRow(@NotNull Map<String, Object> row) {
        return this.addRow(row, true);
    }

    private IDataFrame addRow(@NotNull Map<String, Object> row, boolean clone) {
        HashMap<String, Object> rowToAdd = clone ? this.cloneRow(row) : (row instanceof HashMap ? (HashMap<String, Object>) row : new HashMap<>(row));
        for (var entry : this.columnCreators.entrySet()) {
            String columnName = entry.getKey();
            IColumnCreator creator = entry.getValue();
            if (!rowToAdd.containsKey(columnName)) {
                rowToAdd.put(columnName, creator.createColumn(new Row(rowToAdd)));
            }
        }
        if (!this.pendingTypeInferenceColumns.isEmpty()) {
            var iterator = this.pendingTypeInferenceColumns.iterator();
            while (iterator.hasNext()) {
                String columnName = iterator.next();
                Object value = rowToAdd.get(columnName);
                if (value != null) {
                    this.header.alterColumnType(columnName, value.getClass());
                    iterator.remove();
                }
            }
        }
        rows.add(rowToAdd);
        return this;
    }

    private HashMap<String, Object> cloneRow(@NotNull Map<String, Object> row) {
        HashMap<String, Object> clonedRow = new HashMap<>(row);
        for (var entry : clonedRow.entrySet()) {
            if (entry.getValue() instanceof Group group) {
                entry.setValue(new Group(group));
            }
        }
        return clonedRow;
    }

    @Override
    public IDataFrame removeRow(int index) {
        rows.remove(index);
        return this;
    }


    /**
     * Removes the filtered rows from the DataFrame
     * 
     * @param filter the rows mathing this condition will be removed out of the DataFrame
     * @return the same DataFrame but without the filtered rows
     */
    @Override
    public IDataFrame removeRows(IFilter filter) {
        this.rows.removeIf(row -> Boolean.TRUE.equals(filter.filter(new Row(row))));
        return this;
    }

    @Override
    public IDataFrame filter(IFilter filter) {

        DataFrame filteredDF = new DataFrame();

        filteredDF.setHeaders(new ArrayList<>(this.getHeaders()));
        filteredDF.columnCreators.putAll(this.columnCreators);
        filteredDF.pendingTypeInferenceColumns.addAll(this.pendingTypeInferenceColumns);

        for(var row : this.rows){
            if(Boolean.TRUE.equals(filter.filter(new Row(row)))){
                filteredDF.addRow(row, true);
            }
        }

        return filteredDF;
    }

    @Override
    public IDataFrame update(Map<String, Object> updateColumns) {
        if (updateColumns != null) {
            for(var row : this.rows){
                row.putAll(updateColumns);
            }
        }

        return this;
    }

    @Override
    public IDataFrame sum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Sum";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Object sum = null;
            Group value = (Group) row.get(column);

            if (value.getColumnType() == Integer.class) {
                Integer integerSum = 0;
                finalColumnType = Integer.class;
                for (var valuesToSum : value.groupedValues) {
                    integerSum += (Integer) valuesToSum;
                }
                sum = integerSum;
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                Double doubleSum = 0.0;
                for (var valuesToSum : value.groupedValues) {
                    doubleSum += (Double) valuesToSum;
                }
                sum = doubleSum;
            }

            row.put(columnName, sum);
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame average(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = Double.class;
        String columnName = column + "Average";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Object average = null;
            Group value = (Group) row.get(column);

            if (value.getColumnType() == Integer.class) {
                Integer integerSum = 0;
                for (var valuesToSum : value.groupedValues) {
                    integerSum += (Integer) valuesToSum;
                }
                average = Double.valueOf(integerSum) / Double.valueOf(value.groupedValues.size());
            } else if (value.getColumnType() == Double.class) {
                Double doubleSum = 0.0;
                for (var valuesToSum : value.groupedValues) {
                    doubleSum += (Double) valuesToSum;
                }
                average = doubleSum / Double.valueOf(value.groupedValues.size());
            }

            row.put(columnName, average);
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame maximum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Maximum";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Object maximum = null;
            Group value = (Group) row.get(column);

            if (value.getColumnType() == Integer.class) {
                finalColumnType = Integer.class;
                maximum = java.util.Collections.max(value.groupedValues.stream().map(a -> (Integer) a).toList());
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                maximum = java.util.Collections.max(value.groupedValues.stream().map(a -> (Double) a).toList());
            }

            row.put(columnName, maximum);
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame minimum(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = null;
        String columnName = column + "Minimum";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Object minimum = null;
            Group value = (Group) row.get(column);

            if (value.getColumnType() == Integer.class) {
                finalColumnType = Integer.class;
                minimum = java.util.Collections.min(value.groupedValues.stream().map(a -> (Integer) a).toList());
            } else if (value.getColumnType() == Double.class) {
                finalColumnType = Double.class;
                minimum = java.util.Collections.min(value.groupedValues.stream().map(a -> (Double) a).toList());
            }

            row.put(columnName, minimum);
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame count(String column) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = Integer.class;
        String columnName = column + "Count";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Group value = (Group) row.get(column);
            row.put(columnName, value.groupedValues.size());
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame aggregate(String column, String separator) {
        Column groupedColumn = this.getColumnHeader(column);
        Class<?> finalColumnType = String.class;
        String columnName = column + "Aggregate";

        if (groupedColumn == null || groupedColumn.columnType() != Group.class) {
            throw new SchemaMismatchException("Column '" + column + "' is not a grouped column");
        }

        for (var row : this.rows) {
            Group value = (Group) row.get(column);
            String aggValue = value.groupedValues.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(separator));
            row.put(columnName, aggValue);
        }

        this.header.addColumn(new Column(columnName, finalColumnType));
        return this;
    }

    @Override
    public IDataFrame orderBy(Orderer... orderer) {
        if (this.rows.isEmpty() || orderer == null || orderer.length == 0) {
            return this;
        }

        Comparator<HashMap<String, Object>> rowComparator = (a, b) -> 0;
        for (var order : orderer) {
            if (order == null || order.column() == null) {
                continue;
            }
            String columnName = order.column();
            Class<?> declaredType = this.findDeclaredColumnType(columnName);
            Comparator<Object> valueComparator = (valA, valB) -> this.compareValues(valA, valB, declaredType);
            if (order.type() == Order.DESCENDING) {
                valueComparator = valueComparator.reversed();
            }
            Comparator<Object> nullSafeComparator = Comparator.nullsLast(valueComparator);
            rowComparator = rowComparator.thenComparing(row -> row.get(columnName), nullSafeComparator);
        }

        this.rows.sort(rowComparator);
        return this;
    }

    private Class<?> findDeclaredColumnType(String columnName) {
        for (Column column : this.header.getHeader()) {
            if (column != null && columnName.equals(column.columnName())) {
                return column.columnType();
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private int compareValues(Object rawA, Object rawB, Class<?> declaredType) {
        if (NA.isNA(rawA) && NA.isNA(rawB)) {
            return 0;
        }
        if (NA.isNA(rawA)) {
            return 1;
        }
        if (NA.isNA(rawB)) {
            return -1;
        }

        Object valA = this.coerceToDeclaredType(rawA, declaredType);
        Object valB = this.coerceToDeclaredType(rawB, declaredType);

        if (valA instanceof Integer intA && valB instanceof Integer intB) {
            return Integer.compare(intA, intB);
        }
        if (valA instanceof Long longA && valB instanceof Long longB) {
            return Long.compare(longA, longB);
        }
        if (valA instanceof Double doubleA && valB instanceof Double doubleB) {
            return Double.compare(doubleA, doubleB);
        }
        if (valA instanceof Float floatA && valB instanceof Float floatB) {
            return Float.compare(floatA, floatB);
        }
        if (valA instanceof Short shortA && valB instanceof Short shortB) {
            return Short.compare(shortA, shortB);
        }
        if (valA instanceof Byte byteA && valB instanceof Byte byteB) {
            return Byte.compare(byteA, byteB);
        }
        if (valA instanceof Boolean boolA && valB instanceof Boolean boolB) {
            return Boolean.compare(boolA, boolB);
        }
        if (valA instanceof Instant instA && valB instanceof Instant instB) {
            return instA.compareTo(instB);
        }
        if (valA instanceof LocalDate dateA && valB instanceof LocalDate dateB) {
            return dateA.compareTo(dateB);
        }
        if (valA instanceof LocalDateTime dtA && valB instanceof LocalDateTime dtB) {
            return dtA.compareTo(dtB);
        }
        if (valA instanceof BigDecimal decA && valB instanceof BigDecimal decB) {
            return decA.compareTo(decB);
        }
        if (valA instanceof Date dateA && valB instanceof Date dateB) {
            return Long.compare(dateA.getTime(), dateB.getTime());
        }
        if (valA instanceof Number numA && valB instanceof Number numB) {
            return this.compareNumbers(numA, numB);
        }
        if (valA instanceof Comparable comparableA && valB instanceof Comparable) {
            return comparableA.compareTo(valB);
        }
        throw new IllegalArgumentException(
            "Unsupported or incompatible column values for orderBy: "
                + valA.getClass().getName() + " and " + valB.getClass().getName()
        );
    }

    private Object coerceToDeclaredType(Object value, Class<?> declaredType) {
        if (value == null || declaredType == null || declaredType == String.class || declaredType.isInstance(value)) {
            return value;
        }
        if (value instanceof String str) {
            if (declaredType == Integer.class) return Integer.valueOf(str);
            if (declaredType == Long.class) return Long.valueOf(str);
            if (declaredType == Double.class) return Double.valueOf(str);
            if (declaredType == Float.class) return Float.valueOf(str);
            if (declaredType == Short.class) return Short.valueOf(str);
            if (declaredType == Byte.class) return Byte.valueOf(str);
            if (declaredType == Boolean.class) return Boolean.valueOf(str);
            if (declaredType == BigDecimal.class) return new BigDecimal(str);
            if (declaredType == Instant.class) return Instant.parse(str);
            if (declaredType == LocalDate.class) return LocalDate.parse(str);
            if (declaredType == LocalDateTime.class) return LocalDateTime.parse(str);
        }
        return value;
    }

    private int compareNumbers(Number numA, Number numB) {
        if (numA instanceof Double || numB instanceof Double || numA instanceof Float || numB instanceof Float) {
            double dA = numA.doubleValue();
            double dB = numB.doubleValue();
            if (Double.isNaN(dA) || Double.isNaN(dB) || Double.isInfinite(dA) || Double.isInfinite(dB)) {
                return Double.compare(dA, dB);
            }
        }
        return new BigDecimal(numA.toString()).compareTo(new BigDecimal(numB.toString()));
    }

}
