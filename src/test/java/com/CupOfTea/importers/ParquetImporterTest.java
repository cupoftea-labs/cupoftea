package com.CupOfTea.importers;

import java.io.File;

import com.CupOfTea.CupOfTea;

import junit.framework.TestCase;

public class ParquetImporterTest extends TestCase {

    public void test_GivenAParquetFile_WhenImporting_ShouldReturnACupOfTeaDataFrame() throws Exception {

        int expectedColumnsNumber = 3;
        int expectedRowsNumber = 499;
        Class<?> expectedRowClass = Object.class;

        File testFile = new File("src/test/java/com/CupOfTea/testFiles/parquet_sample.parquet");

        CupOfTea cupOfTea = new CupOfTea();

        cupOfTea.importFromParquet(testFile);

        assertEquals(expectedColumnsNumber, cupOfTea.getHeaders().size());
        assertEquals(expectedRowsNumber, cupOfTea.getRows().size());
        assertEquals(expectedRowClass, cupOfTea.getHeaders().get(0).columnType());
        assertEquals(expectedRowClass, cupOfTea.getHeaders().get(1).columnType());
        assertEquals(expectedRowClass, cupOfTea.getHeaders().get(2).columnType());

    }

}
