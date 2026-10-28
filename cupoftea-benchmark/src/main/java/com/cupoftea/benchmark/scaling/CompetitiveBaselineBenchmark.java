package com.cupoftea.benchmark.scaling;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.Table;

/**
 * Comparative benchmarks comparing CupOfTea (sequential and parallel) against Tablesaw
 * for filtering, aggregation, group-by, and sorting operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class CompetitiveBaselineBenchmark {

    @Param({"100000", "500000"})
    public int rows;

    public CupOfTea cotSeq;
    public CupOfTea cotPar;
    public Table tablesawTable;

    @Setup
    public void setup() {
        CupOfTea base = SyntheticDataGenerator.createNumericDataFrame(rows);
        cotSeq = base.asSequential();
        cotPar = base.asParallel();
        tablesawTable = SyntheticDataGenerator.createTablesawTable(rows);
    }

    // --- FILTER ---

    @Benchmark
    public void cupOfTeaFilterSequential(Blackhole bh) {
        CupOfTea res = cotSeq.filter(r -> r.getInt("id") > 100 && r.getDouble("val") > 50.0);
        bh.consume(res.size());
    }

    @Benchmark
    public void cupOfTeaFilterParallel(Blackhole bh) {
        CupOfTea res = cotPar.filter(r -> r.getInt("id") > 100 && r.getDouble("val") > 50.0);
        bh.consume(res.size());
    }

    @Benchmark
    public void tablesawFilter(Blackhole bh) {
        Table res = tablesawTable.where(
            tablesawTable.intColumn("id").isGreaterThan(100.0)
                .and(tablesawTable.doubleColumn("val").isGreaterThan(50.0))
        );
        bh.consume(res.rowCount());
    }

    // --- AGGREGATION (SUM) ---

    @Benchmark
    public void cupOfTeaSumSequential(Blackhole bh) {
        bh.consume(cotSeq.sumOf("val"));
    }

    @Benchmark
    public void cupOfTeaSumParallel(Blackhole bh) {
        bh.consume(cotPar.sumOf("val"));
    }

    @Benchmark
    public void tablesawSum(Blackhole bh) {
        bh.consume(tablesawTable.doubleColumn("val").sum());
    }

    // --- GROUP BY ---

    @Benchmark
    public void cupOfTeaGroupBy(Blackhole bh) {
        CupOfTea res = cotSeq.groupBy("category").sum("val");
        bh.consume(res.size());
    }

    @Benchmark
    public void tablesawGroupBy(Blackhole bh) {
        Table res = tablesawTable.summarize("val", AggregateFunctions.sum).by("category");
        bh.consume(res.rowCount());
    }

    // --- SORT ---

    @Benchmark
    public void cupOfTeaSort(Blackhole bh) {
        CupOfTea res = cotSeq.orderBy(new Orderer("val", Order.ASCENDING));
        bh.consume(res.size());
    }

    @Benchmark
    public void tablesawSort(Blackhole bh) {
        Table res = tablesawTable.sortOn("val");
        bh.consume(res.rowCount());
    }
}
