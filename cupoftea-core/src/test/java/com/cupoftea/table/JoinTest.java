package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;

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
}
