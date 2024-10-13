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

        int expectedRowsNumber = 4;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData).removeRows(a -> Integer.parseInt(a.get("year").toString()) > 2010)
        ;

        assertEquals(expectedRowsNumber, df.getRows().size());

    }

    public void test_GivenADataFrame_WhenTryingToCreateANewColumnThatIsTheConcatenationOfTheOthers_ShouldAddANewColumnAtTheEndOfTheDataFrame() throws Exception {
        
        String expectedFirstRow = "Lexus LFA 2018";
        Class<?> expectedColumnType = String.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("FullName", (row) -> (String) row.get("car") + " " + (String) row.get("model") + " " + (Integer.valueOf((String) row.get("year"))).toString())
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

    public void test_givenADataFrame_WhenGroupingByAndSumingValues_ThenShouldCreateANewColumnWithTheSumOfTheValues() throws Exception {

        String columnToSum = "price";
        String columnToGroupBy = "car";
        Double expectedSum = 123750.20000000001;
        Class<?> expectedColumnType = Double.class;

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .alterColumnType(columnToSum, expectedColumnType)
            .groupBy(columnToGroupBy)
            .sum(columnToSum)
            .print()
        ;

        Object sum = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
        .getRow(0).get(columnToSum+"Sum");

        assertEquals(expectedSum, sum);

    }
}
