package com.cupoftea.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.benchmark.macro.EndToEndIoBenchmark;
import com.cupoftea.benchmark.macro.EndToEndPipelineBenchmark;
import com.cupoftea.benchmark.micro.ColumnarVectorScanBenchmark;
import com.cupoftea.benchmark.micro.MapReduceAggregationBenchmark;
import com.cupoftea.benchmark.micro.ParallelScanAndFilterBenchmark;
import com.cupoftea.benchmark.micro.ZeroCopySlicingBenchmark;
import com.cupoftea.benchmark.scaling.CompetitiveBaselineBenchmark;
import com.cupoftea.benchmark.scaling.DatasetScaleBenchmark;
import com.cupoftea.benchmark.scaling.MultiCoreScalingBenchmark;

/**
 * Smoke tests executing all benchmark logic on small datasets to ensure correctness and prevent regressions.
 */
public class BenchmarkSmokeTest {

    private final Blackhole noopBlackhole = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");

    @Test
    @DisplayName("Should execute ColumnarVectorScanBenchmark without error")
    void testColumnarVectorScan() {
        ColumnarVectorScanBenchmark bench = new ColumnarVectorScanBenchmark();
        bench.size = 1000;
        bench.setup();

        bench.scanSequentialInt(noopBlackhole);
        bench.scanVectorizedInt(noopBlackhole);

        bench.scanSequentialDouble(noopBlackhole);
        bench.scanVectorizedDouble(noopBlackhole);

        bench.scanSequentialLong(noopBlackhole);
        bench.scanVectorizedLong(noopBlackhole);

        bench.scanSequentialFloat(noopBlackhole);
        bench.scanVectorizedFloat(noopBlackhole);

        bench.scanSequentialBoolean(noopBlackhole);
        bench.scanVectorizedBoolean(noopBlackhole);

        bench.scanSequentialString(noopBlackhole);
        bench.scanVectorizedString(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute ParallelScanAndFilterBenchmark across selectivity rates")
    void testParallelScanAndFilter() {
        ParallelScanAndFilterBenchmark bench = new ParallelScanAndFilterBenchmark();
        bench.rows = 2000;

        for (String sel : new String[]{"0.01", "0.10", "0.50", "0.90"}) {
            bench.selectivity = sel;
            bench.setup();
            bench.filterSingleThread(noopBlackhole);
            bench.filterParallel(noopBlackhole);
        }
    }

    @Test
    @DisplayName("Should execute MapReduceAggregationBenchmark across all aggregation operations")
    void testMapReduceAggregation() {
        MapReduceAggregationBenchmark bench = new MapReduceAggregationBenchmark();
        bench.rows = 1000;
        bench.setup();

        bench.sumSequential(noopBlackhole);
        bench.sumParallel(noopBlackhole);

        bench.meanSequential(noopBlackhole);
        bench.meanParallel(noopBlackhole);

        bench.minSequential(noopBlackhole);
        bench.minParallel(noopBlackhole);

        bench.maxSequential(noopBlackhole);
        bench.maxParallel(noopBlackhole);

        bench.medianSequential(noopBlackhole);
        bench.medianParallel(noopBlackhole);

        bench.stdSequential(noopBlackhole);
        bench.stdParallel(noopBlackhole);

        bench.countSequential(noopBlackhole);
        bench.countParallel(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute ZeroCopySlicingBenchmark")
    void testZeroCopySlicing() {
        ZeroCopySlicingBenchmark bench = new ZeroCopySlicingBenchmark();
        bench.size = 1000;
        bench.setup();

        bench.sliceCopyInt(noopBlackhole);
        bench.sliceViewInt(noopBlackhole);
        bench.sliceCopyDouble(noopBlackhole);
        bench.sliceViewDouble(noopBlackhole);
        bench.partitionIntChunks(noopBlackhole);
        bench.partitionDoubleChunks(noopBlackhole);
        bench.scanSliceViewInt(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute EndToEndIoBenchmark")
    void testEndToEndIo() throws IOException {
        EndToEndIoBenchmark bench = new EndToEndIoBenchmark();
        bench.rows = 50;
        bench.setupTrial();
        try {
            bench.ingestCsv(noopBlackhole);
            bench.ingestJson(noopBlackhole);
            bench.ingestParquet(noopBlackhole);
            bench.ingestExcel(noopBlackhole);
            bench.ingestXml(noopBlackhole);

            bench.egressCsv(noopBlackhole);
            bench.egressJson(noopBlackhole);
            bench.egressParquet(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute EndToEndPipelineBenchmark")
    void testEndToEndPipeline() throws IOException {
        EndToEndPipelineBenchmark bench = new EndToEndPipelineBenchmark();
        bench.rows = 200;
        bench.setupTrial();
        try {
            bench.fullPipelineSequential(noopBlackhole);
            bench.fullPipelineParallel(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute MultiCoreScalingBenchmark")
    void testMultiCoreScaling() {
        MultiCoreScalingBenchmark bench = new MultiCoreScalingBenchmark();
        bench.threads = 2;
        bench.rows = 1000;
        bench.setupTrial();
        try {
            bench.parallelAggregation(noopBlackhole);
            bench.parallelFilter(noopBlackhole);
            bench.parallelVectorMap(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute CompetitiveBaselineBenchmark")
    void testCompetitiveBaseline() {
        CompetitiveBaselineBenchmark bench = new CompetitiveBaselineBenchmark();
        bench.rows = 1000;
        bench.setup();

        bench.cupOfTeaFilterSequential(noopBlackhole);
        bench.cupOfTeaFilterParallel(noopBlackhole);
        bench.tablesawFilter(noopBlackhole);

        bench.cupOfTeaSumSequential(noopBlackhole);
        bench.cupOfTeaSumParallel(noopBlackhole);
        bench.tablesawSum(noopBlackhole);

        bench.cupOfTeaGroupBy(noopBlackhole);
        bench.tablesawGroupBy(noopBlackhole);

        bench.cupOfTeaSort(noopBlackhole);
        bench.tablesawSort(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute DatasetScaleBenchmark")
    void testDatasetScale() {
        DatasetScaleBenchmark bench = new DatasetScaleBenchmark();
        bench.rows = 1000;
        bench.setup();

        bench.aggregationSequential(noopBlackhole);
        bench.aggregationParallel(noopBlackhole);

        bench.filterSequential(noopBlackhole);
        bench.filterParallel(noopBlackhole);

        bench.mapSequential(noopBlackhole);
        bench.mapParallel(noopBlackhole);
    }
}
