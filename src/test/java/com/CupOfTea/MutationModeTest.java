package com.CupOfTea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.table.Column;
import com.CupOfTea.utils.interfaces.Order;
import com.CupOfTea.table.Row;
import com.CupOfTea.utils.interfaces.Orderer;

public class MutationModeTest {

    private CupOfTea createSampleFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("salary", Double.class);

        HashMap<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "Alice");
        r1.put("salary", 50000.0);
        df.addRow(r1);

        HashMap<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "Bob");
        r2.put("salary", 60000.0);
        df.addRow(r2);

        return df;
    }

    @Test
    @DisplayName("Default mutation mode must be IN_PLACE")
    void test_defaultMode_isInPlace() {
        CupOfTea df = new CupOfTea();
        assertThat(df.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
    }

    @Test
    @DisplayName("COPY_ON_WRITE mode: column operations return new instances and leave original untouched")
    void test_copyOnWrite_columnOperations() {
        CupOfTea original = createSampleFrame().asCopyOnWrite();
        int initialHeaders = original.getHeaders().size();

        // addColumn
        CupOfTea withCol = original.addColumn("bonus", Double.class);
        assertThat(withCol).isNotSameAs(original);
        assertThat(original.getHeaders()).hasSize(initialHeaders);
        assertThat(withCol.getHeaders()).hasSize(initialHeaders + 1);
        assertThat(original.getColumnHeader("bonus")).isNull();
        assertThat(withCol.getColumnHeader("bonus")).isNotNull();

        // renameColumn
        CupOfTea renamed = original.renameColumn("name", "fullName");
        assertThat(renamed).isNotSameAs(original);
        assertThat(original.getColumnHeader("name")).isNotNull();
        assertThat(original.getColumnHeader("fullName")).isNull();
        assertThat(renamed.getColumnHeader("name")).isNull();
        assertThat(renamed.getColumnHeader("fullName")).isNotNull();

        // removeColumn
        CupOfTea removed = original.removeColumn("salary");
        assertThat(removed).isNotSameAs(original);
        assertThat(original.getColumnHeader("salary")).isNotNull();
        assertThat(removed.getColumnHeader("salary")).isNull();

        // alterColumnType
        CupOfTea altered = original.alterColumnType("id", Long.class);
        assertThat(altered).isNotSameAs(original);
        assertThat(original.getColumnHeader("id").columnType()).isEqualTo(Integer.class);
        assertThat(altered.getColumnHeader("id").columnType()).isEqualTo(Long.class);
    }

    @Test
    @DisplayName("COPY_ON_WRITE mode: row operations return new instances and leave original untouched")
    void test_copyOnWrite_rowOperations() {
        CupOfTea original = createSampleFrame().asCopyOnWrite();
        int initialSize = original.size();

        // addRow
        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("name", "Charlie");
        r3.put("salary", 70000.0);
        CupOfTea added = original.addRow(r3);

        assertThat(added).isNotSameAs(original);
        assertThat(original.size()).isEqualTo(initialSize);
        assertThat(added.size()).isEqualTo(initialSize + 1);

        // removeRow
        CupOfTea removed = original.removeRow(0);
        assertThat(removed).isNotSameAs(original);
        assertThat(original.size()).isEqualTo(initialSize);
        assertThat(removed.size()).isEqualTo(initialSize - 1);
        assertThat(removed.getRow(0).getString("name")).isEqualTo("Bob");

        // removeRows by filter
        CupOfTea filteredRemoved = original.removeRows(r -> r.getInt("id") == 1);
        assertThat(filteredRemoved).isNotSameAs(original);
        assertThat(original.size()).isEqualTo(initialSize);
        assertThat(filteredRemoved.size()).isEqualTo(1);
        assertThat(filteredRemoved.getRow(0).getString("name")).isEqualTo("Bob");

        // update
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("name", "UpdatedName");
        CupOfTea updated = original.update(updates);
        assertThat(updated).isNotSameAs(original);
        assertThat(original.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(updated.getRow(0).getString("name")).isEqualTo("UpdatedName");

        // orderBy
        CupOfTea ordered = original.orderBy(new Orderer("id", Order.DESCENDING));
        assertThat(ordered).isNotSameAs(original);
        assertThat(original.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(ordered.getRow(0).getInt("id")).isEqualTo(2);
    }

    @Test
    @DisplayName("IN_PLACE mode: operations modify the instance in place and return this")
    void test_inPlace_operationsModifyTargetInPlace() {
        CupOfTea df = createSampleFrame();
        assertThat(df.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);

        // addColumn
        CupOfTea afterAddCol = df.addColumn("active", Boolean.class);
        assertThat(afterAddCol).isSameAs(df);
        assertThat(df.getColumnHeader("active")).isNotNull();

        // addRow
        HashMap<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("name", "Charlie");
        r3.put("salary", 75000.0);
        r3.put("active", true);
        CupOfTea afterAddRow = df.addRow(r3);
        assertThat(afterAddRow).isSameAs(df);
        assertThat(df.size()).isEqualTo(3);

        // renameColumn
        CupOfTea afterRename = df.renameColumn("name", "fullName");
        assertThat(afterRename).isSameAs(df);
        assertThat(df.getColumnHeader("fullName")).isNotNull();
        assertThat(df.getColumnHeader("name")).isNull();

        // removeRow
        CupOfTea afterRemove = df.removeRow(0);
        assertThat(afterRemove).isSameAs(df);
        assertThat(df.size()).isEqualTo(2);

        // orderBy
        CupOfTea afterOrder = df.orderBy(new Orderer("id", Order.DESCENDING));
        assertThat(afterOrder).isSameAs(df);
        assertThat(df.getRow(0).getInt("id")).isEqualTo(3);
    }

    @Test
    @DisplayName("copy() method provides deep copy independent of original")
    void test_deepCopy_independence() {
        CupOfTea df = createSampleFrame();
        CupOfTea copy = df.copy();

        assertThat(copy).isNotSameAs(df);
        assertThat(copy.size()).isEqualTo(df.size());
        assertThat(copy.getHeaders()).isEqualTo(df.getHeaders());
        assertThat(copy.getMutationMode()).isEqualTo(df.getMutationMode());

        // Mutating copy doesn't affect original
        copy.addColumn("extra", String.class);
        HashMap<String, Object> newRow = new HashMap<>();
        newRow.put("id", 99);
        newRow.put("name", "New");
        newRow.put("salary", 999.0);
        newRow.put("extra", "value");
        copy.addRow(newRow);

        assertThat(df.size()).isEqualTo(2);
        assertThat(df.getColumnHeader("extra")).isNull();
        assertThat(copy.size()).isEqualTo(3);
        assertThat(copy.getColumnHeader("extra")).isNotNull();

        // Mutating df doesn't affect copy
        df.removeColumn("salary");
        assertThat(copy.getColumnHeader("salary")).isNotNull();
        assertThat(df.getColumnHeader("salary")).isNull();
    }

    @Test
    @DisplayName("Encapsulation: getRows() and getRow() never leak mutable storage")
    void test_encapsulation_unmodifiableRowsAndList() {
        CupOfTea df = createSampleFrame();

        List<Row> rows = df.getRows();
        assertThatThrownBy(() -> rows.add(Row.of("id", 99)))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(rows::clear)
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> rows.remove(0))
            .isInstanceOf(UnsupportedOperationException.class);

        Row row0 = df.getRow(0);
        assertThatThrownBy(() -> row0.put("name", "Hacked"))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> row0.remove("name"))
            .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(row0::clear)
            .isInstanceOf(UnsupportedOperationException.class);

        // Modifying an external map added to DataFrame does not mutate DataFrame row
        HashMap<String, Object> mutableMap = new HashMap<>();
        mutableMap.put("id", 100);
        mutableMap.put("name", "OriginalName");
        mutableMap.put("salary", 80000.0);
        df.addRow(mutableMap);

        mutableMap.put("name", "MutatedExternally");
        assertThat(df.getRow(df.size() - 1).getString("name")).isEqualTo("OriginalName");
    }

    @Test
    @DisplayName("Filter and groupBy preserve parent mutation mode")
    void test_filterAndGroupBy_preserveMutationMode() {
        CupOfTea cowDf = createSampleFrame().asCopyOnWrite();
        CupOfTea filteredCow = cowDf.filter(r -> r.getInt("id") == 1);
        assertThat(filteredCow.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);

        CupOfTea inPlaceDf = createSampleFrame();
        CupOfTea filteredInPlace = inPlaceDf.filter(r -> r.getInt("id") == 1);
        assertThat(filteredInPlace.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);

        CupOfTea groupedResult = cowDf.groupBy("id").count("name");
        assertThat(groupedResult.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);
    }
}
