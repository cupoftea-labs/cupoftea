package com.CupOfTea.table;

import java.util.HashMap;

import com.CupOfTea.CupOfTea;

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
}
