package com.CupOfTea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.exceptions.ConversionException;
import com.CupOfTea.exceptions.SchemaMismatchException;
import com.CupOfTea.utils.interfaces.Order;
import com.CupOfTea.utils.interfaces.Orderer;

public class DataFrameTest {

    final String testData = """
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

    @Test
    @DisplayName("Should remove filtered rows from DataFrame")
    void test_GivenADataFrame_WhenTryingToRemoveSomeFilteredRows_ThenShouldRemoveTheCorrectRowsOutOfTheDataFrame() {
        int expectedRowsNumber = 3;

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).removeRows(a -> Integer.parseInt(a.get("year").toString()) > 2010);

        assertThat(df.getRows()).hasSize(expectedRowsNumber);
        assertThat(df.getRows()).noneMatch(a -> Integer.parseInt(a.get("year").toString()) > 2010);
    }

    @Test
    @DisplayName("Should remove every matching row when consecutive rows match filter (#126)")
    void test_removeRows_consecutiveMatchingRowsAreAllRemoved() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class);
        df.addColumn("match", Boolean.class);

        // 6 rows: row 0 matches, row 1 matches, row 2 matches, row 3 non-match, row 4 matches, row 5 matches
        for (int i = 0; i < 6; i++) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("id", i);
            row.put("match", i != 3);
            df.addRow(row);
        }

        df.removeRows(row -> Boolean.TRUE.equals(row.get("match")));

        assertThat(df.size()).isEqualTo(1);
        assertThat(df.getRow(0).get("id")).isEqualTo(3);
        assertThat(df.getRows()).noneMatch(row -> Boolean.TRUE.equals(row.get("match")));
    }

    @Test
    @DisplayName("Should add computed column created from concatenation of other columns")
    void test_GivenADataFrame_WhenTryingToCreateANewColumnThatIsTheConcatenationOfTheOthers_ShouldAddANewColumnAtTheEndOfTheDataFrame() {
        String expectedFirstRow = "Lexus LFA 2018";
        Class<?> expectedColumnType = String.class;

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .addColumn("FullName", row -> (String) row.get("car") + " " + (String) row.get("model") + " " + row.get("year").toString());

        assertThat((String) df.getRow(0).get("FullName")).isEqualTo(expectedFirstRow);
        assertThat(df.getHeaders().get(df.getHeaders().size() - 1).columnType()).isEqualTo(expectedColumnType);
    }

    @Test
    @DisplayName("Should alter column type from String to Integer")
    void test_givenADataFrameWithANumberColumnIdentifiedAsString_WhenTryingToChangeThisColumnTypeToInteger_ShouldSuccessfullyChangeTheColumnType() {
        String columnToChangeType = "year";
        Integer expectedFirstRowYear = 2018;
        Class<?> expectedColumnType = Integer.class;

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType(columnToChangeType, expectedColumnType);

        assertThat(df.getColumnHeader(columnToChangeType).columnType()).isEqualTo(expectedColumnType);
        assertThat(df.getRow(0).get(columnToChangeType)).isEqualTo(expectedFirstRowYear);
    }

    @Test
    @DisplayName("Should alter column type and preserve null values")
    void test_givenADataFrameWithANumberColumnIdentifiedAsStringAndNullValues_WhenTryingToChangeThisColumnTypeToInteger_ShouldSuccessfullyChangeTheColumnTypeAndPreserveNulls() {
        String columnToChangeType = "year";
        Integer expectedLastRowYear = null;
        Class<?> expectedColumnType = Integer.class;

        CupOfTea df = new CupOfTea();

        HashMap<String, Object> row = new HashMap<>();
        row.put("car", "Gurgel");
        row.put("model", "QT");
        row.put("year", null);
        row.put("price", "3846.01");

        df.importFromJson(testData)
            .addRow(row)
            .alterColumnType(columnToChangeType, expectedColumnType);

        assertThat(df.getColumnHeader(columnToChangeType).columnType()).isEqualTo(expectedColumnType);
        assertThat(df.getRows().get(df.getRows().size() - 1).get(columnToChangeType)).isEqualTo(expectedLastRowYear);
    }

    @Test
    @DisplayName("Should update row keys and header when renaming column by name (#125)")
    void test_givenADataFrame_WhenRenamingAColumnByName_ThenRowKeysAndHeaderShouldUseTheNewName() {
        String oldName = "year";
        String newName = "modelYear";
        Integer expectedFirstRowYear = 2018;

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType(oldName, Integer.class)
            .renameColumn(oldName, newName);

        assertThat(df.getColumnHeader(newName).columnName()).isEqualTo(newName);
        assertThat(df.getColumnHeader(newName).columnType()).isEqualTo(Integer.class);
        assertThat(df.getRow(0).get(oldName)).isNull();
        assertThat(df.getRow(0).get(newName)).isEqualTo(expectedFirstRowYear);
        assertThat(df.getRow(1).get(newName)).isEqualTo(2006);
        assertThat(df.getRows()).allMatch(row -> row.containsKey(newName) && !row.containsKey(oldName));
    }

    @Test
    @DisplayName("Should update row keys when renaming column by index")
    void test_givenADataFrame_WhenRenamingAColumnByIndex_ThenRowKeysShouldUseTheNewName() {
        String oldName = "car";
        String newName = "brand";

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        int carIndex = -1;
        for (int i = 0; i < df.getHeaders().size(); i++) {
            if (oldName.equals(df.getHeaders().get(i).columnName())) {
                carIndex = i;
                break;
            }
        }

        df.renameColumn(carIndex, newName);

        assertThat(df.getColumnHeader(newName).columnName()).isEqualTo(newName);
        assertThat(df.getColumnHeader(newName).columnType()).isEqualTo(String.class);
        assertThat(df.getRow(0).get(oldName)).isNull();
        assertThat(df.getRow(0).get(newName)).isEqualTo("Lexus");
        assertThat(df.getRows()).allMatch(row -> row.containsKey(newName) && !row.containsKey(oldName));
    }

    @Test
    @DisplayName("Should update row keys when renaming multiple columns via HashMap")
    void test_givenADataFrame_WhenRenamingMultipleColumnsWithMap_ThenRowKeysShouldUseNewNames() {
        HashMap<String, String> renames = new HashMap<>();
        renames.put("car", "brand");
        renames.put("year", "modelYear");

        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .renameColumn(renames);

        assertThat(df.getColumnHeader("brand").columnName()).isEqualTo("brand");
        assertThat(df.getColumnHeader("brand").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("modelYear").columnName()).isEqualTo("modelYear");
        assertThat(df.getColumnHeader("modelYear").columnType()).isEqualTo(Integer.class);

        assertThat(df.getRow(0).get("brand")).isEqualTo("Lexus");
        assertThat(df.getRow(0).get("modelYear")).isEqualTo(2018);
        assertThat(df.getRow(0).get("car")).isNull();
        assertThat(df.getRow(0).get("year")).isNull();
    }

    @Test
    @DisplayName("Should be a no-op when renaming column to same name")
    void test_givenADataFrame_WhenRenamingColumnToSameName_ThenShouldBeNoOp() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .renameColumn("year", "year");

        assertThat(df.getColumnHeader("year").columnName()).isEqualTo("year");
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getRow(0).get("year")).isEqualTo(2018);
    }

    @Test
    @DisplayName("Should throw exception when renaming non-existent column")
    void test_givenADataFrame_WhenRenamingNonExistentColumn_ThenShouldThrowException() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        assertThatThrownBy(() -> df.renameColumn("nonExistent", "newName"))
            .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Should preserve column order and column type when moving column by name (#128)")
    void test_givenADataFrame_WhenMovingAColumnByName_ThenHeaderOrderChangesAndColumnTypeIsPreserved() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class);

        df.moveColumn("price", 0);

        assertThat(df.getHeaders().get(0).columnName()).isEqualTo("price");
        assertThat(df.getHeaders().get(0).columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
    }

    @Test
    @DisplayName("Should preserve column order and column type when moving column by index (#128)")
    void test_givenADataFrame_WhenMovingAColumnByIndex_ThenHeaderOrderChangesAndColumnTypeIsPreserved() {
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

        assertThat(df.getHeaders().get(finalIndex).columnName()).isEqualTo("price");
        assertThat(df.getHeaders().get(finalIndex).columnType()).isEqualTo(Double.class);
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
    }

    @Test
    @DisplayName("Should calculate group sum without binary float noise (#145)")
    void test_givenADataFrame_WhenGroupingByAndSumingValues_ThenShouldCreateANewColumnWithTheSumOfTheValues() {
        String columnToSum = "price";
        String columnToGroupBy = "car";
        double expectedSum = 123750.20;

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .groupBy(columnToGroupBy)
            .sum(columnToSum);

        Object sum = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
            .getRow(0).get(columnToSum + "Sum");

        assertThat(((Number) sum).doubleValue()).isCloseTo(expectedSum, offset(1e-4));
    }

    @Test
    @DisplayName("Should ensure filtered DataFrame headers are independent of original (#127)")
    void test_givenADataFrame_WhenFilteringAndModifyingFilteredHeaders_ThenOriginalHeadersShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);
        int originalHeaderCount = df.getHeaders().size();

        CupOfTea filtered = df.filter(row -> "Mitsubishi".equals(row.get("car")));
        filtered.renameColumn("car", "brand")
            .addColumn("extra")
            .removeColumn("price")
            .alterColumnType("year", String.class);

        assertThat(filtered.getHeaders()).isNotSameAs(df.getHeaders());
        assertThat(df.getHeaders()).hasSize(originalHeaderCount);
        assertThat(df.getColumnHeader("car").columnName()).isEqualTo("car");
        assertThat(df.getColumnHeader("price").columnName()).isEqualTo("price");
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Integer.class);
        assertThat(df.getHeaders()).doesNotContain(new Column("brand", null));
        assertThat(df.getHeaders()).doesNotContain(new Column("extra", null));

        assertThat(filtered.getColumnHeader("brand").columnName()).isEqualTo("brand");
        assertThat(filtered.getColumnHeader("extra").columnName()).isEqualTo("extra");
        assertThat(filtered.getColumnHeader("year").columnType()).isEqualTo(String.class);
        assertThat(filtered.getHeaders()).doesNotContain(new Column("car", null));
        assertThat(filtered.getHeaders()).doesNotContain(new Column("price", null));
    }

    @Test
    @DisplayName("Should ensure filtered DataFrame rows are independent of original (#127)")
    void test_givenADataFrame_WhenFilteringAndModifyingFilteredRows_ThenOriginalRowsShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        CupOfTea filtered = df.filter(row -> "Lexus".equals(row.get("car")));
        assertThat(filtered.size()).isEqualTo(1);
        assertThat(filtered.getRow(0)).isNotSameAs(df.getRow(0));

        filtered.getRow(0).put("model", "IS300");
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("year", 2024);
        filtered.update(updates);
        filtered.renameColumn("car", "brand");

        assertThat(df.getRow(0).get("car")).isEqualTo("Lexus");
        assertThat(df.getRow(0).get("model")).isEqualTo("LFA");
        assertThat(df.getRow(0).get("year")).isEqualTo(2018);
        assertThat(df.getRow(0).get("brand")).isNull();

        assertThat(filtered.getRow(0).get("brand")).isEqualTo("Lexus");
        assertThat(filtered.getRow(0).get("model")).isEqualTo("IS300");
        assertThat(filtered.getRow(0).get("year")).isEqualTo(2024);
        assertThat(filtered.getRow(0).get("car")).isNull();
    }

    @Test
    @DisplayName("Should ensure modifying original DataFrame does not affect filtered frame")
    void test_givenADataFrame_WhenModifyingOriginalAfterFilter_ThenFilteredDataFrameShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        CupOfTea filtered = df.filter(row -> "Lexus".equals(row.get("car")));

        df.renameColumn("car", "brand");
        df.removeColumn("model");
        df.getRow(0).put("year", 1990);

        assertThat(filtered.getRow(0).get("car")).isEqualTo("Lexus");
        assertThat(filtered.getRow(0).get("model")).isEqualTo("LFA");
        assertThat(filtered.getRow(0).get("year")).isEqualTo(2018);
        assertThat(filtered.getRow(0).get("brand")).isNull();
        assertThat(filtered.getColumnHeader("car").columnName()).isEqualTo("car");
        assertThat(filtered.getColumnHeader("model").columnName()).isEqualTo("model");
    }

    @Test
    @DisplayName("Should ensure modifying grouped DataFrame does not affect original frame")
    void test_givenADataFrame_WhenGroupingByAndModifyingGroupedDataFrame_ThenOriginalDataFrameShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);
        int originalHeaderCount = df.getHeaders().size();
        int originalRowCount = df.size();

        CupOfTea grouped = df.groupBy("car");
        grouped.sum("price")
            .renameColumn("car", "brand")
            .addColumn("note");
        grouped.getRow(0).put("brand", "ModifiedBrand");

        assertThat(grouped.getHeaders()).isNotSameAs(df.getHeaders());
        assertThat(df.getHeaders()).hasSize(originalHeaderCount);
        assertThat(df.size()).isEqualTo(originalRowCount);
        assertThat(df.getColumnHeader("car").columnName()).isEqualTo("car");
        assertThat(df.getColumnHeader("price").columnType()).isEqualTo(Double.class);
        assertThat(df.getHeaders()).doesNotContain(new Column("priceSum", null));
        assertThat(df.getHeaders()).doesNotContain(new Column("brand", null));
        assertThat(df.getHeaders()).doesNotContain(new Column("note", null));
        assertThat(df.getRow(0).get("car")).isEqualTo("Lexus");
        assertThat(df.getRow(0).get("brand")).isNull();
        assertThat(df.getRow(0).get("priceSum")).isNull();
    }

    @Test
    @DisplayName("Should ensure modifying original frame does not affect grouped frame")
    void test_givenADataFrame_WhenModifyingOriginalAfterGroupBy_ThenGroupedDataFrameShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);

        CupOfTea grouped = df.groupBy("car");

        df.renameColumn("car", "brand");
        df.removeColumn("price");
        df.getRow(0).put("brand", "Changed");

        grouped.sum("price");
        CupOfTea mitsubishi = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));
        assertThat(((Number) mitsubishi.getRow(0).get("priceSum")).doubleValue()).isCloseTo(123750.20, offset(1e-4));
        assertThat(grouped.getColumnHeader("car").columnName()).isEqualTo("car");
    }

    @Test
    @DisplayName("Should ensure modifying filtered group does not affect original grouped frame")
    void test_givenAGroupedDataFrame_WhenFilteringAndModifyingFilteredGroup_ThenOriginalGroupedDataFrameShouldRemainUnchanged() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData).alterColumnType("price", Double.class);

        CupOfTea grouped = df.groupBy("car");
        CupOfTea filteredGroup = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));

        Group filteredPrices = (Group) filteredGroup.getRow(0).get("price");
        filteredPrices.groupedValues.add(100000.0);
        filteredGroup.sum("price");

        assertThat(grouped.getHeaders()).doesNotContain(new Column("priceSum", null));
        grouped.sum("price");
        CupOfTea originalMitsubishi = grouped.filter(row -> "Mitsubishi".equals(row.get("car")));
        assertThat(((Number) originalMitsubishi.getRow(0).get("priceSum")).doubleValue()).isCloseTo(123750.20, offset(1e-4));
        assertThat(((Number) filteredGroup.getRow(0).get("priceSum")).doubleValue()).isCloseTo(223750.20, offset(1e-4));
    }

    @Test
    @DisplayName("Should add average header once and calculate double average (#129)")
    void test_givenADataFrame_WhenGroupingByAndAveragingDoubleValues_ThenShouldAddHeaderOnceAndCalculateAverage() {
        String columnToAverage = "price";
        String columnToGroupBy = "car";
        String averageColumnName = columnToAverage + "Average";
        double expectedMitsubishiAverage = (58749.87 + 65000.33) / 2.0;

        CupOfTea df = new CupOfTea();

        CupOfTea gdf = df.importFromJson(testData)
            .alterColumnType(columnToAverage, Double.class)
            .groupBy(columnToGroupBy)
            .average(columnToAverage);

        long averageHeaderCount = gdf.getHeaders().stream()
            .filter(header -> averageColumnName.equals(header.columnName()))
            .count();

        assertThat(averageHeaderCount).isEqualTo(1L);
        assertThat(gdf.getHeaders()).hasSize(5);
        assertThat(gdf.getColumnHeader(averageColumnName).columnType()).isEqualTo(Double.class);

        Object mitsubishiAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
            .getRow(0).get(averageColumnName);
        Object lexusAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Lexus"))
            .getRow(0).get(averageColumnName);

        assertThat(((Double) mitsubishiAverage)).isCloseTo(expectedMitsubishiAverage, offset(1e-9));
        assertThat(((Double) lexusAverage)).isCloseTo(3500000.99, offset(1e-9));
    }

    @Test
    @DisplayName("Should add average header once and calculate integer average (#129)")
    void test_givenADataFrame_WhenGroupingByAndAveragingIntegerValues_ThenShouldAddHeaderOnceAndCalculateDoubleAverage() {
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

        assertThat(averageHeaderCount).isEqualTo(1L);
        assertThat(gdf.getHeaders()).hasSize(5);
        assertThat(gdf.getColumnHeader(averageColumnName).columnType()).isEqualTo(Double.class);

        Object mitsubishiAverage = gdf.filter(a -> a.get(columnToGroupBy).equals("Mitsubishi"))
            .getRow(0).get(averageColumnName);

        assertThat(((Double) mitsubishiAverage)).isCloseTo(2015.0, offset(1e-9));
    }

    @Test
    @DisplayName("Should throw SchemaMismatchException when calling average on non-grouped column")
    void test_givenADataFrame_WhenCallingAverageOnNonGroupedColumn_ThenShouldThrowException() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class);

        assertThatThrownBy(() -> df.average("price"))
            .isInstanceOf(SchemaMismatchException.class)
            .hasMessageContaining("is not a grouped column");
    }

    @Test
    @DisplayName("Should throw ConversionException for unsupported target column type")
    void test_givenADataFrame_WhenAlteringColumnTypeToUnsupportedType_ThenShouldThrowException() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);

        assertThatThrownBy(() -> df.alterColumnType("year", Object.class))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    @DisplayName("Should convert column properly through multiple type alterations")
    void test_givenADataFrame_WhenAlteringIntegerColumnToFloatAndDoubleAndString_ThenShouldConvertProperly() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .alterColumnType("year", Float.class);

        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Float.class);
        assertThat(df.getRow(0).get("year")).isEqualTo(2018.0f);

        df.alterColumnType("year", Double.class);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("year")).isEqualTo(2018.0);

        df.alterColumnType("year", String.class);
        assertThat(df.getColumnHeader("year").columnType()).isEqualTo(String.class);
        assertThat(df.getRow(0).get("year")).isEqualTo("2018.0");
    }

    @Test
    @DisplayName("Converters must work statically without target/classes filesystem scanning (#132)")
    void test_converterLookup_worksStaticallyWithoutFileSystemScanning() {
        CupOfTea df = new CupOfTea();
        df.addColumn("val", String.class);
        HashMap<String, Object> r = new HashMap<>();
        r.put("val", "123");
        df.addRow(r);

        df.alterColumnType("val", Integer.class);
        assertThat(df.getColumnHeader("val").columnType()).isEqualTo(Integer.class);
        assertThat(df.getRow(0).get("val")).isEqualTo(123);

        df.alterColumnType("val", Double.class);
        assertThat(df.getColumnHeader("val").columnType()).isEqualTo(Double.class);
        assertThat(df.getRow(0).get("val")).isEqualTo(123.0);

        df.alterColumnType("val", Float.class);
        assertThat(df.getColumnHeader("val").columnType()).isEqualTo(Float.class);
        assertThat(df.getRow(0).get("val")).isEqualTo(123.0f);

        df.alterColumnType("val", String.class);
        assertThat(df.getColumnHeader("val").columnType()).isEqualTo(String.class);
        assertThat(df.getRow(0).get("val")).isEqualTo("123.0");
    }

    @Test
    @DisplayName("Should be a no-op when ordering empty DataFrame (#133)")
    void test_givenAnEmptyDataFrame_WhenOrderingByAnyColumn_ThenShouldBeANoOp() {
        CupOfTea df = new CupOfTea();
        df.addColumn("year");

        df.orderBy(new Orderer("year", Order.ASCENDING));

        assertThat(df.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should order extreme integers without arithmetic overflow (#133)")
    void test_givenADataFrameWithExtremeIntegers_WhenOrderingAscendingAndDescending_ThenShouldNotOverflow() {
        CupOfTea df = new CupOfTea();
        df.setHeaders(new ArrayList<>(List.of(new Column("value", Integer.class))));

        for (int v : new int[] { Integer.MAX_VALUE, -2_000_000_000, 0, Integer.MIN_VALUE, 2_000_000_000 }) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("value", v);
            df.addRow(row);
        }

        df.orderBy(new Orderer("value", Order.ASCENDING));
        assertThat(df.getRow(0).get("value")).isEqualTo(Integer.MIN_VALUE);
        assertThat(df.getRow(1).get("value")).isEqualTo(-2_000_000_000);
        assertThat(df.getRow(2).get("value")).isEqualTo(0);
        assertThat(df.getRow(3).get("value")).isEqualTo(2_000_000_000);
        assertThat(df.getRow(4).get("value")).isEqualTo(Integer.MAX_VALUE);

        df.orderBy(new Orderer("value", Order.DESCENDING));
        assertThat(df.getRow(0).get("value")).isEqualTo(Integer.MAX_VALUE);
        assertThat(df.getRow(1).get("value")).isEqualTo(2_000_000_000);
        assertThat(df.getRow(2).get("value")).isEqualTo(0);
        assertThat(df.getRow(3).get("value")).isEqualTo(-2_000_000_000);
        assertThat(df.getRow(4).get("value")).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    @DisplayName("Should order Double, Float, and Long columns correctly (#133)")
    void test_givenADataFrameWithDoubleFloatAndLongColumns_WhenOrderingByEach_ThenShouldOrderCorrectly() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("price", Double.class)
            .orderBy(new Orderer("price", Order.ASCENDING));

        assertThat(df.getRow(0).get("price")).isEqualTo(500.01);
        assertThat(df.getRow(1).get("price")).isEqualTo(25000.00);
        assertThat(df.getRow(df.size() - 1).get("price")).isEqualTo(7000000.77);

        df.orderBy(new Orderer("price", Order.DESCENDING));
        assertThat(df.getRow(0).get("price")).isEqualTo(7000000.77);
        assertThat(df.getRow(df.size() - 1).get("price")).isEqualTo(500.01);

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
        assertThat(longDf.getRow(0).get("id")).isEqualTo(Long.MIN_VALUE);
        assertThat(longDf.getRow(1).get("id")).isEqualTo(-100L);
        assertThat(longDf.getRow(2).get("id")).isEqualTo(0L);
        assertThat(longDf.getRow(3).get("id")).isEqualTo(Long.MAX_VALUE);

        longDf.orderBy(new Orderer("rating", Order.DESCENDING));
        assertThat(longDf.getRow(0).get("rating")).isEqualTo(9.9f);
        assertThat(longDf.getRow(1).get("rating")).isEqualTo(4.5f);
        assertThat(longDf.getRow(2).get("rating")).isEqualTo(3.3f);
        assertThat(longDf.getRow(3).get("rating")).isEqualTo(1.2f);
    }

    @Test
    @DisplayName("Should order temporal and boolean columns correctly (#133)")
    void test_givenADataFrameWithTemporalAndBooleanColumns_WhenOrderingByThem_ThenShouldOrderCorrectly() {
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

        assertThat(df.getRow(0).get("event")).isEqualTo("A");
        assertThat(df.getRow(1).get("event")).isEqualTo("B");
        assertThat(df.getRow(2).get("event")).isEqualTo("C");

        df.orderBy(
            new Orderer("active", Order.ASCENDING),
            new Orderer("date", Order.DESCENDING)
        );
        assertThat(df.getRow(0).get("event")).isEqualTo("A");
        assertThat(df.getRow(1).get("event")).isEqualTo("C");
        assertThat(df.getRow(2).get("event")).isEqualTo("B");

        CupOfTea localDateDf = new CupOfTea();
        localDateDf.setHeaders(new ArrayList<>(List.of(new Column("day", LocalDate.class))));
        for (LocalDate d : List.of(LocalDate.of(2025, 5, 1), LocalDate.of(2021, 1, 1), LocalDate.of(2023, 3, 15))) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("day", d);
            localDateDf.addRow(row);
        }

        localDateDf.orderBy(new Orderer("day", Order.ASCENDING));
        assertThat(localDateDf.getRow(0).get("day")).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(localDateDf.getRow(1).get("day")).isEqualTo(LocalDate.of(2023, 3, 15));
        assertThat(localDateDf.getRow(2).get("day")).isEqualTo(LocalDate.of(2025, 5, 1));
    }

    @Test
    @DisplayName("Should place null values last when ordering (#133)")
    void test_givenADataFrameWithNullValuesincludingInFirstRow_WhenOrdering_ThenNullsShouldBePlacedLast() {
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

        assertThat(df.getRow(0).get("score")).isEqualTo(10);
        assertThat(df.getRow(1).get("score")).isEqualTo(20);
        assertThat(df.getRow(2).get("score")).isNull();
        assertThat(df.getRow(2).get("name")).isEqualTo("AlphaNull");
        assertThat(df.getRow(3).get("score")).isNull();
        assertThat(df.getRow(3).get("name")).isEqualTo("Bravo");

        df.orderBy(
            new Orderer("score", Order.DESCENDING),
            new Orderer("name", Order.ASCENDING)
        );

        assertThat(df.getRow(0).get("score")).isEqualTo(20);
        assertThat(df.getRow(1).get("score")).isEqualTo(10);
        assertThat(df.getRow(2).get("score")).isNull();
        assertThat(df.getRow(2).get("name")).isEqualTo("AlphaNull");
        assertThat(df.getRow(3).get("score")).isNull();
        assertThat(df.getRow(3).get("name")).isEqualTo("Bravo");
    }

    @Test
    @DisplayName("Should break ties using subsequent orderers when ordering by multiple columns")
    void test_givenADataFrame_WhenOrderingByMultipleColumns_ThenShouldBreakTiesWithSubsequentOrderers() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData)
            .alterColumnType("year", Integer.class)
            .orderBy(
                new Orderer("car", Order.ASCENDING),
                new Orderer("year", Order.DESCENDING)
            );

        assertThat(df.getRow(0).get("car")).isEqualTo("Bugatti");
        assertThat(df.getRow(1).get("car")).isEqualTo("Fiat");
        assertThat(df.getRow(2).get("car")).isEqualTo("Lexus");
        assertThat(df.getRow(3).get("car")).isEqualTo("Mitsubishi");
        assertThat(df.getRow(3).get("year")).isEqualTo(2016);
        assertThat(df.getRow(4).get("car")).isEqualTo("Mitsubishi");
        assertThat(df.getRow(4).get("year")).isEqualTo(2014);
        assertThat(df.getRow(5).get("car")).isEqualTo("Renault");
    }

    @Test
    @DisplayName("Should default computed column type to Object on empty DataFrame (#134)")
    void test_givenAnEmptyDataFrame_WhenAddingAColumnWithColumnCreator_ThenShouldNotThrowAndDefaultTypeToObject() {
        CupOfTea df = new CupOfTea();

        df.addColumn("FullName", row -> row.get("car") + " " + row.get("model"));

        assertThat(df.size()).isEqualTo(0);
        assertThat(df.getHeaders()).hasSize(1);
        assertThat(df.getColumnHeader("FullName").columnName()).isEqualTo("FullName");
        assertThat(df.getColumnHeader("FullName").columnType()).isEqualTo(Object.class);
    }

    @Test
    @DisplayName("Should use explicit column type on empty DataFrame with computed column (#134)")
    void test_givenAnEmptyDataFrame_WhenAddingAColumnWithColumnCreatorAndExplicitType_ThenShouldUseExplicitColumnType() {
        CupOfTea df = new CupOfTea();

        df.addColumn("FullName", String.class, row -> row.get("car") + " " + row.get("model"))
            .addColumn("AgeIn2030", row -> 2030 - (Integer) row.get("year"), Integer.class)
            .addColumn(0, "Tag", String.class, row -> "CAR-" + row.get("car"));

        assertThat(df.size()).isEqualTo(0);
        assertThat(df.getHeaders()).hasSize(3);
        assertThat(df.getHeaders().get(0).columnName()).isEqualTo("Tag");
        assertThat(df.getColumnHeader("Tag").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("FullName").columnType()).isEqualTo(String.class);
        assertThat(df.getColumnHeader("AgeIn2030").columnType()).isEqualTo(Integer.class);
    }

    @Test
    @DisplayName("Should compute column value and infer type once first row is added to empty DataFrame (#134)")
    void test_givenAnEmptyDataFrameWithComputedColumn_WhenAddingFirstRow_ThenShouldComputeColumnValueAndInferTypeFromFirstRow() {
        CupOfTea df = new CupOfTea();

        df.addColumn("car", String.class)
            .addColumn("model", String.class)
            .addColumn("FullName", row -> row.get("car") + " " + row.get("model"));

        assertThat(df.getColumnHeader("FullName").columnType()).isEqualTo(Object.class);

        HashMap<String, Object> firstRow = new HashMap<>();
        firstRow.put("car", "Lexus");
        firstRow.put("model", "LFA");
        df.addRow(firstRow);

        assertThat(df.size()).isEqualTo(1);
        assertThat(df.getRow(0).get("FullName")).isEqualTo("Lexus LFA");
        assertThat(df.getColumnHeader("FullName").columnType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Should infer type from first non-null row when adding computed column")
    void test_givenADataFrameWhereFirstRowComputesNull_WhenAddingColumnWithColumnCreator_ThenShouldInferTypeFromFirstNonNullRow() {
        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("NonLexusYear", row -> "Lexus".equals(row.get("car")) ? null : Integer.valueOf(row.get("year").toString()));

        assertThat(df.getRow(0).get("NonLexusYear")).isNull();
        assertThat(df.getRow(1).get("NonLexusYear")).isEqualTo(2006);
        assertThat(df.getColumnHeader("NonLexusYear").columnType()).isEqualTo(Integer.class);
    }

    @Test
    @DisplayName("Should keep type Object when all computed rows are null until non-null row is added")
    void test_givenADataFrameWhereAllRowsComputeNull_WhenAddingColumnWithColumnCreator_ThenShouldDefaultTypeToObjectUntilNonNullRowAdded() {
        CupOfTea df = new CupOfTea();

        df.importFromJson(testData)
            .addColumn("OptionalTag", row -> "Porsche".equals(row.get("car")) ? "SPORT" : null);

        assertThat(df.getColumnHeader("OptionalTag").columnType()).isEqualTo(Object.class);
        assertThat(df.getRows()).allMatch(row -> row.get("OptionalTag") == null);

        HashMap<String, Object> porscheRow = new HashMap<>();
        porscheRow.put("car", "Porsche");
        porscheRow.put("model", "911");
        porscheRow.put("year", 2022);
        porscheRow.put("price", 150000.0);
        df.addRow(porscheRow);

        assertThat(df.getRow(df.size() - 1).get("OptionalTag")).isEqualTo("SPORT");
        assertThat(df.getColumnHeader("OptionalTag").columnType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Should invoke computed column creator exactly once per row")
    void test_givenADataFrame_WhenAddingColumnWithColumnCreator_ThenCreatorShouldBeInvokedOncePerRow() {
        CupOfTea df = new CupOfTea();
        df.importFromJson(testData);
        AtomicInteger invocations = new AtomicInteger(0);

        df.addColumn("RowNumber", row -> invocations.incrementAndGet());

        assertThat(invocations.get()).isEqualTo(df.size());
        assertThat(df.getColumnHeader("RowNumber").columnType()).isEqualTo(Integer.class);
        assertThat(df.getRow(0).get("RowNumber")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should alter column type to LocalDate")
    void test_givenADataFrame_WhenAlteringColumnTypeToLocalDate_ShouldSuccessfullyConvert() {
        CupOfTea df = new CupOfTea();
        df.addColumn("date");
        HashMap<String, Object> row = new HashMap<>();
        row.put("date", "2023-05-15");
        df.addRow(row);
        df.alterColumnType("date", LocalDate.class);

        assertThat(df.getColumnHeader("date").columnType()).isEqualTo(LocalDate.class);
        assertThat(df.getRow(0).get("date")).isEqualTo(LocalDate.of(2023, 5, 15));
    }

    @Test
    @DisplayName("Should alter column type to Date")
    void test_givenADataFrame_WhenAlteringColumnTypeToDate_ShouldSuccessfullyConvert() {
        CupOfTea df = new CupOfTea();
        df.addColumn("date");
        HashMap<String, Object> row = new HashMap<>();
        row.put("date", "2023-05-15");
        df.addRow(row);
        df.alterColumnType("date", Date.class);

        assertThat(df.getColumnHeader("date").columnType()).isEqualTo(Date.class);
        assertThat(df.getRow(0).get("date")).isInstanceOf(Date.class);
    }

    @Test
    @DisplayName("Should alter column type to Long")
    void test_givenADataFrame_WhenAlteringColumnTypeToLong_ShouldSuccessfullyConvert() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id");
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", "123456789");
        df.addRow(row);
        df.alterColumnType("id", Long.class);

        assertThat(df.getColumnHeader("id").columnType()).isEqualTo(Long.class);
        assertThat(df.getRow(0).get("id")).isEqualTo(123456789L);
    }

    @Test
    @DisplayName("Should alter column type to primitive int")
    void test_givenADataFrame_WhenAlteringColumnTypeToPrimitiveInt_ShouldSuccessfullyConvert() {
        CupOfTea df = new CupOfTea();
        df.addColumn("count");
        HashMap<String, Object> row = new HashMap<>();
        row.put("count", "42");
        df.addRow(row);
        df.alterColumnType("count", int.class);

        assertThat(df.getColumnHeader("count").columnType()).isEqualTo(int.class);
        assertThat(df.getRow(0).get("count")).isEqualTo(42);
    }

    @Test
    @DisplayName("Should throw ConversionException when altering column type to unsupported type")
    void test_givenADataFrame_WhenAlteringColumnTypeToUnsupportedType_ShouldThrowException() {
        CupOfTea df = new CupOfTea();
        df.addColumn("value");
        HashMap<String, Object> row = new HashMap<>();
        row.put("value", "abc");
        df.addRow(row);

        assertThatThrownBy(() -> df.alterColumnType("value", java.util.List.class))
            .isInstanceOf(ConversionException.class);
    }
}
