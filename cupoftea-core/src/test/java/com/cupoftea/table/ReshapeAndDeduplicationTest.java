package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.ColumnVector;

public class ReshapeAndDeduplicationTest {

    // =========================================================================
    // 1. Pivot Table Tests
    // =========================================================================

    @Nested
    @DisplayName("Pivot Table (pivot)")
    class PivotTests {

        private CupOfTea createLongSalesFrame() {
            CupOfTea df = new CupOfTea();
            df.addColumn("date", String.class)
              .addColumn("city", String.class)
              .addColumn("sales", Double.class);

            df.addRow(Map.of("date", "2026-01-01", "city", "New York", "sales", 100.0));
            df.addRow(Map.of("date", "2026-01-01", "city", "London", "sales", 200.0));
            df.addRow(Map.of("date", "2026-01-02", "city", "New York", "sales", 150.0));
            df.addRow(Map.of("date", "2026-01-02", "city", "London", "sales", 250.0));
            df.addRow(Map.of("date", "2026-01-02", "city", "Tokyo", "sales", 300.0));
            return df;
        }

        @Test
        @DisplayName("pivot() default aggregation on numeric column uses mean")
        void test_pivot_defaultAggregation_numeric() {
            CupOfTea df = createLongSalesFrame();
            // Add a duplicate coordinate (2026-01-01, New York) to verify aggregation
            df.addRow(Map.of("date", "2026-01-01", "city", "New York", "sales", 200.0));

            CupOfTea pivoted = df.pivot("date", "city", "sales");

            assertThat(pivoted.size()).isEqualTo(2);
            assertThat(pivoted.getHeaders()).extracting(Column::columnName)
                .containsExactly("date", "New York", "London", "Tokyo");

            // Row 0: 2026-01-01: NY mean = (100+200)/2 = 150.0, London = 200.0, Tokyo = null
            Row r0 = pivoted.getRow(0);
            assertThat(r0.getString("date")).isEqualTo("2026-01-01");
            assertThat(r0.getDouble("New York")).isEqualTo(150.0);
            assertThat(r0.getDouble("London")).isEqualTo(200.0);
            assertThat(r0.get("Tokyo")).isNull();

            // Row 1: 2026-01-02: NY = 150.0, London = 250.0, Tokyo = 300.0
            Row r1 = pivoted.getRow(1);
            assertThat(r1.getString("date")).isEqualTo("2026-01-02");
            assertThat(r1.getDouble("New York")).isEqualTo(150.0);
            assertThat(r1.getDouble("London")).isEqualTo(250.0);
            assertThat(r1.getDouble("Tokyo")).isEqualTo(300.0);
        }

        @Test
        @DisplayName("pivot() with SUM aggregation preserves integer type and sums values")
        void test_pivot_sumAggregation_preservesIntType() {
            CupOfTea df = new CupOfTea();
            df.addColumn("date", String.class)
              .addColumn("item", String.class)
              .addColumn("quantity", Integer.class);

            df.addRow(Map.of("date", "2026-01-01", "item", "Apple", "quantity", 10));
            df.addRow(Map.of("date", "2026-01-01", "item", "Apple", "quantity", 5));
            df.addRow(Map.of("date", "2026-01-01", "item", "Banana", "quantity", 20));
            df.addRow(Map.of("date", "2026-01-02", "item", "Banana", "quantity", 30));

            CupOfTea pivoted = df.pivot("date", "item", "quantity", Aggregation.SUM);

            assertThat(pivoted.size()).isEqualTo(2);
            assertThat(pivoted.getColumnHeader("Apple").columnType()).isEqualTo(Integer.class);
            assertThat(pivoted.getColumnHeader("Banana").columnType()).isEqualTo(Integer.class);

            Row r0 = pivoted.getRow(0);
            assertThat(r0.getInt("Apple")).isEqualTo(15);
            assertThat(r0.getInt("Banana")).isEqualTo(20);

            Row r1 = pivoted.getRow(1);
            assertThat(r1.get("Apple")).isNull();
            assertThat(r1.getInt("Banana")).isEqualTo(30);
        }

        @Test
        @DisplayName("pivot() with BigDecimal type preservation")
        void test_pivot_bigDecimalPreservation() {
            CupOfTea df = new CupOfTea();
            df.addColumn("dept", String.class)
              .addColumn("quarter", String.class)
              .addColumn("budget", BigDecimal.class);

            df.addRow(Map.of("dept", "Sales", "quarter", "Q1", "budget", new BigDecimal("10000.50")));
            df.addRow(Map.of("dept", "Sales", "quarter", "Q1", "budget", new BigDecimal("5000.25")));
            df.addRow(Map.of("dept", "Sales", "quarter", "Q2", "budget", new BigDecimal("12000.00")));

            CupOfTea pivoted = df.pivot("dept", "quarter", "budget", Aggregation.SUM);

            assertThat(pivoted.getColumnHeader("Q1").columnType()).isEqualTo(BigDecimal.class);
            Row r0 = pivoted.getRow(0);
            assertThat(r0.get("Q1")).isEqualTo(new BigDecimal("15000.75"));
            assertThat(r0.get("Q2")).isEqualTo(new BigDecimal("12000.00"));
        }

        @Test
        @DisplayName("pivot() with COUNT, MIN, MAX, FIRST, and LAST aggregations")
        void test_pivot_variousAggregations() {
            CupOfTea df = new CupOfTea();
            df.addColumn("cat", String.class)
              .addColumn("grp", String.class)
              .addColumn("val", Integer.class);

            df.addRow(Map.of("cat", "A", "grp", "X", "val", 10));
            df.addRow(Map.of("cat", "A", "grp", "X", "val", 30));
            df.addRow(Map.of("cat", "A", "grp", "X", "val", 20));

            // COUNT
            CupOfTea countPiv = df.copy().pivot("cat", "grp", "val", Aggregation.COUNT);
            assertThat(countPiv.getRow(0).getInt("X")).isEqualTo(3);

            // MIN
            CupOfTea minPiv = df.copy().pivot("cat", "grp", "val", Aggregation.MIN);
            assertThat(minPiv.getRow(0).getInt("X")).isEqualTo(10);

            // MAX
            CupOfTea maxPiv = df.copy().pivot("cat", "grp", "val", Aggregation.MAX);
            assertThat(maxPiv.getRow(0).getInt("X")).isEqualTo(30);

            // FIRST
            CupOfTea firstPiv = df.copy().pivot("cat", "grp", "val", Aggregation.FIRST);
            assertThat(firstPiv.getRow(0).getInt("X")).isEqualTo(10);

            // LAST
            CupOfTea lastPiv = df.copy().pivot("cat", "grp", "val", Aggregation.LAST);
            assertThat(lastPiv.getRow(0).getInt("X")).isEqualTo(20);
        }

        @Test
        @DisplayName("pivot() with Aggregate directly")
        void test_pivot_withAggregateDirectly() {
            CupOfTea df = createLongSalesFrame();
            CupOfTea pivoted = df.pivot("date", "city", "sales", Aggregations.sum("sales"));

            assertThat(pivoted.size()).isEqualTo(2);
            assertThat(pivoted.getRow(0).getDouble("London")).isEqualTo(200.0);
        }

        @Test
        @DisplayName("pivot() handles nulls in index and columns")
        void test_pivot_nullsInIndexAndColumns() {
            CupOfTea df = new CupOfTea();
            df.addColumn("date", String.class)
              .addColumn("city", String.class)
              .addColumn("sales", Double.class);

            HashMap<String, Object> r1 = new HashMap<>();
            r1.put("date", null);
            r1.put("city", "London");
            r1.put("sales", 100.0);
            df.addRow(r1);

            HashMap<String, Object> r2 = new HashMap<>();
            r2.put("date", "2026-01-01");
            r2.put("city", null);
            r2.put("sales", 200.0);
            df.addRow(r2);

            CupOfTea pivoted = df.pivot("date", "city", "sales", Aggregation.SUM);

            assertThat(pivoted.size()).isEqualTo(2);
            // Column names include London and null
            assertThat(pivoted.getHeaders()).extracting(Column::columnName)
                .containsExactly("date", "London", "null");

            Row nullIndexRow = pivoted.getRow(0);
            assertThat(nullIndexRow.get("date")).isNull();
            assertThat(nullIndexRow.getDouble("London")).isEqualTo(100.0);

            Row nullColRow = pivoted.getRow(1);
            assertThat(nullColRow.getString("date")).isEqualTo("2026-01-01");
            assertThat(nullColRow.getDouble("null")).isEqualTo(200.0);
        }

        @Test
        @DisplayName("pivot() handles empty DataFrame")
        void test_pivot_emptyDataFrame() {
            CupOfTea empty = new CupOfTea();
            empty.addColumn("date", String.class)
                 .addColumn("city", String.class)
                 .addColumn("sales", Double.class);

            CupOfTea pivoted = empty.pivot("date", "city", "sales");
            assertThat(pivoted.size()).isEqualTo(0);
            assertThat(pivoted.getHeaders()).hasSize(1);
            assertThat(pivoted.getColumnHeader("date")).isNotNull();
        }

        @Test
        @DisplayName("pivot() throws for non-existent columns")
        void test_pivot_nonExistentColumns() {
            CupOfTea df = createLongSalesFrame();

            assertThatThrownBy(() -> df.pivot("missing", "city", "sales"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> df.pivot("date", "missing", "sales"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> df.pivot("date", "city", "missing"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> df.pivot(null, "city", "sales"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pivot() respects MutationMode")
        void test_pivot_mutationMode() {
            CupOfTea cow = createLongSalesFrame().asCopyOnWrite();
            CupOfTea pivotedCow = cow.pivot("date", "city", "sales");
            assertThat(pivotedCow).isNotSameAs(cow);
            assertThat(cow.getHeaders()).hasSize(3);
            assertThat(pivotedCow.getHeaders()).hasSize(4);
            assertThat(pivotedCow.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);

            CupOfTea inPlace = createLongSalesFrame().asInPlace();
            CupOfTea pivotedInPlace = inPlace.pivot("date", "city", "sales");
            assertThat(pivotedInPlace).isSameAs(inPlace);
            assertThat(inPlace.getHeaders()).hasSize(4);
            assertThat(inPlace.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
        }
    }

    // =========================================================================
    // 2. Unpivot / Melt Tests
    // =========================================================================

    @Nested
    @DisplayName("Unpivot / Melt (melt)")
    class MeltTests {

        private CupOfTea createWideSalesFrame() {
            CupOfTea df = new CupOfTea();
            df.addColumn("id", Integer.class)
              .addColumn("name", String.class)
              .addColumn("2020", Double.class)
              .addColumn("2021", Double.class);

            df.addRow(Map.of("id", 1, "name", "Alice", "2020", 100.0, "2021", 110.0));
            df.addRow(Map.of("id", 2, "name", "Bob", "2020", 200.0, "2021", 220.0));
            return df;
        }

        @Test
        @DisplayName("melt() collapses designated value columns with columnar vector recreation")
        void test_melt_basic() {
            CupOfTea df = createWideSalesFrame();

            CupOfTea melted = df.melt(List.of("id", "name"), List.of("2020", "2021"), "year", "revenue");

            assertThat(melted.size()).isEqualTo(4); // 2 rows * 2 valueVars
            assertThat(melted.getHeaders()).extracting(Column::columnName)
                .containsExactly("id", "name", "year", "revenue");

            // Verify columnar vectors were recreated with correct types
            assertThat(melted.getColumnHeader("id").columnType()).isEqualTo(Integer.class);
            assertThat(melted.getColumnHeader("name").columnType()).isEqualTo(String.class);
            assertThat(melted.getColumnHeader("year").columnType()).isEqualTo(String.class);
            assertThat(melted.getColumnHeader("revenue").columnType()).isEqualTo(Double.class);

            // Variable 2020 rows
            Row r0 = melted.getRow(0);
            assertThat(r0.getInt("id")).isEqualTo(1);
            assertThat(r0.getString("name")).isEqualTo("Alice");
            assertThat(r0.getString("year")).isEqualTo("2020");
            assertThat(r0.getDouble("revenue")).isEqualTo(100.0);

            Row r1 = melted.getRow(1);
            assertThat(r1.getInt("id")).isEqualTo(2);
            assertThat(r1.getString("name")).isEqualTo("Bob");
            assertThat(r1.getString("year")).isEqualTo("2020");
            assertThat(r1.getDouble("revenue")).isEqualTo(200.0);

            // Variable 2021 rows
            Row r2 = melted.getRow(2);
            assertThat(r2.getInt("id")).isEqualTo(1);
            assertThat(r2.getString("name")).isEqualTo("Alice");
            assertThat(r2.getString("year")).isEqualTo("2021");
            assertThat(r2.getDouble("revenue")).isEqualTo(110.0);

            Row r3 = melted.getRow(3);
            assertThat(r3.getInt("id")).isEqualTo(2);
            assertThat(r3.getString("name")).isEqualTo("Bob");
            assertThat(r3.getString("year")).isEqualTo("2021");
            assertThat(r3.getDouble("revenue")).isEqualTo(220.0);
        }

        @Test
        @DisplayName("melt() with default valueVars unpivots all non-id columns")
        void test_melt_defaultValueVars() {
            CupOfTea df = createWideSalesFrame();

            CupOfTea melted = df.melt(List.of("id", "name"));

            assertThat(melted.getHeaders()).extracting(Column::columnName)
                .containsExactly("id", "name", "variable", "value");
            assertThat(melted.size()).isEqualTo(4);
            assertThat(melted.getRow(0).getString("variable")).isEqualTo("2020");
            assertThat(melted.getRow(2).getString("variable")).isEqualTo("2021");
        }

        @Test
        @DisplayName("melt() without idVars unpivots all columns")
        void test_melt_noIdVars() {
            CupOfTea df = new CupOfTea();
            df.addColumn("A", Integer.class).addColumn("B", Integer.class);
            df.addRow(Map.of("A", 1, "B", 2));

            CupOfTea melted = df.melt();

            assertThat(melted.getHeaders()).extracting(Column::columnName)
                .containsExactly("variable", "value");
            assertThat(melted.size()).isEqualTo(2);
            assertThat(melted.getRow(0).getString("variable")).isEqualTo("A");
            assertThat(melted.getRow(0).getInt("value")).isEqualTo(1);
            assertThat(melted.getRow(1).getString("variable")).isEqualTo("B");
            assertThat(melted.getRow(1).getInt("value")).isEqualTo(2);
        }

        @Test
        @DisplayName("melt() handles empty DataFrame and null values")
        void test_melt_emptyAndNulls() {
            CupOfTea emptyDf = new CupOfTea();
            emptyDf.addColumn("id", Integer.class).addColumn("val", String.class);

            // Empty
            CupOfTea meltedEmpty = emptyDf.copy().melt(List.of("id"), List.of("val"));
            assertThat(meltedEmpty.size()).isEqualTo(0);
            assertThat(meltedEmpty.getHeaders()).extracting(Column::columnName)
                .containsExactly("id", "variable", "value");

            // With nulls
            CupOfTea dfWithNulls = new CupOfTea();
            dfWithNulls.addColumn("id", Integer.class).addColumn("val", String.class);
            HashMap<String, Object> r = new HashMap<>();
            r.put("id", null);
            r.put("val", null);
            dfWithNulls.addRow(r);

            CupOfTea meltedNulls = dfWithNulls.melt(List.of("id"), List.of("val"));
            assertThat(meltedNulls.size()).isEqualTo(1);
            assertThat(meltedNulls.getRow(0).get("id")).isNull();
            assertThat(meltedNulls.getRow(0).getString("variable")).isEqualTo("val");
            assertThat(meltedNulls.getRow(0).get("value")).isNull();
        }

        @Test
        @DisplayName("melt() throws on non-existent columns")
        void test_melt_nonExistentColumnsThrows() {
            CupOfTea df = createWideSalesFrame();

            assertThatThrownBy(() -> df.melt(List.of("nonExistent"), List.of("2020")))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> df.melt(List.of("id"), List.of("nonExistent")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("melt() respects MutationMode")
        void test_melt_mutationMode() {
            CupOfTea cow = createWideSalesFrame().asCopyOnWrite();
            CupOfTea meltedCow = cow.melt(List.of("id"), List.of("2020", "2021"));
            assertThat(meltedCow).isNotSameAs(cow);
            assertThat(cow.size()).isEqualTo(2);
            assertThat(meltedCow.size()).isEqualTo(4);
            assertThat(meltedCow.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);

            CupOfTea inPlace = createWideSalesFrame().asInPlace();
            CupOfTea meltedInPlace = inPlace.melt(List.of("id"), List.of("2020", "2021"));
            assertThat(meltedInPlace).isSameAs(inPlace);
            assertThat(inPlace.size()).isEqualTo(4);
            assertThat(inPlace.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
        }
    }

    // =========================================================================
    // 3. Deduplication Tests (dropDuplicates & distinct)
    // =========================================================================

    @Nested
    @DisplayName("Deduplication (dropDuplicates / distinct)")
    class DeduplicationTests {

        private CupOfTea createDuplicateFrame() {
            CupOfTea df = new CupOfTea();
            df.addColumn("id", Integer.class)
              .addColumn("name", String.class)
              .addColumn("city", String.class);

            df.addRow(Map.of("id", 1, "name", "Alice", "city", "Paris"));   // index 0
            df.addRow(Map.of("id", 2, "name", "Bob", "city", "London"));    // index 1
            df.addRow(Map.of("id", 1, "name", "Alice", "city", "Paris"));   // index 2 (exact duplicate of 0)
            df.addRow(Map.of("id", 3, "name", "Charlie", "city", "Rome"));  // index 3
            df.addRow(Map.of("id", 1, "name", "Alice", "city", "Milan"));   // index 4 (diff city, same id/name)
            df.addRow(Map.of("id", 2, "name", "Bob", "city", "London"));    // index 5 (exact duplicate of 1)
            return df;
        }

        @Test
        @DisplayName("dropDuplicates() with default Keep.FIRST across all columns")
        void test_dropDuplicates_defaultKeepFirst() {
            CupOfTea df = createDuplicateFrame();

            CupOfTea deduped = df.copy().dropDuplicates();

            // Total 6 rows: row 2 dropped (dup of 0), row 5 dropped (dup of 1). Leaves 4 rows.
            assertThat(deduped.size()).isEqualTo(4);
            assertThat(deduped.getRow(0).getInt("id")).isEqualTo(1);
            assertThat(deduped.getRow(0).getString("city")).isEqualTo("Paris");
            assertThat(deduped.getRow(1).getInt("id")).isEqualTo(2);
            assertThat(deduped.getRow(2).getInt("id")).isEqualTo(3);
            assertThat(deduped.getRow(3).getInt("id")).isEqualTo(1);
            assertThat(deduped.getRow(3).getString("city")).isEqualTo("Milan");
        }

        @Test
        @DisplayName("distinct() is an exact alias for dropDuplicates()")
        void test_distinct_alias() {
            CupOfTea df = createDuplicateFrame();

            CupOfTea deduped = df.copy().distinct();
            assertThat(deduped.size()).isEqualTo(4);
        }

        @Test
        @DisplayName("dropDuplicates() with Keep.LAST keeps the last occurrence")
        void test_dropDuplicates_keepLast() {
            CupOfTea df = createDuplicateFrame();

            CupOfTea deduped = df.copy().dropDuplicates(Keep.LAST);

            // Row 0 dropped (dup of 2), row 1 dropped (dup of 5).
            assertThat(deduped.size()).isEqualTo(4);
            assertThat(deduped.getRow(0).getInt("id")).isEqualTo(1);
            assertThat(deduped.getRow(0).getString("city")).isEqualTo("Paris"); // row 2 kept
            assertThat(deduped.getRow(1).getInt("id")).isEqualTo(3);
            assertThat(deduped.getRow(2).getInt("id")).isEqualTo(1);
            assertThat(deduped.getRow(2).getString("city")).isEqualTo("Milan");
            assertThat(deduped.getRow(3).getInt("id")).isEqualTo(2);
            assertThat(deduped.getRow(3).getString("city")).isEqualTo("London"); // row 5 kept
        }

        @Test
        @DisplayName("dropDuplicates() with Keep.NONE drops all duplicated rows")
        void test_dropDuplicates_keepNone() {
            CupOfTea df = createDuplicateFrame();

            CupOfTea deduped = df.copy().dropDuplicates(Keep.NONE);

            // Row 0 and 2 appear twice -> both dropped
            // Row 1 and 5 appear twice -> both dropped
            // Remaining: Row 3 (Charlie) and Row 4 (Alice Milan)
            assertThat(deduped.size()).isEqualTo(2);
            assertThat(deduped.getRow(0).getString("name")).isEqualTo("Charlie");
            assertThat(deduped.getRow(1).getString("city")).isEqualTo("Milan");
        }

        @Test
        @DisplayName("dropDuplicates() considering column subset")
        void test_dropDuplicates_columnSubset() {
            CupOfTea df = createDuplicateFrame();

            // Considering only "name" column: Alice (indices 0, 2, 4), Bob (indices 1, 5), Charlie (index 3)
            CupOfTea subsetFirst = df.copy().dropDuplicates(Keep.FIRST, "name");
            assertThat(subsetFirst.size()).isEqualTo(3);
            assertThat(subsetFirst.getRow(0).getString("name")).isEqualTo("Alice");
            assertThat(subsetFirst.getRow(0).getString("city")).isEqualTo("Paris"); // from index 0
            assertThat(subsetFirst.getRow(1).getString("name")).isEqualTo("Bob");
            assertThat(subsetFirst.getRow(2).getString("name")).isEqualTo("Charlie");

            CupOfTea subsetLast = df.copy().dropDuplicates(Keep.LAST, "name");
            assertThat(subsetLast.size()).isEqualTo(3);
            assertThat(subsetLast.getRow(0).getString("name")).isEqualTo("Charlie");
            assertThat(subsetLast.getRow(1).getString("name")).isEqualTo("Alice");
            assertThat(subsetLast.getRow(1).getString("city")).isEqualTo("Milan"); // from index 4
            assertThat(subsetLast.getRow(2).getString("name")).isEqualTo("Bob");

            CupOfTea subsetNone = df.copy().dropDuplicates(Keep.NONE, "name");
            assertThat(subsetNone.size()).isEqualTo(1);
            assertThat(subsetNone.getRow(0).getString("name")).isEqualTo("Charlie");
        }

        @Test
        @DisplayName("dropDuplicates() edge case: all rows duplicate")
        void test_dropDuplicates_allDuplicateRows() {
            CupOfTea df = new CupOfTea();
            df.addColumn("a", Integer.class);
            df.addRow(Map.of("a", 99));
            df.addRow(Map.of("a", 99));
            df.addRow(Map.of("a", 99));

            assertThat(df.copy().dropDuplicates(Keep.FIRST).size()).isEqualTo(1);
            assertThat(df.copy().dropDuplicates(Keep.LAST).size()).isEqualTo(1);
            assertThat(df.copy().dropDuplicates(Keep.NONE).size()).isEqualTo(0);
        }

        @Test
        @DisplayName("dropDuplicates() edge case: nulls in subset columns are treated as equal")
        void test_dropDuplicates_nullsInSubset() {
            CupOfTea df = new CupOfTea();
            df.addColumn("id", Integer.class).addColumn("note", String.class);

            HashMap<String, Object> r1 = new HashMap<>();
            r1.put("id", null);
            r1.put("note", "first");
            df.addRow(r1);

            HashMap<String, Object> r2 = new HashMap<>();
            r2.put("id", null);
            r2.put("note", "second");
            df.addRow(r2);

            CupOfTea deduped = df.copy().dropDuplicates(Keep.FIRST, "id");
            assertThat(deduped.size()).isEqualTo(1);
            assertThat(deduped.getRow(0).getString("note")).isEqualTo("first");

            CupOfTea dedupedLast = df.copy().dropDuplicates(Keep.LAST, "id");
            assertThat(dedupedLast.size()).isEqualTo(1);
            assertThat(dedupedLast.getRow(0).getString("note")).isEqualTo("second");

            CupOfTea dedupedNone = df.copy().dropDuplicates(Keep.NONE, "id");
            assertThat(dedupedNone.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("dropDuplicates() edge case: empty DataFrame")
        void test_dropDuplicates_emptyDataFrame() {
            CupOfTea empty = new CupOfTea();
            empty.addColumn("a", Integer.class);

            assertThat(empty.dropDuplicates().size()).isEqualTo(0);
            assertThat(empty.dropDuplicates(Keep.NONE).size()).isEqualTo(0);
        }

        @Test
        @DisplayName("dropDuplicates() throws on non-existent subset column")
        void test_dropDuplicates_nonExistentSubsetThrows() {
            CupOfTea df = createDuplicateFrame();

            assertThatThrownBy(() -> df.dropDuplicates("nonExistent"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("dropDuplicates() respecting MutationMode")
        void test_dropDuplicates_respectingMutationMode() {
            // IN_PLACE
            CupOfTea inPlace = createDuplicateFrame().asInPlace();
            CupOfTea returnedInPlace = inPlace.dropDuplicates();
            assertThat(returnedInPlace).isSameAs(inPlace);
            assertThat(inPlace.size()).isEqualTo(4);

            // COPY_ON_WRITE
            CupOfTea cow = createDuplicateFrame().asCopyOnWrite();
            CupOfTea returnedCow = cow.dropDuplicates();
            assertThat(returnedCow).isNotSameAs(cow);
            assertThat(cow.size()).isEqualTo(6); // original untouched
            assertThat(returnedCow.size()).isEqualTo(4);
            assertThat(returnedCow.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);
        }
    }

    // =========================================================================
    // 4. Random Sampling Tests (sample)
    // =========================================================================

    @Nested
    @DisplayName("Random Sampling (sample)")
    class SampleTests {

        private CupOfTea createPopulationFrame(int n) {
            CupOfTea df = new CupOfTea();
            df.addColumn("id", Integer.class).addColumn("val", String.class);
            for (int i = 0; i < n; i++) {
                df.addRow(Map.of("id", i, "val", "v" + i));
            }
            return df;
        }

        @Test
        @DisplayName("sample(n) without replacement selects exactly n rows")
        void test_sample_withoutReplacement() {
            CupOfTea df = createPopulationFrame(100);

            CupOfTea sample = df.sample(25);
            assertThat(sample.size()).isEqualTo(25);
            assertThat(sample.getHeaders()).hasSize(2);

            // Check no duplicates in sampled rows
            long distinctIds = sample.getRows().stream()
                .map(r -> r.getInt("id"))
                .distinct()
                .count();
            assertThat(distinctIds).isEqualTo(25);
        }

        @Test
        @DisplayName("sample(n, replace=true, seed) allows n > size and is reproducible")
        void test_sample_reproducibleWithSeed() {
            CupOfTea df = createPopulationFrame(5);

            CupOfTea s1 = df.sample(10, true, 42L);
            CupOfTea s2 = df.sample(10, true, 42L);
            CupOfTea s3 = df.sample(10, true, 999L);

            assertThat(s1.size()).isEqualTo(10);
            assertThat(s2.size()).isEqualTo(10);

            // s1 and s2 with same seed must be 100% identical in order and content
            for (int i = 0; i < 10; i++) {
                assertThat(s1.getRow(i).getInt("id")).isEqualTo(s2.getRow(i).getInt("id"));
            }

            // s3 with different seed should produce different order
            boolean allEqual = true;
            for (int i = 0; i < 10; i++) {
                if (!s1.getRow(i).getInt("id").equals(s3.getRow(i).getInt("id"))) {
                    allEqual = false;
                    break;
                }
            }
            assertThat(allEqual).isFalse();
        }

        @Test
        @DisplayName("sample(double fraction, long seed) samples given percentage")
        void test_sample_fraction() {
            CupOfTea df = createPopulationFrame(100);

            CupOfTea sample20 = df.sample(0.20, 123L);
            assertThat(sample20.size()).isEqualTo(20);

            CupOfTea sampleHalf = df.sample(0.50, 123L);
            assertThat(sampleHalf.size()).isEqualTo(50);
        }

        @Test
        @DisplayName("sample() edge cases: 0 rows and empty frame")
        void test_sample_edgeCases() {
            CupOfTea df = createPopulationFrame(10);

            CupOfTea emptySample = df.sample(0);
            assertThat(emptySample.size()).isEqualTo(0);
            assertThat(emptySample.getHeaders()).hasSize(2);

            CupOfTea emptyDf = new CupOfTea();
            emptyDf.addColumn("x", Integer.class);
            assertThat(emptyDf.sample(0).size()).isEqualTo(0);

            // Sampling > 0 from empty frame throws
            assertThatThrownBy(() -> emptyDf.sample(1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("sample() validation: invalid count and fraction throw")
        void test_sample_validationThrows() {
            CupOfTea df = createPopulationFrame(10);

            assertThatThrownBy(() -> df.sample(-1))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> df.sample(15, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot take a larger sample than population when replace=false");

            assertThatThrownBy(() -> df.sample(-0.1))
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> df.sample(1.5, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than 1.0");
        }

        @Test
        @DisplayName("sample() preserves parent MutationMode")
        void test_sample_preservesMutationMode() {
            CupOfTea cow = createPopulationFrame(10).asCopyOnWrite();
            CupOfTea sampledCow = cow.sample(5);
            assertThat(sampledCow).isNotSameAs(cow);
            assertThat(sampledCow.getMutationMode()).isEqualTo(MutationMode.COPY_ON_WRITE);

            CupOfTea inPlace = createPopulationFrame(10).asInPlace();
            CupOfTea sampledInPlace = inPlace.sample(5);
            assertThat(sampledInPlace.getMutationMode()).isEqualTo(MutationMode.IN_PLACE);
        }
    }
}
