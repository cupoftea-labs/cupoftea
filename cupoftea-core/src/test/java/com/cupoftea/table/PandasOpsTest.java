package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;

public class PandasOpsTest {

    private CupOfTea createSampleFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("department", String.class)
          .addColumn("salary", Double.class);

        df.addRow(Map.of("id", 1, "name", "Alice", "department", "Engineering", "salary", 95000.0));
        df.addRow(Map.of("id", 2, "name", "Bob", "department", "HR", "salary", 65000.0));
        df.addRow(Map.of("id", 3, "name", "Charlie", "department", "Engineering", "salary", 105000.0));
        df.addRow(Map.of("id", 4, "name", "Diana", "department", "Marketing", "salary", 75000.0));
        df.addRow(Map.of("id", 5, "name", "Evan", "department", "HR", "salary", 60000.0));
        df.addRow(Map.of("id", 6, "name", "Fiona", "department", "Engineering", "salary", 110000.0));
        df.addRow(Map.of("id", 7, "name", "George", "department", "Marketing", "salary", 70000.0));

        return df;
    }

    // --- select / drop ---

    @Test
    @DisplayName("select() should keep only selected columns in requested order")
    void test_select_projectionAndReordering() {
        CupOfTea df = createSampleFrame();

        CupOfTea selected = df.copy().select("salary", "name");
        assertThat(selected.getHeaders()).extracting(Column::columnName)
            .containsExactly("salary", "name");

        Row firstRow = selected.getRow(0);
        assertThat(firstRow.getColumnNames()).containsExactly("salary", "name");
        assertThat(firstRow.getDouble(0)).isEqualTo(95000.0);
        assertThat(firstRow.getString(1)).isEqualTo("Alice");
        assertThat(firstRow.containsKey("id")).isFalse();
        assertThat(firstRow.containsKey("department")).isFalse();
    }

    @Test
    @DisplayName("select() should throw IllegalArgumentException for non-existent columns")
    void test_select_nonExistentColumnThrows() {
        CupOfTea df = createSampleFrame();

        assertThatThrownBy(() -> df.select("id", "nonExistent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonExistent");
    }

    @Test
    @DisplayName("select() should respect MutationMode")
    void test_select_mutationModes() {
        // IN_PLACE
        CupOfTea inPlace = createSampleFrame();
        CupOfTea mutated = inPlace.select("id", "name");
        assertThat(mutated).isSameAs(inPlace);
        assertThat(inPlace.getHeaders()).hasSize(2);

        // COPY_ON_WRITE
        CupOfTea cow = createSampleFrame().asCopyOnWrite();
        CupOfTea projected = cow.select("id", "name");
        assertThat(projected).isNotSameAs(cow);
        assertThat(cow.getHeaders()).hasSize(4);
        assertThat(projected.getHeaders()).hasSize(2);
    }

    @Test
    @DisplayName("drop() should remove specified columns")
    void test_drop_columns() {
        CupOfTea df = createSampleFrame();

        CupOfTea dropped = df.copy().drop("department", "salary");
        assertThat(dropped.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "name");

        Row firstRow = dropped.getRow(0);
        assertThat(firstRow.containsKey("department")).isFalse();
        assertThat(firstRow.containsKey("salary")).isFalse();
        assertThat(firstRow.getInt("id")).isEqualTo(1);
    }

    @Test
    @DisplayName("drop() should throw IllegalArgumentException for non-existent columns")
    void test_drop_nonExistentColumnThrows() {
        CupOfTea df = createSampleFrame();

        assertThatThrownBy(() -> df.drop("unknownCol"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknownCol");
    }

    @Test
    @DisplayName("drop() should respect MutationMode")
    void test_drop_mutationModes() {
        // IN_PLACE
        CupOfTea inPlace = createSampleFrame();
        CupOfTea res = inPlace.drop("department");
        assertThat(res).isSameAs(inPlace);
        assertThat(inPlace.getColumnHeader("department")).isNull();

        // COPY_ON_WRITE
        CupOfTea cow = createSampleFrame().asCopyOnWrite();
        CupOfTea droppedCow = cow.drop("department");
        assertThat(droppedCow).isNotSameAs(cow);
        assertThat(cow.getColumnHeader("department")).isNotNull();
        assertThat(droppedCow.getColumnHeader("department")).isNull();
    }

    // --- head / tail / slice ---

    @Test
    @DisplayName("head() defaults to 5 rows and head(n) slices first n rows without modifying original")
    void test_head() {
        CupOfTea df = createSampleFrame(); // 7 rows
        assertThat(df.size()).isEqualTo(7);

        // default head() returns 5 rows
        CupOfTea h5 = df.head();
        assertThat(h5.size()).isEqualTo(5);
        assertThat(df.size()).isEqualTo(7); // original unchanged
        assertThat(h5.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(h5.getRow(4).getInt("id")).isEqualTo(5);

        // head(3)
        CupOfTea h3 = df.head(3);
        assertThat(h3.size()).isEqualTo(3);
        assertThat(h3.getRow(2).getInt("id")).isEqualTo(3);

        // head larger than size returns all
        CupOfTea h100 = df.head(100);
        assertThat(h100.size()).isEqualTo(7);

        // head(0) or negative returns empty frame with same schema
        CupOfTea h0 = df.head(0);
        assertThat(h0.size()).isEqualTo(0);
        assertThat(h0.getHeaders()).hasSize(4);

        // Preserves mutation mode
        CupOfTea cow = df.asCopyOnWrite();
        assertThat(cow.head(2).getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);
    }

    @Test
    @DisplayName("tail() defaults to 5 rows and tail(n) slices last n rows without modifying original")
    void test_tail() {
        CupOfTea df = createSampleFrame(); // 7 rows (ids 1..7)

        // default tail() returns 5 rows (ids 3..7)
        CupOfTea t5 = df.tail();
        assertThat(t5.size()).isEqualTo(5);
        assertThat(df.size()).isEqualTo(7);
        assertThat(t5.getRow(0).getInt("id")).isEqualTo(3);
        assertThat(t5.getRow(4).getInt("id")).isEqualTo(7);

        // tail(2) returns ids 6, 7
        CupOfTea t2 = df.tail(2);
        assertThat(t2.size()).isEqualTo(2);
        assertThat(t2.getRow(0).getInt("id")).isEqualTo(6);
        assertThat(t2.getRow(1).getInt("id")).isEqualTo(7);

        // tail(100) returns all 7
        assertThat(df.tail(100).size()).isEqualTo(7);

        // tail(0) returns empty
        assertThat(df.tail(0).size()).isEqualTo(0);
    }

    @Test
    @DisplayName("slice() supports standard range and negative indices")
    void test_slice() {
        CupOfTea df = createSampleFrame(); // ids 1..7

        CupOfTea s1 = df.slice(1, 4); // rows 1, 2, 3 -> ids 2, 3, 4
        assertThat(s1.size()).isEqualTo(3);
        assertThat(s1.getRow(0).getInt("id")).isEqualTo(2);
        assertThat(s1.getRow(2).getInt("id")).isEqualTo(4);

        // Negative index: slice(0, -1) excludes last row
        CupOfTea sNeg = df.slice(0, -1); // rows 0..5 -> ids 1..6
        assertThat(sNeg.size()).isEqualTo(6);
        assertThat(sNeg.getRow(5).getInt("id")).isEqualTo(6);
    }

    // --- iloc ---

    @Test
    @DisplayName("iloc(int) returns Row with negative index support")
    void test_iloc_row() {
        CupOfTea df = createSampleFrame();

        Row first = df.iloc(0);
        assertThat(first.getString("name")).isEqualTo("Alice");

        Row last = df.iloc(-1);
        assertThat(last.getString("name")).isEqualTo("George");

        Row secondLast = df.iloc(-2);
        assertThat(secondLast.getString("name")).isEqualTo("Fiona");

        assertThatThrownBy(() -> df.iloc(7))
            .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> df.iloc(-8))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("iloc(row, col) returns cell value with negative index support")
    void test_iloc_cell() {
        CupOfTea df = createSampleFrame();

        assertThat(df.iloc(0, 0)).isEqualTo(1);
        assertThat(df.iloc(0, 1)).isEqualTo("Alice");
        assertThat(df.iloc(0, "name")).isEqualTo("Alice");

        // Negative row and col indices
        assertThat(df.iloc(-1, -1)).isEqualTo(70000.0); // last row, last col (salary)
        assertThat(df.iloc(-1, 1)).isEqualTo("George");
    }

    @Test
    @DisplayName("iloc() indexer supports row, cell, slice, rows, and columns")
    void test_iloc_indexer() {
        CupOfTea df = createSampleFrame();

        // iloc().row() & get()
        assertThat(df.iloc().row(0).getString("name")).isEqualTo("Alice");
        assertThat(df.iloc().get(-1).getString("name")).isEqualTo("George");

        // iloc().cell() & get()
        assertThat(df.iloc().cell(0, 1)).isEqualTo("Alice");
        assertThat(df.iloc().cell(0, "department")).isEqualTo("Engineering");

        // iloc().slice(fromRow, toRow)
        CupOfTea slicedRows = df.iloc().slice(1, 3);
        assertThat(slicedRows.size()).isEqualTo(2);
        assertThat(slicedRows.getRow(0).getString("name")).isEqualTo("Bob");

        // iloc().slice(fromRow, toRow, fromCol, toCol)
        CupOfTea sliced2D = df.iloc().slice(0, 2, 1, 3); // rows 0..1, cols 1..2 ("name", "department")
        assertThat(sliced2D.size()).isEqualTo(2);
        assertThat(sliced2D.getHeaders()).extracting(Column::columnName)
            .containsExactly("name", "department");
        assertThat(sliced2D.getRow(0).getString("name")).isEqualTo("Alice");

        // iloc().rows(indices)
        CupOfTea pickedRows = df.iloc().rows(0, 2, -1);
        assertThat(pickedRows.size()).isEqualTo(3);
        assertThat(pickedRows.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(pickedRows.getRow(1).getString("name")).isEqualTo("Charlie");
        assertThat(pickedRows.getRow(2).getString("name")).isEqualTo("George");

        // iloc().columns(colIndices)
        CupOfTea pickedCols = df.iloc().columns(0, 3);
        assertThat(pickedCols.getHeaders()).extracting(Column::columnName)
            .containsExactly("id", "salary");
    }

    // --- loc (with index) ---

    @Test
    @DisplayName("loc without setting an index throws IllegalStateException")
    void test_loc_withoutIndex_throws() {
        CupOfTea df = createSampleFrame();
        assertThat(df.getIndex()).isNull();

        assertThatThrownBy(() -> df.loc(1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Call setIndex");

        assertThatThrownBy(() -> df.loc().rows(1, 2))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("setIndex, getIndex, resetIndex, and loc lookups")
    void test_loc_withIndex() {
        CupOfTea df = createSampleFrame();
        df.setIndex("id");
        assertThat(df.getIndex()).isEqualTo("id");

        // Lookup row by index key
        Row alice = df.loc(1);
        assertThat(alice.getString("name")).isEqualTo("Alice");

        Row charlie = df.loc(3);
        assertThat(charlie.getString("name")).isEqualTo("Charlie");

        // Cell lookup by index key + column
        assertThat(df.loc(2, "department")).isEqualTo("HR");
        assertThat(df.loc().cell(4, "name")).isEqualTo("Diana");

        // loc().rows(keys...)
        CupOfTea subset = df.loc().rows(1, 3);
        assertThat(subset.size()).isEqualTo(2);
        assertThat(subset.getRow(0).getString("name")).isEqualTo("Alice");
        assertThat(subset.getRow(1).getString("name")).isEqualTo("Charlie");

        // Nonexistent key throws IllegalArgumentException
        assertThatThrownBy(() -> df.loc(999))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");

        // resetIndex
        df.resetIndex();
        assertThat(df.getIndex()).isNull();
    }

    // --- fillna / dropna ---

    private CupOfTea createFrameWithMissingValues() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("score", Double.class)
          .addColumn("comments", String.class);

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "Alice");
        r1.put("score", 90.0);
        r1.put("comments", "Great");
        df.addRow(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", null);
        r2.put("score", 85.0);
        r2.put("comments", ""); // empty string should be preserved
        df.addRow(r2);

        Map<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("name", "Charlie");
        r3.put("score", Double.NaN); // NaN is NA
        r3.put("comments", null);
        df.addRow(r3);

        Map<String, Object> r4 = new HashMap<>();
        r4.put("id", 4);
        r4.put("name", null);
        r4.put("score", null);
        r4.put("comments", NA.NA); // NA singleton
        df.addRow(r4);

        return df;
    }

    @Test
    @DisplayName("fillna(value) should replace all missing values across all columns while preserving empty strings")
    void test_fillna_global() {
        CupOfTea df = createFrameWithMissingValues().fillna("N/A");

        // Row 2: name was null, comments was ""
        assertThat(df.getRow(1).getString("name")).isEqualTo("N/A");
        assertThat(df.getRow(1).getString("comments")).isEqualTo(""); // preserved

        // Row 3: score was NaN, comments was null
        assertThat(df.getRow(2).getString("comments")).isEqualTo("N/A");

        // Row 4: comments was NA.NA
        assertThat(df.getRow(3).getString("comments")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("fillna(column, value) should replace missing values only in specified column")
    void test_fillna_columnSpecific() {
        CupOfTea df = createFrameWithMissingValues().fillna("name", "Unknown");

        assertThat(df.getRow(1).getString("name")).isEqualTo("Unknown");
        assertThat(df.getRow(3).getString("name")).isEqualTo("Unknown");
        // score remains NA in row 3
        assertThat(df.getRow(2).isNA("score")).isTrue();
    }

    @Test
    @DisplayName("fillna(Map) replaces values per column")
    void test_fillna_mapping() {
        CupOfTea df = createFrameWithMissingValues().fillna(Map.of(
            "name", "Anonymous",
            "score", 0.0
        ));

        assertThat(df.getRow(1).getString("name")).isEqualTo("Anonymous");
        assertThat(df.getRow(2).getDouble("score")).isEqualTo(0.0);
        assertThat(df.getRow(3).getDouble("score")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("dropna() drops rows containing any missing value by default")
    void test_dropna_any() {
        CupOfTea df = createFrameWithMissingValues().dropna();

        // Only row 1 has no NA values (row 2 has null name, row 3 has NaN score, row 4 has nulls)
        assertThat(df.size()).isEqualTo(1);
        assertThat(df.getRow(0).getInt("id")).isEqualTo(1);
    }

    @Test
    @DisplayName("dropna(columns) drops rows containing missing value in specified columns")
    void test_dropna_subsetColumns() {
        // Drop only if 'name' is missing
        CupOfTea df = createFrameWithMissingValues().dropna("name");

        // Rows 1 and 3 have names; Rows 2 and 4 have null names
        assertThat(df.size()).isEqualTo(2);
        assertThat(df.getRow(0).getInt("id")).isEqualTo(1);
        assertThat(df.getRow(1).getInt("id")).isEqualTo(3);
    }

    @Test
    @DisplayName("dropna('all') drops rows only if all columns are missing")
    void test_dropna_howAll() {
        CupOfTea df = new CupOfTea();
        df.addColumn("a", String.class).addColumn("b", String.class);
        df.addRow(Map.of("a", "valA", "b", "valB"));
        df.addRow(new HashMap<>() {{ put("a", "valA"); put("b", null); }});
        df.addRow(new HashMap<>() {{ put("a", null); put("b", null); }}); // both missing

        CupOfTea result = df.dropna("all");
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("fillna and dropna respect MutationMode")
    void test_fillna_dropna_mutationMode() {
        // COPY_ON_WRITE
        CupOfTea cow = createFrameWithMissingValues().asCopyOnWrite();
        CupOfTea filled = cow.fillna("name", "Fixed");
        assertThat(filled).isNotSameAs(cow);
        assertThat(cow.getRow(1).getString("name")).isNull();
        assertThat(filled.getRow(1).getString("name")).isEqualTo("Fixed");

        CupOfTea dropped = cow.dropna();
        assertThat(dropped).isNotSameAs(cow);
        assertThat(cow.size()).isEqualTo(4);
        assertThat(dropped.size()).isEqualTo(1);
    }
}
