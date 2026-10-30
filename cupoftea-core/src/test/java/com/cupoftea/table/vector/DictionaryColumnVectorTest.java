package com.cupoftea.table.vector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.Aggregations;
import com.cupoftea.table.Categorical;
import com.cupoftea.table.ColumnRef;
import com.cupoftea.table.DataType;
import com.cupoftea.table.Keep;
import com.cupoftea.table.NA;
import com.cupoftea.table.Row;
import com.cupoftea.table.Schema;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

class DictionaryColumnVectorTest {

    @Test
    void testIntCodeDictionaryEncodingAndLookup() {
        DictionaryColumnVector vec = new DictionaryColumnVector("country", 8, true);
        vec.appendString("Brazil");
        vec.appendString("USA");
        vec.appendString("Brazil");
        vec.appendNull();
        vec.appendString("Japan");
        vec.appendString("USA");

        assertTrue(vec.isDictionaryEncoded());
        assertFalse(vec.isShortCodeStorage());
        assertEquals(6, vec.size());
        assertEquals(3, vec.getDictionarySize());
        assertEquals(3, vec.getCardinality());
        assertEquals(List.of("Brazil", "USA", "Japan"), vec.getCategories());
        assertArrayEquals(new String[] { "Brazil", "USA", "Japan" }, vec.getDictionary());

        assertEquals(0, vec.indexOfCategory("Brazil"));
        assertEquals(1, vec.indexOfCategory("USA"));
        assertEquals(2, vec.indexOfCategory("Japan"));
        assertEquals(-1, vec.indexOfCategory("France"));
        assertTrue(vec.containsCategory("Brazil"));
        assertFalse(vec.containsCategory("France"));

        assertArrayEquals(new int[] { 0, 1, 0, -1, 2, 1 }, vec.getCodes());
        assertEquals("Brazil", vec.getString(0));
        assertEquals("USA", vec.getString(1));
        assertEquals("Brazil", vec.getString(2));
        assertNull(vec.getString(3));
        assertTrue(vec.isNull(3));
        assertEquals("Japan", vec.getString(4));
        assertEquals("USA", vec.getString(5));
    }

    @Test
    void testShortCodeStorageAndAutoPromotion() {
        DictionaryColumnVector vec = new DictionaryColumnVector("status", 16, true, true);
        assertTrue(vec.isShortCodeStorage());

        vec.appendString("OPEN");
        vec.appendString("CLOSED");
        vec.appendString("OPEN");
        vec.appendNull();

        assertEquals(4, vec.size());
        assertEquals(2, vec.getDictionarySize());
        assertArrayEquals(new short[] { 0, 1, 0, -1 }, vec.getShortCodes());
        assertEquals((short) 0, vec.getShortCode(0));
        assertEquals((short) 1, vec.getShortCode(1));
        assertEquals((short) -1, vec.getShortCode(3));

        // Expand and re-compact
        vec.expandToIntCodes();
        assertFalse(vec.isShortCodeStorage());
        assertArrayEquals(new int[] { 0, 1, 0, -1 }, vec.getCodes());

        vec.compactToShortCodes();
        assertTrue(vec.isShortCodeStorage());
        assertArrayEquals(new short[] { 0, 1, 0, -1 }, vec.getShortCodes());

        // Auto-promote when exceeding Short.MAX_VALUE (32,767) unique categories
        DictionaryColumnVector highCard = new DictionaryColumnVector("id", 64, true, true);
        int totalCategories = Short.MAX_VALUE + 5;
        for (int i = 0; i < totalCategories; i++) {
            highCard.appendString("cat_" + i);
        }
        assertFalse(highCard.isShortCodeStorage());
        assertEquals(totalCategories, highCard.getDictionarySize());
        assertEquals("cat_0", highCard.getString(0));
        assertEquals("cat_" + (totalCategories - 1), highCard.getString(totalCategories - 1));
        assertThrows(IllegalStateException.class, highCard::compactToShortCodes);
    }

    @Test
    void testFromStringVectorAndDecodeRoundTrip() {
        StringColumnVector strVec = new StringColumnVector("city", 8, true);
        strVec.appendString("SP");
        strVec.appendString("RJ");
        strVec.appendString("SP");
        strVec.appendNull();
        strVec.appendString("BH");

        DictionaryColumnVector dictVec = strVec.toDictionary(true);
        assertTrue(dictVec.isShortCodeStorage());
        assertEquals(3, dictVec.getDictionarySize());
        assertEquals(5, dictVec.size());
        assertEquals("SP", dictVec.getString(0));
        assertEquals("RJ", dictVec.getString(1));
        assertEquals("SP", dictVec.getString(2));
        assertNull(dictVec.getString(3));
        assertEquals("BH", dictVec.getString(4));

        StringColumnVector decoded = dictVec.decode();
        assertEquals(5, decoded.size());
        assertEquals("SP", decoded.getString(0));
        assertEquals("RJ", decoded.getString(1));
        assertEquals("SP", decoded.getString(2));
        assertNull(decoded.getString(3));
        assertEquals("BH", decoded.getString(4));
    }

    @Test
    void testVectorMutationsCopySlicePermuteFilterAndCompactDictionary() {
        DictionaryColumnVector vec = DictionaryColumnVector.fromStrings(
            "tier", new String[] { "Gold", "Silver", "Bronze", "Gold", null, "Platinum" }, true
        );
        assertTrue(vec.isShortCodeStorage());
        assertEquals(4, vec.getDictionarySize());

        // Copy
        DictionaryColumnVector copy = vec.copy();
        assertEquals(vec.size(), copy.size());
        assertEquals(vec.getCategories(), copy.getCategories());

        // Slice & SliceView
        DictionaryColumnVector sliced = vec.slice(1, 5);
        assertEquals(4, sliced.size());
        assertEquals("Silver", sliced.getString(0));
        assertEquals("Bronze", sliced.getString(1));
        assertEquals("Gold", sliced.getString(2));
        assertNull(sliced.getString(3));

        ColumnVectorSlice view = vec.sliceView(1, 4);
        assertEquals(3, view.size());
        assertEquals("Silver", view.getString(0));
        assertEquals(vec.getCode(1), view.getCode(0));

        // Filter retaining only Gold and Silver, then compactDictionary
        DictionaryColumnVector filtered = vec.filterIn("Gold", "Silver");
        assertEquals(3, filtered.size());
        assertEquals(4, filtered.getDictionarySize()); // uncompacted shares dictionary
        filtered.compactDictionary();
        assertEquals(2, filtered.getDictionarySize());
        assertEquals(List.of("Gold", "Silver"), filtered.getCategories());
        assertEquals("Gold", filtered.getString(0));
        assertEquals("Silver", filtered.getString(1));
        assertEquals("Gold", filtered.getString(2));

        // Permute
        filtered.permute(new int[] { 1, 2, 0 });
        assertEquals("Silver", filtered.getString(0));
        assertEquals("Gold", filtered.getString(1));
        assertEquals("Gold", filtered.getString(2));

        // Fillna & Remove
        copy.fillna("Silver");
        assertFalse(copy.hasNulls());
        assertEquals("Silver", copy.getString(4));
        copy.remove(1);
        assertEquals(5, copy.size());
        assertEquals("Bronze", copy.getString(1));
    }

    @Test
    void testCodeAcceleratedFilteringAndValueCounts() {
        DictionaryColumnVector vec = DictionaryColumnVector.fromStrings(
            "dept", new String[] { "Eng", "Sales", "Eng", "HR", null, "Eng", "Sales" }, false
        );

        // eq / notEq / isin masks and boolean vectors
        BooleanColumnVector eqEng = vec.eq("Eng");
        assertEquals(7, eqEng.size());
        assertTrue(eqEng.getBoolean(0));
        assertFalse(eqEng.getBoolean(1));
        assertTrue(eqEng.getBoolean(2));
        assertTrue(eqEng.isNull(4));
        assertTrue(eqEng.getBoolean(5));

        // Fast-path when target is not in dictionary
        BitSet missingMask = vec.computeEqualsMask("Legal");
        assertEquals(0, missingMask.cardinality());
        DictionaryColumnVector emptyFiltered = vec.filterEquals("Legal");
        assertEquals(0, emptyFiltered.size());

        DictionaryColumnVector notEng = vec.filterNotEquals("Eng");
        assertEquals(4, notEng.size()); // Sales, HR, null, Sales

        DictionaryColumnVector inHrOrSales = vec.filterIn(Set.of("HR", "Sales"));
        assertEquals(3, inHrOrSales.size());

        Map<String, Integer> counts = vec.valueCounts(true);
        assertEquals(3, counts.get("Eng"));
        assertEquals(2, counts.get("Sales"));
        assertEquals(1, counts.get("HR"));
        assertEquals(1, counts.get(null));
    }

    @Test
    void testDictionaryLevelStringOperations() {
        CupOfTea df = new CupOfTea(Schema.of("tag", Schema.CATEGORICAL));
        df.addRow(Map.of("tag", "  java-core  "));
        df.addRow(Map.of("tag", "  python-ml  "));
        df.addRow(Map.of("tag", "  java-core  "));

        ColumnRef colRef = df.col("tag");
        assertTrue(colRef.isDictionaryEncoded());
        DictionaryColumnVector dictVec = (DictionaryColumnVector) colRef.getVector();
        assertEquals(2, dictVec.getDictionarySize());

        BooleanColumnVector hasJava = colRef.str.contains("java");
        assertTrue(hasJava.getBoolean(0));
        assertFalse(hasJava.getBoolean(1));
        assertTrue(hasJava.getBoolean(2));

        IntColumnVector lengths = colRef.str.length();
        assertEquals(13, lengths.getInt(0));
        assertEquals(13, lengths.getInt(1));

        // In-place transform mutates dictionary entries in O(cardinality) and keeps DictionaryColumnVector
        colRef.str.trimInPlace();
        colRef.str.toUpperCaseInPlace();
        assertEquals("JAVA-CORE", df.getRow(0).getString("tag"));
        assertEquals("PYTHON-ML", df.getRow(1).getString("tag"));
        assertEquals("JAVA-CORE", df.getRow(2).getString("tag"));
        assertEquals(2, dictVec.getDictionarySize());

        // Transform that collapses two categories into one deduplicates the dictionary automatically
        DictionaryColumnVector collapsed = dictVec.transformCategories(s -> "UNIFIED", false);
        assertEquals(1, collapsed.getDictionarySize());
        assertEquals("UNIFIED", collapsed.getString(0));
        assertEquals("UNIFIED", collapsed.getString(1));
        assertEquals("UNIFIED", collapsed.getString(2));
    }

    @Test
    void testComparisonsMinMaxAndOrderBy() {
        CupOfTea df = new CupOfTea(
            Schema.of("region", Schema.CATEGORICAL, "amount", Schema.INT)
        );
        df.addRow(Row.of("region", "West", "amount", 10));
        df.addRow(Row.of("region", "East", "amount", 20));
        df.addRow(Row.of("region", null, "amount", 5));
        df.addRow(Row.of("region", "Central", "amount", 30));
        df.addRow(Row.of("region", "East", "amount", 15));

        assertEquals("Central", df.minOf("region"));
        assertEquals("West", df.maxOf("region"));

        df.orderBy(new Orderer("region", Order.ASCENDING), new Orderer("amount", Order.ASCENDING));
        assertEquals("Central", df.getRow(0).getString("region"));
        assertEquals("East", df.getRow(1).getString("region"));
        assertEquals(15, df.getRow(1).getInt("amount"));
        assertEquals("East", df.getRow(2).getString("region"));
        assertEquals(20, df.getRow(2).getInt("amount"));
        assertEquals("West", df.getRow(3).getString("region"));
        assertNull(df.getRow(4).getString("region")); // nulls placed last
    }

    @Test
    void testAcceleratedGroupBySingleAndMultiColumnSequentialAndParallel() {
        CupOfTea df = new CupOfTea(
            Schema.of("category", Schema.CATEGORICAL, "subcat", Schema.CATEGORICAL, "sales", Schema.INT)
        );
        String[] cats = { "Electronics", "Books", "Clothing" };
        String[] subs = { "Online", "Store" };
        for (int i = 0; i < 120; i++) {
            df.addRow(Map.of(
                "category", cats[i % cats.length],
                "subcat", subs[i % subs.length],
                "sales", 10
            ));
        }

        // Sequential single-column groupBy
        CupOfTea groupedSeq = df.groupBy("category").agg(
            Aggregations.count("sales").as("cnt"),
            Aggregations.sum("sales").as("total")
        );
        assertEquals(3, groupedSeq.size());
        for (Row r : groupedSeq.getRows()) {
            assertEquals(40, r.getInt("cnt"));
            assertEquals(400, r.getInt("total"));
        }

        // Parallel single-column groupBy
        CupOfTea groupedPar = df.asParallel().withParallelThreshold(10).groupBy("category").agg(
            Aggregations.count("sales").as("cnt"),
            Aggregations.sum("sales").as("total")
        );
        assertEquals(3, groupedPar.size());
        for (Row r : groupedPar.getRows()) {
            assertEquals(40, r.getInt("cnt"));
            assertEquals(400, r.getInt("total"));
        }

        // Multi-column groupBy with dictionary columns
        CupOfTea multiGrouped = df.asParallel().withParallelThreshold(10).groupBy("category", "subcat").sum("sales");
        assertEquals(6, multiGrouped.size());
        for (Row r : multiGrouped.getRows()) {
            assertEquals(200, r.getInt("salesSum"));
        }
    }

    @Test
    void testAcceleratedJoinsOnDictionaryColumns() {
        CupOfTea left = new CupOfTea(
            Schema.of("code", Schema.CATEGORICAL, "leftVal", Schema.INT)
        );
        // Left dictionary order: ["B", "A", "C"]
        left.addRow(Row.of("code", "B", "leftVal", 2));
        left.addRow(Row.of("code", "A", "leftVal", 1));
        left.addRow(Row.of("code", "C", "leftVal", 3));
        left.addRow(Row.of("code", null, "leftVal", 99));

        CupOfTea right = new CupOfTea(
            Schema.of("code", Schema.CATEGORICAL, "rightVal", Schema.STRING)
        );
        // Right dictionary order is intentionally different: ["A", "D", "B"]
        right.addRow(Row.of("code", "A", "rightVal", "Alpha"));
        right.addRow(Row.of("code", "D", "rightVal", "Delta"));
        right.addRow(Row.of("code", "B", "rightVal", "Beta"));

        CupOfTea inner = left.innerJoin(right, "code");
        assertEquals(2, inner.size());
        assertEquals("B", inner.getRow(0).getString("code"));
        assertEquals("Beta", inner.getRow(0).getString("rightVal"));
        assertEquals("A", inner.getRow(1).getString("code"));
        assertEquals("Alpha", inner.getRow(1).getString("rightVal"));

        CupOfTea leftJoined = left.leftJoin(right, "code");
        assertEquals(4, leftJoined.size());

        CupOfTea outerJoined = left.outerJoin(right, "code");
        assertEquals(5, outerJoined.size());
    }

    @Test
    void testDataFrameEncodeDecodeFilterAndDeduplication() {
        CupOfTea df = new CupOfTea(
            Schema.of("status", Schema.STRING, "score", Schema.INT),
            MutationMode.COPY_ON_WRITE
        );
        df.setMutationMode(MutationMode.IN_PLACE);
        df.addRow(Row.of("status", "ACTIVE", "score", 100));
        df.addRow(Row.of("status", "PENDING", "score", 50));
        df.addRow(Row.of("status", "ACTIVE", "score", 80));
        df.addRow(Row.of("status", "ARCHIVED", "score", 10));
        df.addRow(Row.of("status", null, "score", 0));

        assertFalse(df.isDictionaryEncoded("status"));
        df.encodeDictionary(true, "status");
        assertTrue(df.isDictionaryEncoded("status"));
        assertTrue(df.isCategorical("status"));
        assertInstanceOf(DictionaryColumnVector.class, df.getColumnVector("status"));

        CupOfTea activeOnly = df.filterEquals("status", "ACTIVE");
        assertEquals(2, activeOnly.size());
        assertTrue(activeOnly.isDictionaryEncoded("status"));

        CupOfTea notActive = df.filterNotEquals("status", "ACTIVE");
        assertEquals(3, notActive.size());

        CupOfTea inSet = df.filterIn("status", "PENDING", "ARCHIVED");
        assertEquals(2, inSet.size());

        // Deduplication on dictionary column
        CupOfTea deduped = df.copy().dropDuplicates("status");
        assertEquals(4, deduped.size()); // ACTIVE, PENDING, ARCHIVED, null

        // Alter column type between String and Categorical
        df.decodeDictionary("status");
        assertFalse(df.isDictionaryEncoded("status"));
        assertInstanceOf(StringColumnVector.class, df.getColumnVector("status"));

        df.alterColumnType("status", Categorical.class);
        assertTrue(df.isDictionaryEncoded("status"));
        assertEquals(DataType.CATEGORICAL, df.getSchema().getDataType("status"));

        df.alterColumnType("status", String.class);
        assertFalse(df.isDictionaryEncoded("status"));
        assertEquals(DataType.STRING, df.getSchema().getDataType("status"));

        // CategoricalColumnVector alias check
        CategoricalColumnVector catAlias = new CategoricalColumnVector("alias", new String[] { "X", "Y", "X" });
        assertEquals(2, catAlias.getDictionarySize());
        assertEquals("X", catAlias.getString(0));
        assertTrue(NA.isNA(NA.INSTANCE));
    }
}
