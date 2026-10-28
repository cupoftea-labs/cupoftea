package com.cupoftea.importers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.ImportException;
import com.cupoftea.importers.interfaces.IDtoImporter;
import com.cupoftea.table.Column;
import com.cupoftea.table.interfaces.IDataFrame;

public class DtoImporter implements IDtoImporter {

    private final CupOfTea cupOfTea;

    public DtoImporter(CupOfTea cupOfTea) {
        this.cupOfTea = cupOfTea;
    }

    @Override
    public <T> void importFromDtos(Collection<T> dtos) {
        if (dtos == null) {
            throw new ImportException("DTO collection cannot be null");
        }
        if (dtos.isEmpty()) {
            return;
        }

        T firstNonNull = null;
        for (T item : dtos) {
            if (item != null) {
                firstNonNull = item;
                break;
            }
        }

        if (firstNonNull == null) {
            return;
        }

        Class<?> clazz = firstNonNull.getClass();
        if (clazz.isRecord()) {
            importRecordCollection(dtos, clazz);
        } else {
            importPojoCollection(dtos, clazz);
        }
    }

    private <T> void importRecordCollection(Collection<T> records, Class<?> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();
        IDataFrame df = this.cupOfTea.getDataFrame();

        if (df.getHeaders().isEmpty()) {
            for (RecordComponent comp : components) {
                df.addColumn(comp.getName(), comp.getType());
            }
        }

        for (T record : records) {
            if (record == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (RecordComponent comp : components) {
                try {
                    Method accessor = comp.getAccessor();
                    accessor.setAccessible(true);
                    Object val = accessor.invoke(record);
                    row.put(comp.getName(), val);
                } catch (Exception e) {
                    throw new ImportException("Failed to read record component '" + comp.getName() + "' from " + clazz.getName(), e);
                }
            }
            df.addRow(row);
        }
    }

    private <T> void importPojoCollection(Collection<T> dtos, Class<?> clazz) {
        Map<String, PropertyAccessor> accessors = inspectProperties(clazz);
        IDataFrame df = this.cupOfTea.getDataFrame();

        if (df.getHeaders().isEmpty()) {
            for (Map.Entry<String, PropertyAccessor> entry : accessors.entrySet()) {
                df.addColumn(entry.getKey(), entry.getValue().getType());
            }
        }

        for (T dto : dtos) {
            if (dto == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, PropertyAccessor> entry : accessors.entrySet()) {
                try {
                    Object val = entry.getValue().getValue(dto);
                    row.put(entry.getKey(), val);
                } catch (Exception e) {
                    throw new ImportException("Failed to read property '" + entry.getKey() + "' from " + clazz.getName(), e);
                }
            }
            df.addRow(row);
        }
    }

    static Map<String, PropertyAccessor> inspectProperties(Class<?> clazz) {
        Map<String, PropertyAccessor> result = new LinkedHashMap<>();
        Map<String, Method> getters = new HashMap<>();

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType().equals(void.class)) {
                continue;
            }
            if (method.getDeclaringClass().equals(Object.class)) {
                continue;
            }
            String name = method.getName();
            String propName = null;
            if (name.startsWith("get") && name.length() > 3) {
                propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2 && (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class))) {
                propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }
            if (propName != null) {
                getters.put(propName, method);
            }
        }

        // Collect fields from class hierarchy
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

        // Match getters and fields
        for (Field field : fields) {
            String propName = field.getName();
            Method getter = getters.get(propName);
            result.put(propName, new PropertyAccessor(propName, field.getType(), getter, field));
        }

        // Add any getter-only properties
        for (Map.Entry<String, Method> entry : getters.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), new PropertyAccessor(entry.getKey(), entry.getValue().getReturnType(), entry.getValue(), null));
            }
        }

        return result;
    }

    static class PropertyAccessor {
        private final String name;
        private final Class<?> type;
        private final Method getter;
        private final Field field;

        public PropertyAccessor(String name, Class<?> type, Method getter, Field field) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.field = field;
            if (field != null) {
                try {
                    field.setAccessible(true);
                } catch (Exception ignored) {
                }
            }
            if (getter != null) {
                try {
                    getter.setAccessible(true);
                } catch (Exception ignored) {
                }
            }
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return type;
        }

        public Object getValue(Object target) throws Exception {
            if (getter != null) {
                return getter.invoke(target);
            } else if (field != null) {
                return field.get(target);
            }
            return null;
        }
    }
}
