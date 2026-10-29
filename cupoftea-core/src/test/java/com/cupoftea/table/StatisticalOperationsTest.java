package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;

public class StatisticalOperationsTest {

    private CupOfTea createSampleFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", Integer.class)
          .addColumn("name", String.class)
          .addColumn("score", Double.class)
          .addColumn("bonus", Integer.class);

        // Scores: [10, 20, 30, 40, 50]
        df.addRow(Map.of("id", 1, "name", "Alice", "score", 10.0, "bonus", 100));
        df.addRow(Map.of("id", 2, "name", "Bob", "score", 20.0, "bonus", 200));
        df.addRow(Map.of("id", 3, "name", "Charlie", "score", 30.0, "bonus", 300));
        df.addRow(Map.of("id", 4, "name", "Diana", "score", 40.0, "bonus", 400));
        df.addRow(Map.of("id", 5, "name", "Evan", "score", 50.0, "bonus", 500));
        return df;
    }

    // ==========================================
    // 1. ColumnVector Statistical Operations
    // ==========================================

    @Test
    @DisplayName("Primitive vector operations on DoubleColumnVector and IntColumnVector")
    void test_vectorPrimitives() {
        DoubleColumnVector dVec = new DoubleColumnVector("val", 5, true);
        dVec.appendDouble(10.0);
        dVec.appendDouble(20.0);
        dVec.appendDouble(30.0);
        dVec.appendDouble(40.0);
        dVec.appendDouble(50.0);

        assertThat(dVec.validCount()).isEqualTo(5);
        assertThat(dVec.sum()).isEqualTo(150.0);
        assertThat(dVec.mean()).isEqualTo(30.0);
        assertThat(dVec.min()).isEqualTo(10.0);
        assertThat(dVec.max()).isEqualTo(50.0);
        assertThat(dVec.median()).isEqualTo(30.0);
        // Sample variance for [10,20,30,40,50]: sum((x-30)^2)/4 = (400+100+0+100+400)/4 = 1000/4 = 250
        assertThat(dVec.variance()).isEqualTo(250.0);
        assertThat(dVec.var()).isEqualTo(250.0);
        // Population variance: 1000/5 = 200
        assertThat(dVec.variance(false)).isEqualTo(200.0);
        assertThat(dVec.std()).isCloseTo(Math.sqrt(250.0), offset(1e-9));
        assertThat(dVec.std(false)).isCloseTo(Math.sqrt(200.0), offset(1e-9));
        assertThat(dVec.quantile(0.25)).isEqualTo(20.0);
        assertThat(dVec.quantile(0.75)).isEqualTo(40.0);
        // Symmetric distribution -> skewness ~ 0
        assertThat(dVec.skewness()).isCloseTo(0.0, offset(1e-9));

        IntColumnVector iVec = new IntColumnVector("iVal", 5, true);
        iVec.appendInt(10);
        iVec.appendInt(20);
        iVec.appendInt(30);
        iVec.appendInt(40);
        iVec.appendInt(50);
        assertThat(iVec.sum()).isEqualTo(150.0);
        assertThat(iVec.mean()).isEqualTo(30.0);
        assertThat(iVec.min()).isEqualTo(10.0);
        assertThat(iVec.max()).isEqualTo(50.0);
        assertThat(iVec.median()).isEqualTo(30.0);
        assertThat(iVec.variance()).isEqualTo(250.0);
    }

    // ==========================================
    // 2. Core DataFrame & CupOfTea Statistical Methods
    // ==========================================

    @Test
    @DisplayName("DataFrame univariate statistics (mean, median, var, std, quantile, skewness, kurtosis)")
    void test_dataFrameUnivariateStats() {
        CupOfTea df = createSampleFrame();

        assertThat(df.mean("score")).isEqualTo(30.0);
        assertThat(df.min("score")).isEqualTo(10.0);
        assertThat(df.max("score")).isEqualTo(50.0);
        assertThat(df.median("score")).isEqualTo(30.0);
        assertThat(df.variance("score")).isEqualTo(250.0);
        assertThat(df.var("score")).isEqualTo(250.0);
        assertThat(df.std("score")).isCloseTo(Math.sqrt(250.0), offset(1e-9));
        assertThat(df.quantile("score", 0.25)).isEqualTo(20.0);
        assertThat(df.quantile("score", 0.75)).isEqualTo(40.0);
        assertThat(df.quantile("score", 0.90)).isEqualTo(46.0);
        assertThat(df.skewness("score")).isCloseTo(0.0, offset(1e-9));
        assertThat(df.skew("score")).isCloseTo(0.0, offset(1e-9));
        // Kurtosis for 5 uniform/equispaced values
        assertThat(df.kurtosis("score")).isNotNull();
        assertThat(df.kurt("score")).isNotNull();
    }

    @Test
    @DisplayName("Statistics should skip null and NA values")
    void test_statisticsWithMissingValues() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", Double.class);
        df.addRow(new HashMap<>() {{ put("x", 10.0); }});
        df.addRow(new HashMap<>() {{ put("x", null); }});
        df.addRow(new HashMap<>() {{ put("x", Double.NaN); }});
        df.addRow(new HashMap<>() {{ put("x", NA.NA); }});
        df.addRow(new HashMap<>() {{ put("x", 30.0); }});

        // Valid values: [10.0, 30.0]
        assertThat(df.mean("x")).isEqualTo(20.0);
        assertThat(df.median("x")).isEqualTo(20.0);
        assertThat(df.min("x")).isEqualTo(10.0);
        assertThat(df.max("x")).isEqualTo(30.0);
        // Sample variance for [10, 30]: (10-20)^2 + (30-20)^2 / 1 = 200.0
        assertThat(df.variance("x")).isEqualTo(200.0);
        assertThat(df.std("x")).isCloseTo(Math.sqrt(200.0), offset(1e-9));
    }

    // ==========================================
    // 3. Edge Cases: Empty, Single Element, All NA, Zero Variance
    // ==========================================

    @Test
    @DisplayName("Empty frame should return Double.NaN for aggregations")
    void test_emptyFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", Double.class);

        assertThat(df.mean("x")).isNaN();
        assertThat(df.median("x")).isNaN();
        assertThat(df.variance("x")).isNaN();
        assertThat(df.std("x")).isNaN();
        assertThat(df.quantile("x", 0.5)).isNaN();
        assertThat(df.skewness("x")).isNaN();
        assertThat(df.kurtosis("x")).isNaN();
        assertThat(df.min("x")).isNaN();
        assertThat(df.max("x")).isNaN();
    }

    @Test
    @DisplayName("Single element series edge cases")
    void test_singleElement() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", Double.class);
        df.addRow(Map.of("x", 42.0));

        assertThat(df.mean("x")).isEqualTo(42.0);
        assertThat(df.median("x")).isEqualTo(42.0);
        assertThat(df.min("x")).isEqualTo(42.0);
        assertThat(df.max("x")).isEqualTo(42.0);
        assertThat(df.quantile("x", 0.25)).isEqualTo(42.0);
        // Sample variance with n=1 is undefined (NaN)
        assertThat(df.variance("x")).isNaN();
        assertThat(df.std("x")).isNaN();
        // Population variance with n=1 is 0.0
        assertThat(df.variance("x", false)).isEqualTo(0.0);
        assertThat(df.std("x", false)).isEqualTo(0.0);
        // Skewness and kurtosis with n=1 are NaN
        assertThat(df.skewness("x")).isNaN();
        assertThat(df.kurtosis("x")).isNaN();
    }

    @Test
    @DisplayName("All NA series edge cases")
    void test_allNA() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", Double.class);
        df.addRow(new HashMap<>() {{ put("x", null); }});
        df.addRow(new HashMap<>() {{ put("x", Double.NaN); }});
        df.addRow(new HashMap<>() {{ put("x", NA.NA); }});

        assertThat(df.mean("x")).isNaN();
        assertThat(df.median("x")).isNaN();
        assertThat(df.variance("x")).isNaN();
        assertThat(df.std("x")).isNaN();
        assertThat(df.quantile("x", 0.5)).isNaN();
    }

    @Test
    @DisplayName("Zero variance series (all values identical)")
    void test_zeroVariance() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", Double.class);
        df.addRow(Map.of("x", 10.0));
        df.addRow(Map.of("x", 10.0));
        df.addRow(Map.of("x", 10.0));
        df.addRow(Map.of("x", 10.0));

        assertThat(df.mean("x")).isEqualTo(10.0);
        assertThat(df.median("x")).isEqualTo(10.0);
        assertThat(df.variance("x")).isEqualTo(0.0);
        assertThat(df.std("x")).isEqualTo(0.0);
        // Skewness and kurtosis undefined when variance is 0
        assertThat(df.skewness("x")).isNaN();
        assertThat(df.kurtosis("x")).isNaN();
    }

    @Test
    @DisplayName("Invalid column checks")
    void test_invalidColumn() {
        CupOfTea df = createSampleFrame();

        // Non-existent column
        assertThatThrownBy(() -> df.median("nonExistent"))
            .isInstanceOf(SchemaMismatchException.class);

        // Non-numeric column
        assertThatThrownBy(() -> df.median("name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not numeric");
    }

    // ==========================================
    // 4. Bivariate Statistics: Covariance & Correlation
    // ==========================================

    @Test
    @DisplayName("Covariance and Pearson correlation between columns")
    void test_covarianceAndCorrelation() {
        CupOfTea df = createSampleFrame();

        // score = [10, 20, 30, 40, 50], bonus = [100, 200, 300, 400, 500]
        // Perfectly linear: bonus = 10 * score
        // Covariance: cov(x, 10x) = 10 * var(x) = 10 * 250 = 2500
        assertThat(df.covariance("score", "bonus")).isEqualTo(2500.0);
        assertThat(df.cov("score", "bonus")).isEqualTo(2500.0);
        assertThat(df.correlation("score", "bonus")).isCloseTo(1.0, offset(1e-9));
        assertThat(df.corr("score", "bonus")).isCloseTo(1.0, offset(1e-9));

        // Negative correlation
        CupOfTea dfNeg = new CupOfTea();
        dfNeg.addColumn("x", Double.class).addColumn("y", Double.class);
        dfNeg.addRow(Map.of("x", 1.0, "y", 10.0));
        dfNeg.addRow(Map.of("x", 2.0, "y", 8.0));
        dfNeg.addRow(Map.of("x", 3.0, "y", 6.0));
        dfNeg.addRow(Map.of("x", 4.0, "y", 4.0));
        assertThat(dfNeg.correlation("x", "y")).isCloseTo(-1.0, offset(1e-9));
    }

    @Test
    @DisplayName("Correlation matrix corr() returns square DataFrame")
    void test_correlationMatrix() {
        CupOfTea df = createSampleFrame();
        CupOfTea corrMatrix = df.corr();

        // Numeric columns: id, score, bonus (name is string, skipped)
        assertThat(corrMatrix.size()).isEqualTo(3);
        assertThat(corrMatrix.getHeaders()).extracting(Column::columnName)
            .containsExactly("column", "id", "score", "bonus");

        // Diagonal should be 1.0
        assertThat((Double) corrMatrix.loc("score", "score")).isCloseTo(1.0, offset(1e-9));
        assertThat((Double) corrMatrix.loc("bonus", "bonus")).isCloseTo(1.0, offset(1e-9));
        assertThat((Double) corrMatrix.loc("id", "id")).isCloseTo(1.0, offset(1e-9));

        // Off-diagonal symmetry
        double scoreBonus = (Double) corrMatrix.loc("score", "bonus");
        double bonusScore = (Double) corrMatrix.loc("bonus", "score");
        assertThat(scoreBonus).isCloseTo(1.0, offset(1e-9));
        assertThat(bonusScore).isCloseTo(1.0, offset(1e-9));
    }

    // ==========================================
    // 5. Descriptive Summary describe()
    // ==========================================

    @Test
    @DisplayName("describe() returns formatted 8-row summary DataFrame with loc access")
    void test_describe_numeric() {
        CupOfTea df = createSampleFrame();
        CupOfTea summary = df.describe();

        assertThat(summary.size()).isEqualTo(8);
        assertThat(summary.getHeaders()).extracting(Column::columnName)
            .containsExactly("stat", "id", "score", "bonus");

        // Verify metrics for score: [10, 20, 30, 40, 50]
        assertThat((Double) summary.loc("count", "score")).isEqualTo(5.0);
        assertThat((Double) summary.loc("mean", "score")).isEqualTo(30.0);
        assertThat((Double) summary.loc("std", "score")).isCloseTo(Math.sqrt(250.0), offset(1e-9));
        assertThat((Double) summary.loc("min", "score")).isEqualTo(10.0);
        assertThat((Double) summary.loc("25%", "score")).isEqualTo(20.0);
        assertThat((Double) summary.loc("50%", "score")).isEqualTo(30.0);
        assertThat((Double) summary.loc("75%", "score")).isEqualTo(40.0);
        assertThat((Double) summary.loc("max", "score")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("describe() supports temporal columns (LocalDate)")
    void test_describe_temporal() {
        CupOfTea df = new CupOfTea();
        df.addColumn("date", LocalDate.class).addColumn("val", Double.class);
        df.addRow(Map.of("date", LocalDate.of(2023, 1, 1), "val", 100.0));
        df.addRow(Map.of("date", LocalDate.of(2023, 1, 15), "val", 200.0));
        df.addRow(Map.of("date", LocalDate.of(2023, 1, 31), "val", 300.0));

        CupOfTea summary = df.describe();
        assertThat(summary.size()).isEqualTo(8);
        assertThat(summary.getColumnHeader("date")).isNotNull();

        assertThat(summary.loc("count", "date")).isEqualTo(3.0);
        assertThat(summary.loc("min", "date")).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(summary.loc("50%", "date")).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(summary.loc("max", "date")).isEqualTo(LocalDate.of(2023, 1, 31));
        assertThat(summary.loc("std", "date")).isNull();
    }
}
