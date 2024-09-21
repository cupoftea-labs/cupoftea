package com.CupOfTea.table;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class DataFrameTest extends TestCase{

    public void test_GivenADataFrame_WhenTryingToRemoveSomeFilteredRows_ThenShouldRemoveTheCorrectRowsOutOfTheDataFrame() throws Exception {

        int expectedRowsNumber = 3;

        CupOfTea df = new CupOfTea();

        df.importFromJson(
            """
            [
                {
                    "car": "Lexus",
                    "model": "LFA",
                    "year": 2018
                },
                {
                    "car": "Bugatti",
                    "model": "Veyron",
                    "year": 2006
                },
                {
                    "car": "Mitsubishi",
                    "model": "Lancer",
                    "year": 2014
                },
                {
                    "car": "Fiat",
                    "model": "Uno",
                    "year": 1999
                },
                {
                    "car": "Renault",
                    "model": "Scenic",
                    "year": 2008
                }
            ] 
            """
        ).removeRows(a -> Integer.parseInt(a.get("year").toString()) > 2010)
        ;

        assertEquals(expectedRowsNumber, df.getRows().size());

    }

    public void test_GivenADataFrame_WhenTryingToCreateANewColumnThatIsTheConcatenationOfTheOthers_ShouldAddANewColumnAtTheEndOfTheDataFrame() throws Exception {
        
        String expectedFirstRow = "Lexus LFA 2018";
        Class<?> expectedColumnType = String.class;

        CupOfTea df = new CupOfTea();

        df.importFromJson(
            """
            [
                {
                    "car": "Lexus",
                    "model": "LFA",
                    "year": 2018
                },
                {
                    "car": "Bugatti",
                    "model": "Veyron",
                    "year": 2006
                },
                {
                    "car": "Mitsubishi",
                    "model": "Lancer",
                    "year": 2014
                },
                {
                    "car": "Fiat",
                    "model": "Uno",
                    "year": 1999
                },
                {
                    "car": "Renault",
                    "model": "Scenic",
                    "year": 2008
                }
            ] 
            """
        )
        .addColumn("FullName", (row) -> (String) row.get("car") + " " + (String) row.get("model") + " " + ((Integer) row.get("year")).toString())
        ;

        assertEquals(expectedFirstRow, (String) df.getRow(0).get("FullName"));
        assertEquals(expectedColumnType, df.getHeaders().getLast().columnType());

    }

}
