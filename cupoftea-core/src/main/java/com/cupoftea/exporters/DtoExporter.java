package com.cupoftea.exporters;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ConversionException;
import com.cupoftea.exceptions.ExportException;
import com.cupoftea.exporters.interfaces.IDtoExporter;
import com.cupoftea.table.DataFrame;
import com.cupoftea.table.NA;
import com.cupoftea.table.Row;
import com.cupoftea.table.interfaces.IDataTypeConverter;

public class DtoExporter implements IDtoExporter {

    private final CupOfTea cupOfTea;

    public DtoExporter(CupOfTea cupOfTea) {
        this.cupOfTea = cupOfTea;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> exportToDtos(Class<T> dtoClass) {
        if (dtoClass == null) {
            throw new ExportException("Target DTO class cannot be null");
        }

        if (dtoClass.isRecord()) {
            return (List<T>) exportToRecordType((Class<? extends Record>) dtoClass);
        } else {
            return exportToPojoType(dtoClass);
        }
    }

    private <T extends Record> List<T> exportToRecordType(Class<T> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
        }

        Constructor<T> canonicalConstructor;
        try {
            canonicalConstructor = recordClass.getDeclaredConstructor(paramTypes);
            canonicalConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExportException("Canonical constructor not found for record " + recordClass.getName(), e);
        }

        List<T> result = new ArrayList<>();
        List<Row> rows = this.cupOfTea.getDataFrame().getRows();

        for (Row row : rows) {
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                String name = components[i].getName();
                Class<?> targetType = components[i].getType();
                Object raw = row.get(name);
                args[i] = convertValue(raw, targetType);
            }
            try {
                T instance = canonicalConstructor.newInstance(args);
                result.add(instance);
            } catch (Exception e) {
                throw new ExportException("Failed to instantiate record " + recordClass.getName(), e);
            }
        }
        return result;
    }

    private <T> List<T> exportToPojoType(Class<T> pojoClass) {
        Constructor<T> noArgConstructor;
        try {
            noArgConstructor = pojoClass.getDeclaredConstructor();
            noArgConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExportException("No default no-argument constructor found for " + pojoClass.getName(), e);
        }

        Map<String, PropertyWriter> writers = inspectWriters(pojoClass);
        List<T> result = new ArrayList<>();
        List<Row> rows = this.cupOfTea.getDataFrame().getRows();

        for (Row row : rows) {
            T instance;
            try {
                instance = noArgConstructor.newInstance();
            } catch (Exception e) {
                throw new ExportException("Failed to instantiate POJO " + pojoClass.getName(), e);
            }

            for (Map.Entry<String, PropertyWriter> entry : writers.entrySet()) {
                String propName = entry.getKey();
                if (row.containsKey(propName)) {
                    Object raw = row.get(propName);
                    PropertyWriter pw = entry.getValue();
                    Object converted = convertValue(raw, pw.getType());
                    try {
                        pw.setValue(instance, converted);
                    } catch (Exception e) {
                        throw new ExportException("Failed to set property '" + propName + "' on " + pojoClass.getName(), e);
                    }
                }
            }
            result.add(instance);
        }
        return result;
    }

    static Object convertValue(Object raw, Class<?> targetType) {
        if (raw == null || NA.isNA(raw)) {
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) return false;
                if (targetType == byte.class) return (byte) 0;
                if (targetType == short.class) return (short) 0;
                if (targetType == int.class) return 0;
                if (targetType == long.class) return 0L;
                if (targetType == float.class) return 0.0f;
                if (targetType == double.class) return 0.0d;
                if (targetType == char.class) return '\0';
            }
            return null;
        }

        if (targetType.isInstance(raw)) {
            return raw;
        }

        IDataTypeConverter<?> converter = DataFrame.CONVERTERS.get(targetType);
        if (converter != null) {
            try {
                return converter.convert(raw, raw.getClass());
            } catch (ConversionException e) {
                throw new ExportException("Cannot convert value '" + raw + "' of type " + raw.getClass().getName() + " to " + targetType.getName(), e);
            }
        }

        // Handle primitive conversions or fallback
        if (targetType == int.class || targetType == Integer.class) {
            if (raw instanceof Number) return ((Number) raw).intValue();
            return Integer.valueOf(raw.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            if (raw instanceof Number) return ((Number) raw).doubleValue();
            return Double.valueOf(raw.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (raw instanceof Number) return ((Number) raw).longValue();
            return Long.valueOf(raw.toString());
        }
        if (targetType == float.class || targetType == Float.class) {
            if (raw instanceof Number) return ((Number) raw).floatValue();
            return Float.valueOf(raw.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.valueOf(raw.toString());
        }
        if (targetType == String.class) {
            return raw.toString();
        }

        return raw;
    }

    static Map<String, PropertyWriter> inspectWriters(Class<?> clazz) {
        Map<String, PropertyWriter> result = new HashMap<>();
        Map<String, Method> setters = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }
            String name = method.getName();
            if (name.startsWith("set") && name.length() > 3) {
                String propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                setters.put(propName, method);
            }
        }

        Class<?> current = clazz;
        List<Field> fields = new ArrayList<>();
        while (current != null && !current.equals(Object.class)) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }

        for (Field field : fields) {
            String propName = field.getName();
            Method setter = setters.get(propName);
            result.put(propName, new PropertyWriter(propName, field.getType(), setter, field));
        }

        for (Map.Entry<String, Method> entry : setters.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                Class<?> paramType = entry.getValue().getParameterTypes()[0];
                result.put(entry.getKey(), new PropertyWriter(entry.getKey(), paramType, entry.getValue(), null));
            }
        }

        return result;
    }

    static class PropertyWriter {
        private final String name;
        private final Class<?> type;
        private final Method setter;
        private final Field field;

        public PropertyWriter(String name, Class<?> type, Method setter, Field field) {
            this.name = name;
            this.type = type;
            this.setter = setter;
            this.field = field;
            if (setter != null) {
                try {
                    setter.setAccessible(true);
                } catch (Exception ignored) {}
            }
            if (field != null) {
                try {
                    field.setAccessible(true);
                } catch (Exception ignored) {}
            }
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return type;
        }

        public void setValue(Object target, Object value) throws Exception {
            if (setter != null) {
                setter.invoke(target, value);
            } else if (field != null) {
                field.set(target, value);
            }
        }
    }
}
