package com.cupoftea.table;

import static com.cupoftea.table.Aggregations.count;
import static com.cupoftea.table.Aggregations.listAgg;
import static com.cupoftea.table.Aggregations.max;
import static com.cupoftea.table.Aggregations.mean;
import static com.cupoftea.table.Aggregations.min;
import static com.cupoftea.table.Aggregations.sum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class GroupByPerformanceTest {

    @Test
    @DisplayName("Should perform single-pass aggregation on high row count dataset and populate output column vectors directly")
    void testHighRowCountDirectVectorPopulation() {
        CupOfTea df = new CupOfTea();
        int n = 50_000;
        df.addColumn("category", String.class);
        df.addColumn("val_double", Double.class);
        df.addColumn("val_int", Integer.class);
        df.addColumn("val_long", Long.class);

        String[] categories = new String[]{"Alpha", "Beta", "Gamma", "Delta", "Epsilon"};
        Random rng = new Random(42);

        StringColumnVector catVec = (StringColumnVector) df.getDataFrame().getColumnVector("category");
        DoubleColumnVector dblVec = (DoubleColumnVector) df.getDataFrame().getColumnVector("val_double");
        IntColumnVector intVec = (IntColumnVector) df.getDataFrame().getColumnVector("val_int");
        LongColumnVector lngVec = (LongColumnVector) df.getDataFrame().getColumnVector("val_long");

        for (int i = 0; i < n; i++) {
            catVec.appendString(categories[rng.nextInt(categories.length)]);
            dblVec.appendDouble(rng.nextDouble() * 100.0);
            intVec.appendInt(rng.nextInt(1000));
            lngVec.appendLong(rng.nextLong(1_000_000L));
        }

        CupOfTea res = df.groupBy("category").agg(
            sum("val_double").as("sum_dbl"),
            mean("val_double").as("avg_dbl"),
            min("val_double").as("min_dbl"),
            max("val_double").as("max_dbl"),
            count("val_int").as("cnt_int"),
            sum("val_long").as("sum_lng")
        );

        assertThat(res.size()).isEqualTo(5);

        // Verify output column vectors are concrete columnar vectors
        assertThat(res.getDataFrame().getColumnVector("category")).isInstanceOf(StringColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("sum_dbl")).isInstanceOf(DoubleColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("avg_dbl")).isInstanceOf(DoubleColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("min_dbl")).isInstanceOf(DoubleColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("max_dbl")).isInstanceOf(DoubleColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("cnt_int")).isInstanceOf(IntColumnVector.class);
        assertThat(res.getDataFrame().getColumnVector("sum_lng")).isInstanceOf(LongColumnVector.class);

        // Check sum of counts equals n
        int totalCount = 0;
        for (int r = 0; r < res.size(); r++) {
            totalCount += res.getRow(r).getInt("cnt_int");
            double minVal = res.getRow(r).getDouble("min_dbl");
            double maxVal = res.getRow(r).getDouble("max_dbl");
            double avgVal = res.getRow(r).getDouble("avg_dbl");
            assertThat(minVal).isLessThanOrEqualTo(avgVal);
            assertThat(avgVal).isLessThanOrEqualTo(maxVal);
        }
        assertThat(totalCount).isEqualTo(n);
    }

    @Test
    @DisplayName("Should handle high cardinality with thousands of distinct groups")
    void testHighCardinalityGroups() {
        CupOfTea df = new CupOfTea();
        int numGroups = 5_000;
        int rowsPerGroup = 10;
        int totalRows = numGroups * rowsPerGroup;

        df.addColumn("groupId", Integer.class);
        df.addColumn("val", Double.class);

        IntColumnVector gidVec = (IntColumnVector) df.getDataFrame().getColumnVector("groupId");
        DoubleColumnVector valVec = (DoubleColumnVector) df.getDataFrame().getColumnVector("val");

        for (int g = 0; g < numGroups; g++) {
            for (int r = 0; r < rowsPerGroup; r++) {
                gidVec.appendInt(g);
                valVec.appendDouble(1.5);
            }
        }

        CupOfTea res = df.groupBy("groupId").agg(
            sum("val").as("total"),
            count().as("row_count")
        );

        assertThat(res.size()).isEqualTo(numGroups);
        for (int g = 0; g < numGroups; g++) {
            Row row = res.getRow(g);
            assertThat(row.getInt("groupId")).isEqualTo(g);
            assertThat(row.getDouble("total")).isCloseTo(15.0, offset(1e-6));
            assertThat(row.getInt("row_count")).isEqualTo(rowsPerGroup);
        }
    }

    @Test
    @DisplayName("Should produce identical results in sequential and parallel execution")
    void testParallelConsistency() {
        CupOfTea seqDf = new CupOfTea();
        int totalRows = 20_000;
        seqDf.addColumn("region", String.class);
        seqDf.addColumn("dept", String.class);
        seqDf.addColumn("revenue", Double.class);
        seqDf.addColumn("headcount", Integer.class);

        StringColumnVector regVec = (StringColumnVector) seqDf.getDataFrame().getColumnVector("region");
        StringColumnVector depVec = (StringColumnVector) seqDf.getDataFrame().getColumnVector("dept");
        DoubleColumnVector revVec = (DoubleColumnVector) seqDf.getDataFrame().getColumnVector("revenue");
        IntColumnVector hcVec = (IntColumnVector) seqDf.getDataFrame().getColumnVector("headcount");

        String[] regions = new String[]{"North", "South", "East", "West"};
        String[] depts = new String[]{"Sales", "Engineering", "Legal", "Marketing", "HR"};
        Random rng = new Random(12345);

        for (int i = 0; i < totalRows; i++) {
            regVec.appendString(regions[rng.nextInt(regions.length)]);
            depVec.appendString(depts[rng.nextInt(depts.length)]);
            revVec.appendDouble(rng.nextDouble() * 10_000.0);
            hcVec.appendInt(rng.nextInt(50) + 1);
        }

        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            CupOfTea parDf = seqDf.copy().asParallel(pool).withParallelThreshold(1_000);

            CupOfTea seqRes = seqDf.groupBy("region", "dept").agg(
                sum("revenue").as("rev_sum"),
                mean("revenue").as("rev_mean"),
                min("revenue").as("rev_min"),
                max("revenue").as("rev_max"),
                count("headcount").as("hc_count"),
                sum("headcount").as("hc_sum")
            );

            CupOfTea parRes = parDf.groupBy("region", "dept").agg(
                sum("revenue").as("rev_sum"),
                mean("revenue").as("rev_mean"),
                min("revenue").as("rev_min"),
                max("revenue").as("rev_max"),
                count("headcount").as("hc_count"),
                sum("headcount").as("hc_sum")
            );

            assertThat(parRes.size()).isEqualTo(seqRes.size());
            for (int r = 0; r < seqRes.size(); r++) {
                Row seqRow = seqRes.getRow(r);
                Row parRow = parRes.getRow(r);
                assertThat(parRow.get("region")).isEqualTo(seqRow.get("region"));
                assertThat(parRow.get("dept")).isEqualTo(seqRow.get("dept"));
                assertThat(parRow.getDouble("rev_sum")).isCloseTo(seqRow.getDouble("rev_sum"), offset(1e-4));
                assertThat(parRow.getDouble("rev_mean")).isCloseTo(seqRow.getDouble("rev_mean"), offset(1e-4));
                assertThat(parRow.getDouble("rev_min")).isCloseTo(seqRow.getDouble("rev_min"), offset(1e-4));
                assertThat(parRow.getDouble("rev_max")).isCloseTo(seqRow.getDouble("rev_max"), offset(1e-4));
                assertThat(parRow.getInt("hc_count")).isEqualTo(seqRow.getInt("hc_count"));
                assertThat(parRow.getInt("hc_sum")).isEqualTo(seqRow.getInt("hc_sum"));
            }
        } finally {
            pool.shutdown();
        }
    }
}
