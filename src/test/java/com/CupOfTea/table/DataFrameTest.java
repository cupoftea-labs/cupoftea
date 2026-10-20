package com.CupOfTea.table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.utils.interfaces.Order;
import com.CupOfTea.utils.interfaces.Orderer;

import junit.framework.TestCase;

public class DataFrameTest extends TestCase {

    final String testData =  """
        [
            {
                "car": "Lexus",
                "model": "LFA",
                "year": 2018,
                "price": 3500000.99
            },
            {
                "car": "Bugatti",
                "model": "Veyron",
                "year": 2006,
                "price": 7000000.77
            },
            {
                "car": "Mitsubishi",
                "model": "Lancer",
                "year": 2014,
                "price": 58749.87
            },
            {
                "car": "Mitsubishi",
                "model": "Lancer",
                "year": 2016,
                "price": 65000.33
            },
            {
                "car": "Fiat",
                "model": "Uno",
                "year": 1999,
                "price": 500.01
            },
            {
                "car": "Renault",
                "model": "Scenic",
                "year": 2008,
                "price": 25000.00
            }
        ] 
        """;

    public void test_GivenADataFrame_WhenTryingToRemoveSomeFilteredRows_ThenShouldRemoveTheCorrectRowsOutOfTheDataFrame() throws Exception {

        int expectedRowsNumber = 3;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData).removeRows(a -> Integer.parseInt(a.get("year").toString()) > 2010)
        ;

        assertEquals(expectedRowsNumber, df.getRows().size());
        assertTrue(df.getRows().stream().noneMatch(a -> Integer.parseInt(a.get("year").toString()) > 2010));

    }

    public void test_GivenADataFrame_WhenTryingToCreateANewColumnThatIsTheConcatenationOfTheOthers_ShouldAddANewColumnAtTheEndOfTheDataFrame() throws Exception {
        
        String expectedFirstRow = "Lexus LFA 2018";
        Class<?> expectedColumnType = String.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("FullName", (row) -> (String) row.get("car") + " " + (String) row.get("model") + " " + row.get("year").toString())
        ;

        assertEquals(expectedFirstRow, (String) df.getRow(0).get("FullName"));
        assertEquals(expectedColumnType, df.getHeaders().get(df.getHeaders().size() - 1).columnType());

    }

    public void test_givenADataFrameWithANumberColumnIdentifiedAsString_WhenTryingToChangeThisColumnTypeToInteger_ShouldSuccessfullyChangeTheColumnType() throws Exception {

        String columnToChangeType = "year";
        Integer expectedFirstRowYear = 2018;
        Class<?> expectedColumnType = Integer.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .alterColumnType(columnToChangeType, expectedColumnType)
        ;

        assertEquals(expectedColumnType, df.getColumnHeader(columnToChangeType).columnType());
        assertEquals(expectedFirstRowYear, df.getRows().get(0).get(columnToChangeType));

    }

    public void test_givenADataFrameWithAFloatNumberColumnIdentifiedAsString_WhenTryingToChangeThisColumnTypeToFloat_ShouldSuccessfullyChangeTheColumnType() throws Exception {
        String columnToChangeType = "price";
        Float expectedFirstRowYear = Float.valueOf("3500000.99");
        Class<?> expectedColumnType = Float.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .alterColumnType(columnToChangeType, expectedColumnType)
        ;

        assertEquals(expectedColumnType, df.getColumnHeader(columnToChangeType).columnType());
        assertEquals(expectedFirstRowYear, df.getRows().get(0).get(columnToChangeType));
    }

    public void test_givenADataFrameWithADoubleNumberColumnIdentifiedAsString_WhenTryingToChangeThisColumnTypeToDouble_ShouldSuccessfullyChangeTheColumnType() throws Exception {
        String columnToChangeType = "price";
        Double expectedFirstRowYear = Double.valueOf("3500000.99");
        Class<?> expectedColumnType = Double.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .alterColumnType(columnToChangeType, expectedColumnType)
        ;

        assertEquals(expectedColumnType, df.getColumnHeader(columnToChangeType).columnType());
        assertEquals(expectedFirstRowYear, df.getRows().get(0).get(columnToChangeType));
    }

    public void test_givenADataFrameWithANullValueInAColumnIdentifiedAsAString_WhenTryingToChangeThisColumnTypeToInteger_ShouldSuccessfullyChangeTheColumnType() throws Exception {
        String columnToChangeType = "year";
        String expectedLastRowYear = null;
        Class<?> expectedColumnType = Integer.class;

        CupOfTea df = new CupOfTea();

        HashMap<String,Object> row = new HashMap<>();

        row.put("car", "Gurgel");
        row.put("model", "QT");
        row.put("year", null);
        row.put("price", "3846.01");

        df.importFromJson(testData)
            .addRow(row)
            .alterColumnType(columnToChangeType, expectedColumnType)
        ;

        assertEquals(expectedColumnType, df.getColumnHeader(columnToChangeType).columnType());
        assertEquals(expectedLastRowYear, df.getRows().get(df.getRows().size() - 1).get(columnToChangeType));
    }

    public void test_givenADataFrame_WhenRenamingAColumnByName_ThenRowKeysAndHeaderShouldUseTheNewName() throws Exception {
        String oldName = "year";
        String newName = "modelYear";
        Integer expectedFirstRowYear = 2018;

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType(oldName, Integer.class)
            .renameColumn(oldName, newName);

        assertEquals(newName, df.getColumnHeader(newName).columnName());
        assertEquals(Integer.class, df.getColumnHeader(newName).columnType());
        assertNull(df.getRow(0).get(oldName));
        assertEquals(expectedFirstRowYear, df.getRow(0).get(newName));
        assertEquals(Integer.valueOf(2006), df.getRow(1).get(newName));
        assertEquals(df.size().longValue(), df.getRows().stream().filter(row -> row.containsKey(newName)).count());
        assertEquals(0L, df.getRows().stream().filter(row -> row.containsKey(oldName)).count());
    }

    public void test_givenADataFrame_WhenRenamingAColumnByIndex_ThenRowKeysShouldUseTheNewName() throws Exception {
        String oldName = "car";
        String newName = "brand";

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType(oldName, String.class);
        int columnIndex = 0;
        for (int i = 0; i < df.getHeaders().size(); i++) {
            if (oldName.equals(df.getHeaders().get(i).columnName())) {
                columnIndex = i;
                break;
            }
        }

        df.renameColumn(columnIndex, newName);

        assertEquals("Lexus", df.getRow(0).get(newName));
        assertNull(df.getRow(0).get(oldName));
        assertEquals(newName, df.getHeaders().get(columnIndex).columnName());
        assertEquals(String.class, df.getHeaders().get(columnIndex).columnType());
    }

    public void test_givenADataFrame_WhenRenamingSeveralColumnsAtOnce_ThenAllRowKeysShouldBeUpdated() throws Exception {
        CupOfTea df = new CupOfTea();
        HashMap<String, String> renames = new HashMap<>();
        renames.put("car", "brand");
        renames.put("model", "version");

        df.importFromJson(testData)
            .alterColumnType("car", String.class)
            .alterColumnType("model", String.class)
            .renameColumn(renames);

        assertEquals("Lexus", df.getRow(0).get("brand"));
        assertEquals("LFA", df.getRow(0).get("version"));
        assertNull(df.getRow(0).get("car"));
        assertNull(df.getRow(0).get("model"));
        assertEquals(String.class, df.getColumnHeader("brand").columnType());
        assertEquals(String.class, df.getColumnHeader("version").columnType());
    }

    public void test_givenADataFrame_WhenRenamingAColumnToTheSameName_ThenRowValuesShouldBeKept() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("car", String.class)
            .renameColumn("car", "car");

        assertEquals("Lexus", df.getRow(0).get("car"));
        assertEquals("car", df.getColumnHeader("car").columnName());
        assertEquals(String.class, df.getColumnHeader("car").columnType());
    }

    public void test_givenADataFrame_WhenMovingAColumnByName_ThenHeaderOrderChangesAndColumnTypeIsPreserved() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .moveColumn("year", 0);

        assertEquals("year", df.getHeaders().get(0).columnName());
        assertEquals(Integer.class, df.getHeaders().get(0).columnType());
        assertEquals(Integer.class, df.getColumnHeader("year").columnType());
    }

    public void test_givenADataFrame_WhenMovingAColumnByIndex_ThenHeaderOrderChangesAndColumnTypeIsPreserved() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class);

        int initialIndex = -1;
        for (int i = 0; i < df.getHeaders().size(); i++) {
            if ("price".equals(df.getHeaders().get(i).columnName())) {
                initialIndex = i;
                break;
            }
        }
        int finalIndex = (initialIndex == 0) ? df.getHeaders().size() - 1 : 0;

        df.moveColumn(initialIndex, finalIndex);

        assertEquals("price", df.getHeaders().get(finalIndex).columnName());
        assertEquals(Double.class, df.getHeaders().get(finalIndex).columnType());
        assertEquals(Double.class, df.getColumnHeader("price").columnType());
    }

    public void test_givenADataFrame_WhenGroupingByAndSumingValues_ThenShouldCreateANewColumnWithTheSumOfTheValues() throws Exception {

        String columnToSum = "price";
        String columnToGroupBy = "car";
        Double expectedSum = 123750.20000000001;
        Class<?> expectedColumnType = Double.class;

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .groupBy(columnToGroupBy)
            .sum(columnToSum)
            .print()
        ;

        Object sum = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
        .getRow(0).get(columnToSum+"Sum");

        assertEquals(expectedSum, sum);

    }

    public void test_givenADataFrame_WhenFilteringAndModifyingFilteredHeaders_ThenOriginalHeadersShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);
        int originalHeaderCount = df.getHeaders().size();

        CupOfTea filtered = df.filter(row -> "Mitsubishi".equals(row.get("car")));
        filtered.renameColumn("car", "brand")
            .addColumn("extra")
            .removeColumn("price")
            .alterColumnType("year", String.class);

        assertNotSame(df.getHeaders(), filtered.getHeaders());
        assertEquals(originalHeaderCount, df.getHeaders().size());
        assertEquals("car", df.getColumnHeader("car").columnName());
        assertEquals("price", df.getColumnHeader("price").columnName());
        assertEquals(Integer.class, df.getColumnHeader("year").columnType());
        assertFalse(df.getHeaders().contains(new Column("brand", null)));
        assertFalse(df.getHeaders().contains(new Column("extra", null)));

        assertEquals("brand", filtered.getColumnHeader("brand").columnName());
        assertEquals("extra", filtered.getColumnHeader("extra").columnName());
        assertEquals(String.class, filtered.getColumnHeader("year").columnType());
        assertFalse(filtered.getHeaders().contains(new Column("car", null)));
        assertFalse(filtered.getHeaders().contains(new Column("price", null)));
    }

    public void test_givenADataFrame_WhenFilteringAndModifyingFilteredRows_ThenOriginalRowsShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        CupOfTea filtered = df.filter(row -> "Lexus".equals(row.get("car")));
        assertEquals(1, filtered.size().intValue());
        assertNotSame(df.getRow(0), filtered.getRow(0));

        filtered.getRow(0).put("model", "IS300");
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("year", 2024);
        filtered.update(updates);
        filtered.renameColumn("car", "brand");

        assertEquals("Lexus", df.getRow(0).get("car"));
        assertEquals("LFA", df.getRow(0).get("model"));
        assertEquals(Integer.valueOf(2018), df.getRow(0).get("year"));
        assertNull(df.getRow(0).get("brand"));

        assertEquals("Lexus", filtered.getRow(0).get("brand"));
        assertEquals("IS300", filtered.getRow(0).get("model"));
        assertEquals(Integer.valueOf(2024), filtered.getRow(0).get("year"));
        assertNull(filtered.getRow(0).get("car"));
    }

    public void test_givenADataFrame_WhenModifyingOriginalAfterFilter_ThenFilteredDataFrameShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        CupOfTea filtered = df.filter(row -> "Lexus".equals(row.get("car")));

        df.renameColumn("car", "brand");
        df.removeColumn("model");
        df.getRow(0).put("year", 1990);

        assertEquals("Lexus", filtered.getRow(0).get("car"));
        assertEquals("LFA", filtered.getRow(0).get("model"));
        assertEquals(Integer.valueOf(2018), filtered.getRow(0).get("year"));
        assertNull(filtered.getRow(0).get("brand"));
        assertEquals("car", filtered.getColumnHeader("car").columnName());
        assertEquals("model", filtered.getColumnHeader("model").columnName());
    }

    public void test_givenADataFrame_WhenGroupingByAndModifyingGroupedDataFrame_ThenOriginalDataFrameShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);
        int originalHeaderCount = df.getHeaders().size();
        int originalRowCount = df.size();

        CupOfTea grouped = df.groupBy("car");
        grouped.sum("price")
            .renameColumn("car", "brand")
            .addColumn("note");
        grouped.getRow(0).put("brand", "ModifiedBrand");

        assertNotSame(df.getHeaders(), grouped.getHeaders());
        assertEquals(originalHeaderCount, df.getHeaders().size());
        assertEquals(originalRowCount, df.size().intValue());
        assertEquals("car", df.getColumnHeader("car").columnName());
        assertEquals(Double.class, df.getColumnHeader("price").columnType());
        assertFalse(df.getHeaders().contains(new Column("priceSum", null)));
        assertFalse(df.getHeaders().contains(new Column("brand", null)));
        assertFalse(df.getHeaders().contains(new Column("note", null)));
        assertEquals("Lexus", df.getRow(0).get("car"));
        assertNull(df.getRow(0).get("brand"));
        assertNull(df.getRow(0).get("priceSum"));
    }

    public void test_givenADataFrame_WhenModifyingOriginalAfterGroupBy_ThenGroupedDataFrameShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);

        CupOfTea grouped = df.groupBy("car");

        df.renameColumn("car", "brand");
        df.removeColumn("price");
        df.getRow(0).put("brand", "Changed");

        grouped.sum("price");
        CupOfTea mitsubishi = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));
        assertEquals(Double.valueOf(123750.20000000001), mitsubishi.getRow(0).get("priceSum"));
        assertEquals("car", grouped.getColumnHeader("car").columnName());
    }

    public void test_givenAGroupedDataFrame_WhenFilteringAndModifyingFilteredGroup_ThenOriginalGroupedDataFrameShouldRemainUnchanged() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);

        CupOfTea grouped = df.groupBy("car");
        CupOfTea filteredGroup = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));

        Group filteredPrices = (Group) filteredGroup.getRow(0).get("price");
        filteredPrices.groupedValues.add(100000.0);
        filteredGroup.sum("price");

        assertFalse(grouped.getHeaders().contains(new Column("priceSum", null)));
        grouped.sum("price");
        CupOfTea originalMitsubishi = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));
        assertEquals(Double.valueOf(123750.20000000001), originalMitsubishi.getRow(0).get("priceSum"));
        assertEquals(Double.valueOf(223750.20000000001), filteredGroup.getRow(0).get("priceSum"));
    }

    public void test_givenADataFrame_WhenGroupingByAndAveragingDoubleValues_ThenShouldAddHeaderOnceAndCalculateAverage() throws Exception {
        String columnToAverage = "price";
        String columnToGroupBy = "car";
        String averageColumnName = columnToAverage + "Average";
        Double expectedMitsubishiAverage = (58749.87 + 65000.33) / 2.0;

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .alterColumnType(columnToAverage, Double.class)
            .groupBy(columnToGroupBy)
            .average(columnToAverage);

        long averageHeaderCount = gdf.getHeaders().stream()
            .filter(header -> averageColumnName.equals(header.columnName()))
            .count();

        assertEquals(1L, averageHeaderCount);
        assertEquals(5, gdf.getHeaders().size());
        assertEquals(Double.class, gdf.getColumnHeader(averageColumnName).columnType());

        Object mitsubishiAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
            .getRow(0).get(averageColumnName);
        Object lexusAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Lexus"))
            .getRow(0).get(averageColumnName);

        assertEquals(expectedMitsubishiAverage, (Double) mitsubishiAverage, 1e-9);
        assertEquals(3500000.99, (Double) lexusAverage, 1e-9);
    }

    public void test_givenADataFrame_WhenGroupingByAndAveragingIntegerValues_ThenShouldAddHeaderOnceAndCalculateDoubleAverage() throws Exception {
        String columnToAverage = "year";
        String columnToGroupBy = "car";
        String averageColumnName = columnToAverage + "Average";

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .alterColumnType(columnToAverage, Integer.class)
            .groupBy(columnToGroupBy)
            .average(columnToAverage);

        long averageHeaderCount = gdf.getHeaders().stream()
            .filter(header -> averageColumnName.equals(header.columnName()))
            .count();

        assertEquals(1L, averageHeaderCount);
        assertEquals(5, gdf.getHeaders().size());
        assertEquals(Double.class, gdf.getColumnHeader(averageColumnName).columnType());

        Object mitsubishiAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
            .getRow(0).get(averageColumnName);

        assertEquals(2015.0, (Double) mitsubishiAverage, 1e-9);
    }

    public void test_givenADataFrame_WhenCallingAverageOnNonGroupedColumn_ThenShouldThrowException() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class);

        try {
            df.average("price");
            fail("Expected Exception when calling average on a non-grouped column");
        } catch (Exception e) {
            assertEquals("This is not a grouped column", e.getMessage());
        }
    }

    public void test_givenADataFrame_WhenAlteringColumnTypeToUnsupportedType_ThenShouldThrowException() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        try {
            df.alterColumnType("year", Object.class);
            fail("Expected exception for unsupported target column type");
        } catch (Exception e) {
            assertEquals("Column Type not Suported for Conversion", e.getMessage());
        }
    }

    public void test_givenADataFrame_WhenAlteringIntegerColumnToFloatAndDoubleAndString_ThenShouldConvertProperly() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .alterColumnType("year", Float.class);

        assertEquals(Float.class, df.getColumnHeader("year").columnType());
        assertEquals(Float.valueOf(2018.0f), df.getRow(0).get("year"));

        df.alterColumnType("year", Double.class);
        assertEquals(Double.class, df.getColumnHeader("year").columnType());
        assertEquals(Double.valueOf(2018.0), df.getRow(0).get("year"));

        df.alterColumnType("year", String.class);
        assertEquals(String.class, df.getColumnHeader("year").columnType());
        assertEquals("2018.0", df.getRow(0).get("year"));
    }

    public void test_givenAnEmptyDataFrame_WhenOrderingByAnyColumn_ThenShouldBeANoOp() throws Exception {
        CupOfTea df = new CupOfTea();
        df.addColumn("year");

        df.orderBy(new Orderer("year", Order.ASCENDING));

        assertEquals(Integer.valueOf(0), df.size());
    }

    public void test_givenADataFrameWithExtremeIntegers_WhenOrderingAscendingAndDescending_ThenShouldNotOverflow() throws Exception {
        CupOfTea df = new CupOfTea();
        df.setHeaders(new ArrayList<>(List.of(new Column("value", Integer.class))));

        for (int v : new int[] { Integer.MAX_VALUE, -2_000_000_000, 0, Integer.MIN_VALUE, 2_000_000_000 }) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("value", v);
            df.addRow(row);
        }

        df.orderBy(new Orderer("value", Order.ASCENDING));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), df.getRow(0).get("value"));
        assertEquals(Integer.valueOf(-2_000_000_000), df.getRow(1).get("value"));
        assertEquals(Integer.valueOf(0), df.getRow(2).get("value"));
        assertEquals(Integer.valueOf(2_000_000_000), df.getRow(3).get("value"));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), df.getRow(4).get("value"));

        df.orderBy(new Orderer("value", Order.DESCENDING));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), df.getRow(0).get("value"));
        assertEquals(Integer.valueOf(2_000_000_000), df.getRow(1).get("value"));
        assertEquals(Integer.valueOf(0), df.getRow(2).get("value"));
        assertEquals(Integer.valueOf(-2_000_000_000), df.getRow(3).get("value"));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), df.getRow(4).get("value"));
    }

    public void test_givenADataFrameWithDoubleFloatAndLongColumns_WhenOrderingByEach_ThenShouldOrderCorrectly() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class)
            .orderBy(new Orderer("price", Order.ASCENDING));

        assertEquals(Double.valueOf(500.01), df.getRow(0).get("price"));
        assertEquals(Double.valueOf(25000.00), df.getRow(1).get("price"));
        assertEquals(Double.valueOf(7000000.77), df.getRow(df.size() - 1).get("price"));

        df.orderBy(new Orderer("price", Order.DESCENDING));
        assertEquals(Double.valueOf(7000000.77), df.getRow(0).get("price"));
        assertEquals(Double.valueOf(500.01), df.getRow(df.size() - 1).get("price"));

        CupOfTea longDf = new CupOfTea();
        longDf.setHeaders(new ArrayList<>(List.of(
            new Column("id", Long.class),
            new Column("rating", Float.class)
        )));

        long[] ids = { Long.MAX_VALUE, Long.MIN_VALUE, 0L, -100L };
        float[] ratings = { 4.5f, 1.2f, 9.9f, 3.3f };
        for (int i = 0; i < ids.length; i++) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("id", ids[i]);
            row.put("rating", ratings[i]);
            longDf.addRow(row);
        }

        longDf.orderBy(new Orderer("id", Order.ASCENDING));
        assertEquals(Long.valueOf(Long.MIN_VALUE), longDf.getRow(0).get("id"));
        assertEquals(Long.valueOf(-100L), longDf.getRow(1).get("id"));
        assertEquals(Long.valueOf(0L), longDf.getRow(2).get("id"));
        assertEquals(Long.valueOf(Long.MAX_VALUE), longDf.getRow(3).get("id"));

        longDf.orderBy(new Orderer("rating", Order.DESCENDING));
        assertEquals(Float.valueOf(9.9f), longDf.getRow(0).get("rating"));
        assertEquals(Float.valueOf(4.5f), longDf.getRow(1).get("rating"));
        assertEquals(Float.valueOf(3.3f), longDf.getRow(2).get("rating"));
        assertEquals(Float.valueOf(1.2f), longDf.getRow(3).get("rating"));
    }

    public void test_givenADataFrameWithTemporalAndBooleanColumns_WhenOrderingByThem_ThenShouldOrderCorrectly() throws Exception {
        String datedJson = """
            [
                { "event": "C", "date": "2024-12-31", "active": "true" },
                { "event": "A", "date": "2020-01-15", "active": "false" },
                { "event": "B", "date": "2022-06-01", "active": "true" }
            ]
            """;

        CupOfTea df = new CupOfTea();
        df.importFromJson(datedJson)
            .alterColumnType("date", Date.class)
            .alterColumnType("active", Boolean.class)
            .orderBy(new Orderer("date", Order.ASCENDING));

        assertEquals("A", df.getRow(0).get("event"));
        assertEquals("B", df.getRow(1).get("event"));
        assertEquals("C", df.getRow(2).get("event"));

        df.orderBy(
            new Orderer("active", Order.ASCENDING),
            new Orderer("date", Order.DESCENDING)
        );
        assertEquals("A", df.getRow(0).get("event"));
        assertEquals("C", df.getRow(1).get("event"));
        assertEquals("B", df.getRow(2).get("event"));

        CupOfTea localDateDf = new CupOfTea();
        localDateDf.setHeaders(new ArrayList<>(List.of(new Column("day", LocalDate.class))));
        for (LocalDate d : List.of(LocalDate.of(2025, 5, 1), LocalDate.of(2021, 1, 1), LocalDate.of(2023, 3, 15))) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("day", d);
            localDateDf.addRow(row);
        }

        localDateDf.orderBy(new Orderer("day", Order.ASCENDING));
        assertEquals(LocalDate.of(2021, 1, 1), localDateDf.getRow(0).get("day"));
        assertEquals(LocalDate.of(2023, 3, 15), localDateDf.getRow(1).get("day"));
        assertEquals(LocalDate.of(2025, 5, 1), localDateDf.getRow(2).get("day"));
    }

    public void test_givenADataFrameWithNullValuesincludingInFirstRow_WhenOrdering_ThenNullsShouldBePlacedLast() throws Exception {
        CupOfTea df = new CupOfTea();
        df.setHeaders(new ArrayList<>(List.of(
            new Column("score", Integer.class),
            new Column("name", String.class)
        )));

        HashMap<String, Object> row1 = new HashMap<>();
        row1.put("score", null);
        row1.put("name", "Bravo");

        HashMap<String, Object> row2 = new HashMap<>();
        row2.put("score", 20);
        row2.put("name", "Charlie");

        HashMap<String, Object> row3 = new HashMap<>();
        row3.put("score", 10);
        row3.put("name", "Alpha");

        HashMap<String, Object> row4 = new HashMap<>();
        row4.put("score", null);
        row4.put("name", "AlphaNull");

        df.addRow(row1).addRow(row2).addRow(row3).addRow(row4);

        df.orderBy(
            new Orderer("score", Order.ASCENDING),
            new Orderer("name", Order.ASCENDING)
        );

        assertEquals(Integer.valueOf(10), df.getRow(0).get("score"));
        assertEquals(Integer.valueOf(20), df.getRow(1).get("score"));
        assertNull(df.getRow(2).get("score"));
        assertEquals("AlphaNull", df.getRow(2).get("name"));
        assertNull(df.getRow(3).get("score"));
        assertEquals("Bravo", df.getRow(3).get("name"));

        df.orderBy(
            new Orderer("score", Order.DESCENDING),
            new Orderer("name", Order.ASCENDING)
        );

        assertEquals(Integer.valueOf(20), df.getRow(0).get("score"));
        assertEquals(Integer.valueOf(10), df.getRow(1).get("score"));
        assertNull(df.getRow(2).get("score"));
        assertEquals("AlphaNull", df.getRow(2).get("name"));
        assertNull(df.getRow(3).get("score"));
        assertEquals("Bravo", df.getRow(3).get("name"));
    }

    public void test_givenADataFrame_WhenOrderingByMultipleColumns_ThenShouldBreakTiesWithSubsequentOrderers() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .orderBy(
                new Orderer("car", Order.ASCENDING),
                new Orderer("year", Order.DESCENDING)
            );

        assertEquals("Bugatti", df.getRow(0).get("car"));
        assertEquals("Fiat", df.getRow(1).get("car"));
        assertEquals("Lexus", df.getRow(2).get("car"));
        assertEquals("Mitsubishi", df.getRow(3).get("car"));
        assertEquals(Integer.valueOf(2016), df.getRow(3).get("year"));
        assertEquals("Mitsubishi", df.getRow(4).get("car"));
        assertEquals(Integer.valueOf(2014), df.getRow(4).get("year"));
        assertEquals("Renault", df.getRow(5).get("car"));
    }

    public void test_givenAnEmptyDataFrame_WhenAddingAColumnWithColumnCreator_ThenShouldNotThrowAndDefaultTypeToObject() throws Exception {
        CupOfTea df = new CupOfTea();

        df.addColumn("FullName", row -> row.get("car") + " " + row.get("model"));

        assertEquals(0, df.size().intValue());
        assertEquals(1, df.getHeaders().size());
        assertEquals("FullName", df.getColumnHeader("FullName").columnName());
        assertEquals(Object.class, df.getColumnHeader("FullName").columnType());
    }

    public void test_givenAnEmptyDataFrame_WhenAddingAColumnWithColumnCreatorAndExplicitType_ThenShouldUseExplicitColumnType() throws Exception {
        CupOfTea df = new CupOfTea();

        df.addColumn("FullName", String.class, row -> row.get("car") + " " + row.get("model"))
            .addColumn("AgeIn2030", row -> 2030 - (Integer) row.get("year"), Integer.class)
            .addColumn(0, "Tag", String.class, row -> "CAR-" + row.get("car"));

        assertEquals(0, df.size().intValue());
        assertEquals(3, df.getHeaders().size());
        assertEquals("Tag", df.getHeaders().get(0).columnName());
        assertEquals(String.class, df.getColumnHeader("Tag").columnType());
        assertEquals(String.class, df.getColumnHeader("FullName").columnType());
        assertEquals(Integer.class, df.getColumnHeader("AgeIn2030").columnType());
    }

    public void test_givenAnEmptyDataFrameWithComputedColumn_WhenAddingFirstRow_ThenShouldComputeColumnValueAndInferTypeFromFirstRow() throws Exception {
        CupOfTea df = new CupOfTea();

        df.addColumn("car", String.class)
            .addColumn("model", String.class)
            .addColumn("FullName", row -> row.get("car") + " " + row.get("model"));

        assertEquals(Object.class, df.getColumnHeader("FullName").columnType());

        HashMap<String, Object> firstRow = new HashMap<>();
        firstRow.put("car", "Lexus");
        firstRow.put("model", "LFA");
        df.addRow(firstRow);

        assertEquals(1, df.size().intValue());
        assertEquals("Lexus LFA", df.getRow(0).get("FullName"));
        assertEquals(String.class, df.getColumnHeader("FullName").columnType());
    }

    public void test_givenADataFrameWhereFirstRowComputesNull_WhenAddingColumnWithColumnCreator_ThenShouldInferTypeFromFirstNonNullRow() throws Exception {
        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("NonLexusYear", row -> "Lexus".equals(row.get("car")) ? null : Integer.valueOf(row.get("year").toString()));

        assertNull(df.getRow(0).get("NonLexusYear"));
        assertEquals(Integer.valueOf(2006), df.getRow(1).get("NonLexusYear"));
        assertEquals(Integer.class, df.getColumnHeader("NonLexusYear").columnType());
    }

    public void test_givenADataFrameWhereAllRowsComputeNull_WhenAddingColumnWithColumnCreator_ThenShouldDefaultTypeToObjectUntilNonNullRowAdded() throws Exception {
        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("OptionalTag", row -> "Porsche".equals(row.get("car")) ? "SPORT" : null);

        assertEquals(Object.class, df.getColumnHeader("OptionalTag").columnType());
        assertTrue(df.getRows().stream().allMatch(row -> row.get("OptionalTag") == null));

        HashMap<String, Object> porscheRow = new HashMap<>();
        porscheRow.put("car", "Porsche");
        porscheRow.put("model", "911");
        porscheRow.put("year", 2022);
        porscheRow.put("price", 150000.0);
        df.addRow(porscheRow);

        assertEquals("SPORT", df.getRow(df.size() - 1).get("OptionalTag"));
        assertEquals(String.class, df.getColumnHeader("OptionalTag").columnType());
    }

    public void test_givenADataFrame_WhenAddingColumnWithColumnCreator_ThenCreatorShouldBeInvokedOncePerRow() throws Exception {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);
        AtomicInteger invocations = new AtomicInteger(0);

        df.addColumn("RowNumber", row -> invocations.incrementAndGet());

        assertEquals(df.size().intValue(), invocations.get());
        assertEquals(Integer.class, df.getColumnHeader("RowNumber").columnType());
        assertEquals(Integer.valueOf(1), df.getRow(0).get("RowNumber"));
    }
}
