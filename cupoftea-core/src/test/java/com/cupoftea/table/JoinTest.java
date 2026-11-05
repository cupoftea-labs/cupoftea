package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.DictionaryColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.FloatColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class JoinTest {

    private CupOfTea createEmployees() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("dept_id", Integer.class);

        df.addRow(Map.of("id", 1, "name", "Alice", "dept_id", 10));
        df.addRow(Map.of("id", 2, "name", "Bob", "dept_id", 20));
        df.addRow(Map.of("id", 3, "name", "Charlie", "dept_id", 10));
        df.addRow(Map.of("id", 4, "name", "David", "dept_id", 99)); // dept 99 does not exist in depts

        return df;
    }

    private CupOfTea createDepartments() {
        CupOfTea df = new CupOfTea();
        df.addColumn("dept_id", Integer.class)
          .addColumn("dept_name", String.class)
          .addColumn("location", String.class);

        df.addRow(Map.of("dept_id", 10, "dept_name", "Engineering", "location", "NY"));
        df.addRow(Map.of("dept_id", 20, "dept_name", "HR", "location", "London"));
        df.addRow(Map.of("dept_id", 30, "dept_name", "Marketing", "location", "Paris")); // no employee in dept 30

        return df;
    }

    // --- Inner Join ---

    @Test
    @DisplayName("innerJoin on single common column")
    void test_innerJoin_singleColumn() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.innerJoin(depts, "dept_id");

        assertThat(joined.size()).isEqualTo(3);
        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name", "dept_id", "dept_name", "location");

        // Alice (dept 10)
        Row r0 = joined.getRow(0);
        assertThat(r0.getString("name")).isEqualTo("Alice");
        assertThat(r0.getString("dept_name")).isEqualTo("Engineering");

        // Bob (dept 20)
        Row r1 = joined.getRow(1);
        assertThat(r1.getString("name")).isEqualTo("Bob");
        assertThat(r1.getString("dept_name")).isEqualTo("HR");

        // Charlie (dept 10)
        Row r2 = joined.getRow(2);
        assertThat(r2.getString("name")).isEqualTo("Charlie");
        assertThat(r2.getString("dept_name")).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("natural join (join without on) infers common column")
    void test_naturalJoin_inferredCommonColumn() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        // Default join() is INNER on common column ("dept_id")
        CupOfTea joined = emps.join(depts);

        assertThat(joined.size()).isEqualTo(3);
        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name", "dept_id", "dept_name", "location");
    }

    @Test
    @DisplayName("innerJoin with multi-column keys")
    void test_innerJoin_multiColumnKeys() {
        CupOfTea left = new CupOfTea();
        left.addColumn("c1", String.class)
            .addColumn("c2", Integer.class)
            .addColumn("val_left", String.class);
        left.addRow(Map.of("c1", "A", "c2", 1, "val_left", "L1"));
        left.addRow(Map.of("c1", "A", "c2", 2, "val_left", "L2"));
        left.addRow(Map.of("c1", "B", "c2", 1, "val_left", "L3"));

        CupOfTea right = new CupOfTea();
        right.addColumn("c1", String.class)
             .addColumn("c2", Integer.class)
             .addColumn("val_right", String.class);
        right.addRow(Map.of("c1", "A", "c2", 1, "val_right", "R1"));
        right.addRow(Map.of("c1", "B", "c2", 2, "val_right", "R2"));

        CupOfTea joined = left.innerJoin(right, List.of("c1", "c2"));

        assertThat(joined.size()).isEqualTo(1);
        Row r = joined.getRow(0);
        assertThat(r.getString("c1")).isEqualTo("A");
        assertThat(r.getInt("c2")).isEqualTo(1);
        assertThat(r.getString("val_left")).isEqualTo("L1");
        assertThat(r.getString("val_right")).isEqualTo("R1");
    }

    @Test
    @DisplayName("innerJoin with one-to-many and many-to-many matches")
    void test_innerJoin_manyToMany() {
        CupOfTea left = new CupOfTea();
        left.addColumn("key", Integer.class).addColumn("l_val", String.class);
        left.addRow(Map.of("key", 1, "l_val", "L1"));
        left.addRow(Map.of("key", 1, "l_val", "L2"));

        CupOfTea right = new CupOfTea();
        right.addColumn("key", Integer.class).addColumn("r_val", String.class);
        right.addRow(Map.of("key", 1, "r_val", "R1"));
        right.addRow(Map.of("key", 1, "r_val", "R2"));
        right.addRow(Map.of("key", 1, "r_val", "R3"));

        CupOfTea joined = left.innerJoin(right, "key");

        // 2 * 3 = 6 rows
        assertThat(joined.size()).isEqualTo(6);
    }

    // --- Left Join ---

    @Test
    @DisplayName("leftJoin keeps all left rows and pads right columns with null")
    void test_leftJoin() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.leftJoin(depts, "dept_id");

        assertThat(joined.size()).isEqualTo(4);

        // David (dept 99) has no matching department
        Row david = joined.getRow(3);
        assertThat(david.getString("name")).isEqualTo("David");
        assertThat(david.getInt("dept_id")).isEqualTo(99);
        assertThat(david.get("dept_name")).isNull();
        assertThat(david.get("location")).isNull();
        assertThat(david.isNull("dept_name")).isTrue();
    }

    // --- Right Join ---

    @Test
    @DisplayName("rightJoin keeps all right rows and pads left columns with null")
    void test_rightJoin() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.rightJoin(depts, "dept_id");

        // Depts: 10 (Alice, Charlie), 20 (Bob), 30 (no emps) -> total 4 rows
        assertThat(joined.size()).isEqualTo(4);

        // Check dept 30 has right key populated and null employee columns
        Row dept30 = joined.getRow(3);
        assertThat(dept30.getInt("dept_id")).isEqualTo(30);
        assertThat(dept30.getString("dept_name")).isEqualTo("Marketing");
        assertThat(dept30.get("name")).isNull();
        assertThat(dept30.get("id")).isNull();
    }

    // --- Full Outer Join ---

    @Test
    @DisplayName("outerJoin keeps all rows from both sides")
    void test_outerJoin() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.outerJoin(depts, "dept_id");

        // Matched: Alice (10), Bob (20), Charlie (10)
        // Left-only: David (99)
        // Right-only: Marketing (30)
        // Total = 5 rows
        assertThat(joined.size()).isEqualTo(5);

        // David row
        Row david = joined.getRow(3);
        assertThat(david.getString("name")).isEqualTo("David");
        assertThat(david.getInt("dept_id")).isEqualTo(99);
        assertThat(david.get("dept_name")).isNull();

        // Marketing dept 30 row
        Row marketing = joined.getRow(4);
        assertThat(marketing.getInt("dept_id")).isEqualTo(30);
        assertThat(marketing.getString("dept_name")).isEqualTo("Marketing");
        assertThat(marketing.get("name")).isNull();
    }

    // --- Cross Join ---

    @Test
    @DisplayName("crossJoin produces Cartesian product")
    void test_crossJoin() {
        CupOfTea left = new CupOfTea();
        left.addColumn("color", String.class);
        left.addRow(Map.of("color", "Red"));
        left.addRow(Map.of("color", "Blue"));

        CupOfTea right = new CupOfTea();
        right.addColumn("size", String.class);
        right.addRow(Map.of("size", "S"));
        right.addRow(Map.of("size", "M"));
        right.addRow(Map.of("size", "L"));

        CupOfTea joined = left.crossJoin(right);

        assertThat(joined.size()).isEqualTo(6);
        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("color", "size");

        assertThat(joined.getRow(0).getString("color")).isEqualTo("Red");
        assertThat(joined.getRow(0).getString("size")).isEqualTo("S");
        assertThat(joined.getRow(5).getString("color")).isEqualTo("Blue");
        assertThat(joined.getRow(5).getString("size")).isEqualTo("L");
    }

    @Test
    @DisplayName("crossJoin with empty dataframe produces 0 rows")
    void test_crossJoin_empty() {
        CupOfTea left = new CupOfTea();
        left.addColumn("color", String.class);
        left.addRow(Map.of("color", "Red"));

        CupOfTea emptyRight = new CupOfTea();
        emptyRight.addColumn("size", String.class);

        CupOfTea joined = left.crossJoin(emptyRight);
        assertThat(joined.size()).isEqualTo(0);
        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("color", "size");
    }

    // --- Column Collisions & Suffixes ---

    @Test
    @DisplayName("Colliding column names are disambiguated with default suffixes")
    void test_columnCollision_defaultSuffixes() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class)
            .addColumn("score", Double.class);
        left.addRow(Map.of("id", 1, "score", 90.0));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Integer.class)
             .addColumn("score", Double.class);
        right.addRow(Map.of("id", 1, "score", 85.0));

        CupOfTea joined = left.innerJoin(right, "id");

        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "score_left", "score_right");

        Row r = joined.getRow(0);
        assertThat(r.getDouble("score_left")).isEqualTo(90.0);
        assertThat(r.getDouble("score_right")).isEqualTo(85.0);
    }

    @Test
    @DisplayName("Colliding column names use custom suffixes")
    void test_columnCollision_customSuffixes() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("value", String.class);
        left.addRow(Map.of("id", 1, "value", "leftVal"));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Integer.class).addColumn("value", String.class);
        right.addRow(Map.of("id", 1, "value", "rightVal"));

        CupOfTea joined = left.join(right, JoinType.INNER, List.of("id"), List.of("id"), "_x", "_y");

        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "value_x", "value_y");

        Row r = joined.getRow(0);
        assertThat(r.getString("value_x")).isEqualTo("leftVal");
        assertThat(r.getString("value_y")).isEqualTo("rightVal");
    }

    @Test
    @DisplayName("Empty leftSuffix keeps left name unchanged and renames right")
    void test_columnCollision_emptyLeftSuffix() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("desc", String.class);
        left.addRow(Map.of("id", 1, "desc", "original"));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Integer.class).addColumn("desc", String.class);
        right.addRow(Map.of("id", 1, "desc", "new"));

        CupOfTea joined = left.join(right, JoinType.INNER, List.of("id"), List.of("id"), "", "_right");

        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "desc", "desc_right");
    }

    @Test
    @DisplayName("Identical suffixes with collisions throw IllegalArgumentException")
    void test_columnCollision_duplicateThrows() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("val", String.class);
        left.addRow(Map.of("id", 1, "val", "A"));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Integer.class).addColumn("val", String.class);
        right.addRow(Map.of("id", 1, "val", "B"));

        assertThatThrownBy(() -> left.join(right, JoinType.INNER, List.of("id"), List.of("id"), "", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate column name");
    }

    // --- Different Key Column Names ---

    @Test
    @DisplayName("Joining on different column names (leftOn, rightOn) retains both key columns")
    void test_differentKeyNames() {
        CupOfTea employees = new CupOfTea();
        employees.addColumn("emp_id", Integer.class).addColumn("name", String.class);
        employees.addRow(Map.of("emp_id", 1, "name", "Alice"));
        employees.addRow(Map.of("emp_id", 2, "name", "Bob"));

        CupOfTea badges = new CupOfTea();
        badges.addColumn("badge_id", Integer.class).addColumn("code", String.class);
        badges.addRow(Map.of("badge_id", 1, "code", "B100"));

        CupOfTea joined = employees.innerJoin(badges, "emp_id", "badge_id");

        assertThat(joined.size()).isEqualTo(1);
        assertThat(joined.getHeaders()).extracting(Column::columnName)
            .containsExactly("emp_id", "name", "badge_id", "code");

        Row r = joined.getRow(0);
        assertThat(r.getInt("emp_id")).isEqualTo(1);
        assertThat(r.getInt("badge_id")).isEqualTo(1);
        assertThat(r.getString("code")).isEqualTo("B100");
    }

    // --- NA / Missing Values in Join Keys ---

    @Test
    @DisplayName("NA / null in join keys do not match each other")
    void test_naKeysDoNotMatch() {
        CupOfTea left = new CupOfTea();
        left.addColumn("key", Integer.class).addColumn("val", String.class);
        left.addRow(Map.of("val", "left_null")); // key is null

        CupOfTea right = new CupOfTea();
        right.addColumn("key", Integer.class).addColumn("val", String.class);
        right.addRow(Map.of("val", "right_null")); // key is null

        CupOfTea inner = left.innerJoin(right, "key");
        assertThat(inner.size()).isEqualTo(0);

        CupOfTea leftJoined = left.leftJoin(right, "key");
        assertThat(leftJoined.size()).isEqualTo(1);
        assertThat(leftJoined.getRow(0).get("val_right")).isNull();

        CupOfTea outer = left.outerJoin(right, "key");
        // Null in left emitted as unmatched left, null in right emitted as unmatched right -> 2 rows
        assertThat(outer.size()).isEqualTo(2);
    }

    // --- Numeric Type Equivalence in Keys ---

    @Test
    @DisplayName("Join key matching is numeric-type tolerant (Integer matches Long and Double 1.0)")
    void test_numericTypeEquivalence() {
        CupOfTea left = new CupOfTea();
        left.addColumn("id", Integer.class).addColumn("name", String.class);
        left.addRow(Map.of("id", 1, "name", "Alice"));
        left.addRow(Map.of("id", 2, "name", "Bob"));

        CupOfTea right = new CupOfTea();
        right.addColumn("id", Long.class).addColumn("salary", Double.class);
        right.addRow(Map.of("id", 1L, "salary", 50000.0));
        right.addRow(Map.of("id", 2L, "salary", 60000.0));

        CupOfTea joined = left.innerJoin(right, "id");
        assertThat(joined.size()).isEqualTo(2);
        assertThat(joined.getRow(0).getDouble("salary")).isEqualTo(50000.0);
        assertThat(joined.getRow(1).getDouble("salary")).isEqualTo(60000.0);
    }

    // --- MutationMode Preservation ---

    @Test
    @DisplayName("join preserves parent MutationMode")
    void test_mutationModePreserved() {
        CupOfTea emps = createEmployees().asCopyOnWrite();
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.join(depts);
        assertThat(joined.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);
        assertThat(emps.size()).isEqualTo(4); // original untouched

        CupOfTea inPlaceEmps = createEmployees();
        CupOfTea inPlaceJoined = inPlaceEmps.join(depts);
        assertThat(inPlaceJoined.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
        assertThat(inPlaceEmps.size()).isEqualTo(4); // original untouched
    }

    // --- Index Preservation ---

    @Test
    @DisplayName("join preserves index if set on left DataFrame")
    void test_indexPreserved() {
        CupOfTea emps = createEmployees().setIndex("id");
        CupOfTea depts = createDepartments();

        CupOfTea joined = emps.innerJoin(depts, "dept_id");
        assertThat(joined.getIndex()).isEqualTo("id");
        assertThat(joined.loc(1).getString("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("join on index columns when no common columns exist and both have index set")
    void test_joinOnIndexWhenNoCommonColumns() {
        CupOfTea left = new CupOfTea();
        left.addColumn("emp_no", Integer.class).addColumn("name", String.class);
        left.addRow(Map.of("emp_no", 1, "name", "Alice"));
        left.setIndex("emp_no");

        CupOfTea right = new CupOfTea();
        right.addColumn("dept_emp_no", Integer.class).addColumn("dept", String.class);
        right.addRow(Map.of("dept_emp_no", 1, "dept", "Engineering"));
        right.setIndex("dept_emp_no");

        CupOfTea joined = left.join(right);
        assertThat(joined.size()).isEqualTo(1);
        assertThat(joined.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(joined.getRow(0).getString("dept")).isEqualTo("Engineering");
    }

    // --- Error Conditions ---

    @Test
    @DisplayName("join with null right throws IllegalArgumentException")
    void test_nullRightThrows() {
        CupOfTea df = createEmployees();
        assertThatThrownBy(() -> df.join((CupOfTea) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("join with non-existent column in left throws SchemaMismatchException")
    void test_nonExistentLeftColumnThrows() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        assertThatThrownBy(() -> emps.join(depts, "nonExistent"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("nonExistent");
    }

    @Test
    @DisplayName("join with non-existent column in right throws SchemaMismatchException")
    void test_nonExistentRightColumnThrows() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        assertThatThrownBy(() -> emps.join(depts, "dept_id", "bad_col"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("bad_col");
    }

    @Test
    @DisplayName("join with mismatched key column counts throws IllegalArgumentException")
    void test_mismatchedKeyCountsThrows() {
        CupOfTea emps = createEmployees();
        CupOfTea depts = createDepartments();

        assertThatThrownBy(() -> emps.join(depts, JoinType.INNER, List.of("id", "dept_id"), List.of("dept_id")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same number of columns");
    }

    @Test
    @DisplayName("join with no common columns and no explicit key throws IllegalArgumentException")
    void test_noCommonColumnsThrows() {
        CupOfTea left = new CupOfTea();
        left.addColumn("a", Integer.class);

        CupOfTea right = new CupOfTea();
        right.addColumn("b", Integer.class);

        assertThatThrownBy(() -> left.join(right))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No common columns found");
    }

    // --- Direct DataFrame API ---

    @Test
    @DisplayName("Direct IDataFrame/DataFrame join methods")
    void test_directDataFrameJoin() {
        DataFrame left = new DataFrame();
        left.addColumn("id", Integer.class).addColumn("x", String.class);
        left.addRow(Map.of("id", 1, "x", "foo"));

        DataFrame right = new DataFrame();
        right.addColumn("id", Integer.class).addColumn("y", String.class);
        right.addRow(Map.of("id", 1, "y", "bar"));

        IDataFrame joined = left.join(right, "id");
        assertThat(joined.size()).isEqualTo(1);
        assertThat(joined.getRow(0).getString("x")).isEqualTo("foo");
        assertThat(joined.getRow(0).getString("y")).isEqualTo("bar");
    }

    // --- Columnar Vectorized Fast-Paths (Long, String UTF-8, Dictionary, All Vector Types, Parallel) ---

    @Test
    @DisplayName("Columnar join fast-path on LongColumnVector keys across INNER, LEFT, RIGHT, OUTER")
    void test_longColumnVectorJoin() {
        BitSet lNulls = new BitSet();
        lNulls.set(2);
        LongColumnVector lKey = new LongColumnVector("k", true, new long[]{100L, 200L, 0L, 300L}, lNulls, 4);
        IntColumnVector lVal = new IntColumnVector("l_val", false, new int[]{1, 2, 3, 4}, new BitSet(), 4);
        CupOfTea left = CupOfTea.fromVectors(List.of(lKey, lVal));

        BitSet rNulls = new BitSet();
        rNulls.set(1);
        LongColumnVector rKey = new LongColumnVector("k", true, new long[]{200L, 0L, 400L, 100L}, rNulls, 4);
        StringColumnVector rVal = new StringColumnVector("r_val", false, new String[]{"R200", "RNULL", "R400", "R100"}, new BitSet(), 4);
        CupOfTea right = CupOfTea.fromVectors(List.of(rKey, rVal));

        CupOfTea inner = left.innerJoin(right, "k");
        assertThat(inner.size()).isEqualTo(2);
        assertThat(inner.getRow(0).getLong("k")).isEqualTo(100L);
        assertThat(inner.getRow(0).getString("r_val")).isEqualTo("R100");
        assertThat(inner.getRow(1).getLong("k")).isEqualTo(200L);
        assertThat(inner.getRow(1).getString("r_val")).isEqualTo("R200");

        CupOfTea leftJoin = left.leftJoin(right, "k");
        assertThat(leftJoin.size()).isEqualTo(4);
        assertThat(leftJoin.getRow(2).isNull("k")).isTrue();
        assertThat(leftJoin.getRow(2).isNull("r_val")).isTrue();
        assertThat(leftJoin.getRow(3).getLong("k")).isEqualTo(300L);
        assertThat(leftJoin.getRow(3).isNull("r_val")).isTrue();

        CupOfTea rightJoin = left.rightJoin(right, "k");
        assertThat(rightJoin.size()).isEqualTo(4);

        CupOfTea outerJoin = left.outerJoin(right, "k");
        assertThat(outerJoin.size()).isEqualTo(6);
    }

    @Test
    @DisplayName("Columnar join fast-path on UTF-8 StringColumnVector with multi-byte characters, empty strings, and nulls")
    void test_utf8StringColumnVectorJoin() {
        BitSet lNulls = new BitSet();
        lNulls.set(3);
        StringColumnVector lKey = new StringColumnVector(
            "city", true, new String[]{"São Paulo", "", "Zürich", null, "東京"}, lNulls, 5);
        DoubleColumnVector lVal = new DoubleColumnVector(
            "pop", false, new double[]{12.3, 0.0, 0.4, 1.0, 14.0}, new BitSet(), 5);
        CupOfTea left = CupOfTea.fromVectors(List.of(lKey, lVal));

        BitSet rNulls = new BitSet();
        rNulls.set(2);
        StringColumnVector rKey = new StringColumnVector(
            "city", true, new String[]{"Zürich", "東京", null, "", "København"}, rNulls, 5);
        IntColumnVector rRank = new IntColumnVector(
            "rank", false, new int[]{3, 1, 99, 0, 4}, new BitSet(), 5);
        CupOfTea right = CupOfTea.fromVectors(List.of(rKey, rRank));

        CupOfTea inner = left.innerJoin(right, "city");
        assertThat(inner.size()).isEqualTo(3);
        assertThat(inner.getRow(0).getString("city")).isEqualTo("");
        assertThat(inner.getRow(0).getInt("rank")).isEqualTo(0);
        assertThat(inner.getRow(1).getString("city")).isEqualTo("Zürich");
        assertThat(inner.getRow(1).getInt("rank")).isEqualTo(3);
        assertThat(inner.getRow(2).getString("city")).isEqualTo("東京");
        assertThat(inner.getRow(2).getInt("rank")).isEqualTo(1);
    }

    @Test
    @DisplayName("Columnar join fast-path on DictionaryColumnVector keys and payload columns")
    void test_dictionaryColumnVectorJoin() {
        // Left has dictionary ["Engineering", "Sales", "Support"]
        DictionaryColumnVector lDept = DictionaryColumnVector.fromStrings(
            "dept", "Engineering", "Sales", null, "Engineering", "Support");
        IntColumnVector lId = new IntColumnVector("emp_id", false, new int[]{10, 20, 30, 40, 50}, new BitSet(), 5);
        CupOfTea left = CupOfTea.fromVectors(List.of(lDept, lId));

        // Right has a different dictionary order ["HR", "Sales", "Engineering"] and a Dictionary payload column
        DictionaryColumnVector rDept = DictionaryColumnVector.fromStrings(
            "dept", "HR", "Sales", "Engineering", null);
        DictionaryColumnVector rRegion = DictionaryColumnVector.fromStrings(
            "region", "EMEA", "AMER", "APAC", "GLOBAL");
        CupOfTea right = CupOfTea.fromVectors(List.of(rDept, rRegion));

        // Dictionary <-> Dictionary join
        CupOfTea inner = left.innerJoin(right, "dept");
        assertThat(inner.size()).isEqualTo(3);
        assertThat(inner.getRow(0).getString("dept")).isEqualTo("Engineering");
        assertThat(inner.getRow(0).getString("region")).isEqualTo("APAC");
        assertThat(inner.getRow(1).getString("dept")).isEqualTo("Sales");
        assertThat(inner.getRow(1).getString("region")).isEqualTo("AMER");
        assertThat(inner.getRow(2).getString("dept")).isEqualTo("Engineering");
        assertThat(inner.getRow(2).getString("region")).isEqualTo("APAC");

        // Left join preserves unmatched and nulls in Dictionary payload column
        CupOfTea leftJoined = left.leftJoin(right, "dept");
        assertThat(leftJoined.size()).isEqualTo(5);
        assertThat(leftJoined.getRow(2).isNull("dept")).isTrue();
        assertThat(leftJoined.getRow(2).isNull("region")).isTrue();
        assertThat(leftJoined.getRow(4).getString("dept")).isEqualTo("Support");
        assertThat(leftJoined.getRow(4).isNull("region")).isTrue();

        // Dictionary <-> StringColumnVector join
        StringColumnVector strDept = new StringColumnVector(
            "dept", false, new String[]{"Sales", "Engineering"}, new BitSet(), 2);
        IntColumnVector budget = new IntColumnVector("budget", false, new int[]{100, 200}, new BitSet(), 2);
        CupOfTea rightStr = CupOfTea.fromVectors(List.of(strDept, budget));

        CupOfTea dictToStr = left.innerJoin(rightStr, "dept");
        assertThat(dictToStr.size()).isEqualTo(3);
        assertThat(dictToStr.getRow(0).getInt("budget")).isEqualTo(200);
        assertThat(dictToStr.getRow(1).getInt("budget")).isEqualTo(100);
        assertThat(dictToStr.getRow(2).getInt("budget")).isEqualTo(200);

        CupOfTea strToDict = rightStr.innerJoin(left, "dept");
        assertThat(strToDict.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("All ColumnVector payload types preserve values and nulls across OUTER join")
    void test_allColumnVectorTypesNullableGather() {
        IntColumnVector lKey = new IntColumnVector("k", false, new int[]{1, 2}, new BitSet(), 2);
        FloatColumnVector lFloat = new FloatColumnVector("f", false, new float[]{1.5f, 2.5f}, new BitSet(), 2);
        BooleanColumnVector lBool = new BooleanColumnVector("b", false, new boolean[]{true, false}, new BitSet(), 2);
        ObjectColumnVector lObj = new ObjectColumnVector(
            "obj", BigDecimal.class, true, new Object[]{new BigDecimal("10.5"), null},
            BitSet.valueOf(new long[]{0b10L}), 2);
        CupOfTea left = CupOfTea.fromVectors(List.of(lKey, lFloat, lBool, lObj));

        IntColumnVector rKey = new IntColumnVector("k", false, new int[]{2, 3}, new BitSet(), 2);
        LongColumnVector rLong = new LongColumnVector("l", false, new long[]{20L, 30L}, new BitSet(), 2);
        DoubleColumnVector rDouble = new DoubleColumnVector("d", false, new double[]{2.25, 3.75}, new BitSet(), 2);
        StringColumnVector rStr = new StringColumnVector("s", false, new String[]{"two", "three"}, new BitSet(), 2);
        CupOfTea right = CupOfTea.fromVectors(List.of(rKey, rLong, rDouble, rStr));

        CupOfTea outer = left.outerJoin(right, "k");
        assertThat(outer.size()).isEqualTo(3);

        // Row 0: k=1 (left only)
        assertThat(outer.getRow(0).getInt("k")).isEqualTo(1);
        assertThat(outer.getRow(0).getFloat("f")).isEqualTo(1.5f);
        assertThat(outer.getRow(0).getBoolean("b")).isTrue();
        assertThat(outer.getRow(0).get("obj")).isEqualTo(new BigDecimal("10.5"));
        assertThat(outer.getRow(0).isNull("l")).isTrue();
        assertThat(outer.getRow(0).isNull("d")).isTrue();
        assertThat(outer.getRow(0).isNull("s")).isTrue();

        // Row 1: k=2 (matched, but obj is null on left)
        assertThat(outer.getRow(1).getInt("k")).isEqualTo(2);
        assertThat(outer.getRow(1).getFloat("f")).isEqualTo(2.5f);
        assertThat(outer.getRow(1).getBoolean("b")).isFalse();
        assertThat(outer.getRow(1).isNull("obj")).isTrue();
        assertThat(outer.getRow(1).getLong("l")).isEqualTo(20L);
        assertThat(outer.getRow(1).getDouble("d")).isEqualTo(2.25);
        assertThat(outer.getRow(1).getString("s")).isEqualTo("two");

        // Row 2: k=3 (right only)
        assertThat(outer.getRow(2).getInt("k")).isEqualTo(3);
        assertThat(outer.getRow(2).isNull("f")).isTrue();
        assertThat(outer.getRow(2).isNull("b")).isTrue();
        assertThat(outer.getRow(2).isNull("obj")).isTrue();
        assertThat(outer.getRow(2).getLong("l")).isEqualTo(30L);
        assertThat(outer.getRow(2).getDouble("d")).isEqualTo(3.75);
        assertThat(outer.getRow(2).getString("s")).isEqualTo("three");
    }

    @Test
    @DisplayName("Parallel columnar joins match sequential results across 1:1 and N:M cardinalities")
    void test_parallelColumnarJoinMatchesSequential() {
        int n = 500;
        int[] lKeys = new int[n];
        String[] lStrs = new String[n];
        double[] lVals = new double[n];
        for (int i = 0; i < n; i++) {
            lKeys[i] = i % 200;
            lStrs[i] = "K_" + (i % 200);
            lVals[i] = i * 1.5;
        }
        CupOfTea leftSeq = CupOfTea.fromVectors(List.of(
            new IntColumnVector("k_int", false, lKeys, new BitSet(), n),
            new StringColumnVector("k_str", false, lStrs, new BitSet(), n),
            new DoubleColumnVector("l_val", false, lVals, new BitSet(), n)
        )).asSequential();
        CupOfTea leftPar = leftSeq.asParallel().withParallelThreshold(64);

        // 1:1 unique right side
        int rUniqueN = 250;
        int[] rUniqueKeys = new int[rUniqueN];
        String[] rUniqueStrs = new String[rUniqueN];
        for (int i = 0; i < rUniqueN; i++) {
            int k = (i % 5 == 0) ? (1000 + i) : i;
            rUniqueKeys[i] = k;
            rUniqueStrs[i] = "K_" + k;
        }
        CupOfTea rightUnique = CupOfTea.fromVectors(List.of(
            new IntColumnVector("k_int", false, rUniqueKeys, new BitSet(), rUniqueN),
            new StringColumnVector("k_str", false, rUniqueStrs, new BitSet(), rUniqueN)
        ));

        for (JoinType jt : List.of(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT, JoinType.OUTER)) {
            CupOfTea seqInt = leftSeq.join(rightUnique, jt, "k_int");
            CupOfTea parInt = leftPar.join(rightUnique, jt, "k_int");
            assertThat(parInt.size()).isEqualTo(seqInt.size());
            for (int r = 0; r < seqInt.size(); r++) {
                assertThat(parInt.getRow(r).get("k_int")).isEqualTo(seqInt.getRow(r).get("k_int"));
                assertThat(parInt.getRow(r).get("l_val")).isEqualTo(seqInt.getRow(r).get("l_val"));
            }

            CupOfTea seqStr = leftSeq.join(rightUnique, jt, "k_str");
            CupOfTea parStr = leftPar.join(rightUnique, jt, "k_str");
            assertThat(parStr.size()).isEqualTo(seqStr.size());
        }

        // N:M duplicate right side
        int rDupN = 300;
        int[] rDupKeys = new int[rDupN];
        String[] rDupStrs = new String[rDupN];
        for (int i = 0; i < rDupN; i++) {
            int k = i % 120;
            rDupKeys[i] = k;
            rDupStrs[i] = "K_" + k;
        }
        CupOfTea rightDup = CupOfTea.fromVectors(List.of(
            new IntColumnVector("k_int", false, rDupKeys, new BitSet(), rDupN),
            new StringColumnVector("k_str", false, rDupStrs, new BitSet(), rDupN)
        ));

        for (JoinType jt : List.of(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT, JoinType.OUTER)) {
            CupOfTea seqDup = leftSeq.join(rightDup, jt, "k_int");
            CupOfTea parDup = leftPar.join(rightDup, jt, "k_int");
            assertThat(parDup.size()).isEqualTo(seqDup.size());
            for (int r = 0; r < seqDup.size(); r++) {
                assertThat(parDup.getRow(r).get("k_int")).isEqualTo(seqDup.getRow(r).get("k_int"));
                assertThat(parDup.getRow(r).get("l_val")).isEqualTo(seqDup.getRow(r).get("l_val"));
            }
        }
    }

    @Test
    @DisplayName("Join with Short String SSO, Long Strings (>16 bytes), Nulls, and Dictionary keys")
    void testStringAndDictionaryJoinVariations() {
        // Strings <= 16 bytes (SSO) and > 16 bytes (fallback), plus nulls
        String shortA = "short_key_1";
        String shortB = "short_key_2";
        String longA = "this_is_a_very_long_string_key_exceeding_16_bytes_A";
        String longB = "this_is_a_very_long_string_key_exceeding_16_bytes_B";

        CupOfTea left = new CupOfTea();
        left.addColumn("key", String.class);
        left.addColumn("l_val", Integer.class);
        left.addRow(Map.of("key", shortA, "l_val", 1));
        left.addRow(Map.of("key", shortB, "l_val", 2));
        left.addRow(Map.of("key", longA, "l_val", 3));
        left.addRow(Map.of("key", longB, "l_val", 4));
        left.addRow(Map.of("l_val", 5)); // null key

        CupOfTea right = new CupOfTea();
        right.addColumn("key", String.class);
        right.addColumn("r_val", String.class);
        right.addRow(Map.of("key", shortA, "r_val", "MATCH_SHORT_A"));
        right.addRow(Map.of("key", longA, "r_val", "MATCH_LONG_A"));
        right.addRow(Map.of("key", "unmatched_right", "r_val", "NO_MATCH"));
        right.addRow(Map.of("r_val", "NULL_RIGHT")); // null key

        // 1. String to String Inner Join
        CupOfTea inner = left.innerJoin(right, "key");
        assertThat(inner.size()).isEqualTo(2);
        assertThat(inner.getRows()).extracting(r -> r.get("l_val")).containsExactlyInAnyOrder(1, 3);
        assertThat(inner.getRows()).extracting(r -> r.get("r_val")).containsExactlyInAnyOrder("MATCH_SHORT_A", "MATCH_LONG_A");

        // 2. String to String Left Join
        CupOfTea leftRes = left.leftJoin(right, "key");
        assertThat(leftRes.size()).isEqualTo(5);

        // 3. Mixed: Left is Dictionary, Right is String
        CupOfTea leftDict = left.withColumn("key", left.getColumnVector("key").toDictionary());
        CupOfTea mixedInner1 = leftDict.innerJoin(right, "key");
        assertThat(mixedInner1.size()).isEqualTo(2);
        assertThat(mixedInner1.getRows()).extracting(r -> r.get("l_val")).containsExactlyInAnyOrder(1, 3);

        // 4. Mixed: Left is String, Right is Dictionary
        CupOfTea rightDict = right.withColumn("key", right.getColumnVector("key").toDictionary());
        CupOfTea mixedInner2 = left.innerJoin(rightDict, "key");
        assertThat(mixedInner2.size()).isEqualTo(2);
        assertThat(mixedInner2.getRows()).extracting(r -> r.get("l_val")).containsExactlyInAnyOrder(1, 3);

        // 5. Dictionary to Dictionary
        CupOfTea dictInner = leftDict.innerJoin(rightDict, "key");
        assertThat(dictInner.size()).isEqualTo(2);
        assertThat(dictInner.getRows()).extracting(r -> r.get("l_val")).containsExactlyInAnyOrder(1, 3);
    }
}
