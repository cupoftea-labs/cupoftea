package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.interfaces.IDataFrame;

public class ConcatTest {

    private CupOfTea createEmployeesA() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("dept_id", Integer.class);

        df.addRow(Map.of("id", 1, "name", "Alice", "dept_id", 10));
        df.addRow(Map.of("id", 2, "name", "Bob", "dept_id", 20));
        return df;
    }

    private CupOfTea createEmployeesB() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("dept_id", Integer.class);

        df.addRow(Map.of("id", 3, "name", "Charlie", "dept_id", 10));
        df.addRow(Map.of("id", 4, "name", "Diana", "dept_id", 30));
        return df;
    }

    // --- Vertical Concatenation (axis = 0 / ROWS) ---

    @Test
    @DisplayName("concat two DataFrames with identical schemas vertically")
    void test_concatVertical_identicalSchemas() {
        CupOfTea a = createEmployeesA();
        CupOfTea b = createEmployeesB();

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name", "dept_id");

        assertThat(result.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(result.getRow(1).getInt("id")).isEqualTo(2);
        assertThat(result.getRow(2).getInt("id")).isEqualTo(3);
        assertThat(result.getRow(3).getString("name")).isEqualTo("Diana");
        assertThat(result.getRow(3).getInt("dept_id")).isEqualTo(30);

        // Inputs remain untouched
        assertThat(a.size()).isEqualTo(2);
        assertThat(b.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("concat with different column order aligns by column name")
    void test_concatVertical_differentColumnOrder() {
        CupOfTea a = new CupOfTea();
        a.addColumn("id", Integer.class).addColumn("name", String.class);
        a.addRow(Map.of("id", 1, "name", "Alice"));

        CupOfTea b = new CupOfTea();
        b.addColumn("name", String.class).addColumn("id", Integer.class);
        b.addRow(Map.of("name", "Bob", "id", 2));

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name");

        assertThat(result.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(result.getRow(1).getInt("id")).isEqualTo(2);
        assertThat(result.getRow(1).getString("name")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("outer concat with disjoint/overlapping columns pads with nulls")
    void test_concatVertical_outerJoinDisjointColumns() {
        CupOfTea a = new CupOfTea();
        a.addColumn("id", Integer.class).addColumn("name", String.class);
        a.addRow(Map.of("id", 1, "name", "Alice"));

        CupOfTea b = new CupOfTea();
        b.addColumn("id", Integer.class).addColumn("age", Integer.class);
        b.addRow(Map.of("id", 2, "age", 25));

        CupOfTea result = a.concat(b, JoinType.OUTER);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name", "age");

        // Row 0 from a: age is null
        assertThat(result.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(result.getRow(0).get("age")).isNull();

        // Row 1 from b: name is null
        assertThat(result.getRow(1).getInt("id")).isEqualTo(2);
        assertThat(result.getRow(1).get("name")).isNull();
        assertThat(result.getRow(1).getInt("age")).isEqualTo(25);
    }

    @Test
    @DisplayName("inner concat retains only columns present in all DataFrames")
    void test_concatVertical_innerJoin() {
        CupOfTea a = new CupOfTea();
        a.addColumn("id", Integer.class).addColumn("name", String.class).addColumn("city", String.class);
        a.addRow(Map.of("id", 1, "name", "Alice", "city", "NY"));

        CupOfTea b = new CupOfTea();
        b.addColumn("id", Integer.class).addColumn("age", Integer.class).addColumn("name", String.class);
        b.addRow(Map.of("id", 2, "age", 30, "name", "Bob"));

        CupOfTea result = a.concat(b, JoinType.INNER);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name");

        assertThat(result.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(result.getRow(1).getInt("id")).isEqualTo(2);
        assertThat(result.getRow(1).getString("name")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("numeric type widening: Integer and Double promote to Double")
    void test_concatVertical_numericWidening_intAndDouble() {
        CupOfTea a = new CupOfTea();
        a.addColumn("val", Integer.class);
        a.addRow(Map.of("val", 10));

        CupOfTea b = new CupOfTea();
        b.addColumn("val", Double.class);
        b.addRow(Map.of("val", 20.5));

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getColumnHeader("val").columnType()).isEqualTo(Double.class);
        assertThat(result.getRow(0).getDouble("val")).isEqualTo(10.0);
        assertThat(result.getRow(1).getDouble("val")).isEqualTo(20.5);
    }

    @Test
    @DisplayName("numeric type widening: Integer and Long promote to Long")
    void test_concatVertical_numericWidening_intAndLong() {
        CupOfTea a = new CupOfTea();
        a.addColumn("val", Integer.class);
        a.addRow(Map.of("val", 100));

        CupOfTea b = new CupOfTea();
        b.addColumn("val", Long.class);
        b.addRow(Map.of("val", 5000000000L));

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getColumnHeader("val").columnType()).isEqualTo(Long.class);
        assertThat(result.getRow(0).getLong("val")).isEqualTo(100L);
        assertThat(result.getRow(1).getLong("val")).isEqualTo(5000000000L);
    }

    @Test
    @DisplayName("numeric type widening: Number and BigDecimal promote to BigDecimal")
    void test_concatVertical_numericWidening_bigDecimal() {
        CupOfTea a = new CupOfTea();
        a.addColumn("amount", Double.class);
        a.addRow(Map.of("amount", 123.45));

        CupOfTea b = new CupOfTea();
        b.addColumn("amount", BigDecimal.class);
        b.addRow(Map.of("amount", new BigDecimal("678.90")));

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getColumnHeader("amount").columnType()).isEqualTo(BigDecimal.class);
        assertThat(result.getRow(0).getBigDecimal("amount")).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(result.getRow(1).getBigDecimal("amount")).isEqualByComparingTo(new BigDecimal("678.90"));
    }

    @Test
    @DisplayName("non-numeric incompatible types fall back to Object")
    void test_concatVertical_fallbackToObject() {
        CupOfTea a = new CupOfTea();
        a.addColumn("mixed", String.class);
        a.addRow(Map.of("mixed", "hello"));

        CupOfTea b = new CupOfTea();
        b.addColumn("mixed", Integer.class);
        b.addRow(Map.of("mixed", 42));

        CupOfTea result = a.concat(b);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getColumnHeader("mixed").columnType()).isEqualTo(Object.class);
        assertThat(result.getRow(0).get("mixed")).isEqualTo("hello");
        assertThat(result.getRow(1).get("mixed")).isEqualTo(42);
    }

    // --- Empty Frames & Single Frame ---

    @Test
    @DisplayName("concat with empty DataFrames")
    void test_concatVertical_emptyFrames() {
        CupOfTea empty = new CupOfTea();
        empty.addColumn("id", Integer.class).addColumn("name", String.class);

        CupOfTea full = createEmployeesA();

        // empty + full
        CupOfTea res1 = empty.concat(full);
        assertThat(res1.size()).isEqualTo(2);
        assertThat(res1.getRow(0).getString("name")).isEqualTo("Alice");

        // full + empty
        CupOfTea res2 = full.concat(empty);
        assertThat(res2.size()).isEqualTo(2);
        assertThat(res2.getRow(0).getString("name")).isEqualTo("Alice");

        // empty + empty
        CupOfTea res3 = empty.concat(empty);
        assertThat(res3.size()).isEqualTo(0);
        assertThat(res3.getHeaders()).hasSize(2);
    }

    @Test
    @DisplayName("static concat with single DataFrame returns copy")
    void test_concat_singleFrameReturnsCopy() {
        CupOfTea original = createEmployeesA();
        CupOfTea result = CupOfTea.concat(List.of(original));

        assertThat(result).isNotSameAs(original);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
    }

    // --- Multiple DataFrames (varargs and list) ---

    @Test
    @DisplayName("static concat with varargs and lists of 3+ DataFrames")
    void test_concat_multipleFrames() {
        CupOfTea a = createEmployeesA();
        CupOfTea b = createEmployeesB();

        CupOfTea c = new CupOfTea();
        c.addColumn("id", Integer.class).addColumn("name", String.class).addColumn("dept_id", Integer.class);
        c.addRow(Map.of("id", 5, "name", "Eve", "dept_id", 40));

        // Static CupOfTea.concat
        CupOfTea multi1 = CupOfTea.concat(a, b, c);
        assertThat(multi1.size()).isEqualTo(5);
        assertThat(multi1.getRow(4).getString("name")).isEqualTo("Eve");

        // Static List CupOfTea.concat
        CupOfTea multi2 = CupOfTea.concat(List.of(a, b, c));
        assertThat(multi2.size()).isEqualTo(5);

        // Instance concatWith
        CupOfTea multi3 = a.concatWith(b, c);
        assertThat(multi3.size()).isEqualTo(5);
    }

    // --- Horizontal Concatenation (axis = 1 / COLUMNS) ---

    @Test
    @DisplayName("concat horizontally with no column name collisions")
    void test_concatHorizontal_noCollisions() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("name", String.class);
        left.addRow(Map.of("id", 1, "name", "Alice"));
        left.addRow(Map.of("id", 2, "name", "Bob"));

        CupOfTea right = new CupOfTea();
        right.addColumn("salary", Double.class).addColumn("dept", String.class);
        right.addRow(Map.of("salary", 50000.0, "dept", "HR"));
        right.addRow(Map.of("salary", 60000.0, "dept", "IT"));

        CupOfTea result = left.concat(right, ConcatAxis.COLUMNS);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name", "salary", "dept");

        assertThat(result.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(result.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(result.getRow(0).getDouble("salary")).isEqualTo(50000.0);
        assertThat(result.getRow(0).getString("dept")).isEqualTo("HR");
    }

    @Test
    @DisplayName("concat horizontally with column collisions applies disambiguation suffixes")
    void test_concatHorizontal_columnCollisionsWithSuffixes() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("val", String.class);
        left.addRow(Map.of("id", 1, "val", "v1"));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Integer.class).addColumn("val", String.class);
        right.addRow(Map.of("id", 2, "val", "v2"));

        CupOfTea result = left.concatHorizontal(right, "_left", "_right");

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getHeaders()).extracting(Column::columnName)
            .containsExactly("id_left", "val_left", "id_right", "val_right");

        assertThat(result.getRow(0).getInt("id_left")).isEqualTo(1);
        assertThat(result.getRow(0).getString("val_left")).isEqualTo("v1");
        assertThat(result.getRow(0).getInt("id_right")).isEqualTo(2);
        assertThat(result.getRow(0).getString("val_right")).isEqualTo("v2");
    }

    @Test
    @DisplayName("concat horizontally with unequal rows outer join pads shorter frame with nulls")
    void test_concatHorizontal_unequalRows_outer() {
        CupOfTea left = new CupOfTea();
        left.addColumn("colA", String.class);
        left.addRow(Map.of("colA", "a1"));
        left.addRow(Map.of("colA", "a2"));
        left.addRow(Map.of("colA", "a3"));

        CupOfTea right = new CupOfTea();
        right.addColumn("colB", Integer.class);
        right.addRow(Map.of("colB", 100));

        CupOfTea result = left.concat(right, 1, JoinType.OUTER);

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.getRow(0).getString("colA")).isEqualTo("a1");
        assertThat(result.getRow(0).getInt("colB")).isEqualTo(100);

        assertThat(result.getRow(1).getString("colA")).isEqualTo("a2");
        assertThat(result.getRow(1).get("colB")).isNull();

        assertThat(result.getRow(2).getString("colA")).isEqualTo("a3");
        assertThat(result.getRow(2).get("colB")).isNull();
    }

    @Test
    @DisplayName("concat horizontally with unequal rows inner join truncates to minimum rows")
    void test_concatHorizontal_unequalRows_inner() {
        CupOfTea left = new CupOfTea();
        left.addColumn("colA", String.class);
        left.addRow(Map.of("colA", "a1"));
        left.addRow(Map.of("colA", "a2"));
        left.addRow(Map.of("colA", "a3"));

        CupOfTea right = new CupOfTea();
        right.addColumn("colB", Integer.class);
        right.addRow(Map.of("colB", 100));

        CupOfTea result = left.concat(right, 1, JoinType.INNER);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getRow(0).getString("colA")).isEqualTo("a1");
        assertThat(result.getRow(0).getInt("colB")).isEqualTo(100);
    }

    // --- Index Preservation & ignoreIndex ---

    @Test
    @DisplayName("concat preserves index column when identical across frames")
    void test_concat_preservesIndex() {
        CupOfTea a = createEmployeesA().setIndex("id");
        CupOfTea b = createEmployeesB().setIndex("id");

        CupOfTea result = a.concat(b);

        assertThat(result.getIndex()).isEqualTo("id");
        assertThat(result.loc(1).getString("name")).isEqualTo("Alice");
        assertThat(result.loc(4).getString("name")).isEqualTo("Diana");
    }

    @Test
    @DisplayName("concat resets index when ignoreIndex is true")
    void test_concat_ignoreIndex() {
        CupOfTea a = createEmployeesA().setIndex("id");
        CupOfTea b = createEmployeesB().setIndex("id");

        CupOfTea result = a.concat(b, true);

        assertThat(result.getIndex()).isNull();
    }

    @Test
    @DisplayName("concat resets index when frames have conflicting index columns")
    void test_concat_conflictingIndexResets() {
        CupOfTea a = createEmployeesA().setIndex("id");
        CupOfTea b = createEmployeesB().setIndex("name");

        CupOfTea result = a.concat(b);

        assertThat(result.getIndex()).isNull();
    }

    // --- MutationMode Preservation ---

    @Test
    @DisplayName("concat preserves caller's MutationMode and does not mutate inputs")
    void test_concat_preservesMutationMode() {
        CupOfTea a = createEmployeesA().asCopyOnWrite();
        CupOfTea b = createEmployeesB();

        CupOfTea result = a.concat(b);

        assertThat(result.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);
        assertThat(a.size()).isEqualTo(2);
        assertThat(b.size()).isEqualTo(2);

        CupOfTea inPlaceA = createEmployeesA().asInPlace();
        CupOfTea inPlaceResult = inPlaceA.concat(b);

        assertThat(inPlaceResult.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
        assertThat(inPlaceA.size()).isEqualTo(2);
    }

    // --- Static API & Axis Parsing ---

    @Test
    @DisplayName("ConcatAxis parses string and numeric axis correctly")
    void test_concatAxis_parsing() {
        assertThat(ConcatAxis.fromValue(0)).isEqualTo(ConcatAxis.ROWS);
        assertThat(ConcatAxis.fromValue(1)).isEqualTo(ConcatAxis.COLUMNS);
        assertThatThrownBy(() -> ConcatAxis.fromValue(2))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(ConcatAxis.fromString("rows")).isEqualTo(ConcatAxis.ROWS);
        assertThat(ConcatAxis.fromString("0")).isEqualTo(ConcatAxis.ROWS);
        assertThat(ConcatAxis.fromString("vertical")).isEqualTo(ConcatAxis.ROWS);
        assertThat(ConcatAxis.fromString("columns")).isEqualTo(ConcatAxis.COLUMNS);
        assertThat(ConcatAxis.fromString("1")).isEqualTo(ConcatAxis.COLUMNS);
        assertThat(ConcatAxis.fromString("horizontal")).isEqualTo(ConcatAxis.COLUMNS);
        assertThatThrownBy(() -> ConcatAxis.fromString("diagonal"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Validation and Error Handling ---

    @Test
    @DisplayName("validation: null or empty frames throw IllegalArgumentException")
    void test_validation_nullOrEmpty() {
        assertThatThrownBy(() -> CupOfTea.concat((List<CupOfTea>) null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> CupOfTea.concat(List.of()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> createEmployeesA().concat((CupOfTea) null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> CupOfTea.concat(createEmployeesA(), (CupOfTea) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IDataFrame concat interface methods match CupOfTea methods")
    void test_IDataFrame_concat() {
        IDataFrame a = createEmployeesA().getDataFrame();
        IDataFrame b = createEmployeesB().getDataFrame();

        IDataFrame res = a.concat(b);
        assertThat(res.size()).isEqualTo(4);
        assertThat(res.getRow(3).getString("name")).isEqualTo("Diana");

        IDataFrame staticRes = IDataFrame.concat(a, b);
        assertThat(staticRes.size()).isEqualTo(4);
    }
}
