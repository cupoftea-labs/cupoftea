package com.cupoftea.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.infra.Blackhole;

import com.cupoftea.CupOfTea;
import com.cupoftea.benchmark.data.SyntheticDataGenerator;
import com.cupoftea.benchmark.macro.EndToEndIoBenchmark;
import com.cupoftea.benchmark.macro.EndToEndPipelineBenchmark;
import com.cupoftea.benchmark.micro.ColumnarVectorScanBenchmark;
import com.cupoftea.benchmark.micro.MapReduceAggregationBenchmark;
import com.cupoftea.benchmark.micro.ParallelScanAndFilterBenchmark;
import com.cupoftea.benchmark.micro.SimdCsvScannerBenchmark;
import com.cupoftea.benchmark.micro.ZeroCopySlicingBenchmark;
import com.cupoftea.benchmark.polars.OneBrcStandardBenchmark;
import com.cupoftea.benchmark.polars.PolarsArrowBenchmark;
import com.cupoftea.benchmark.polars.PolarsEgressBenchmark;
import com.cupoftea.benchmark.polars.PolarsFilterVectorBenchmark;
import com.cupoftea.benchmark.polars.PolarsGroupByBenchmark;
import com.cupoftea.benchmark.polars.PolarsIngestBenchmark;
import com.cupoftea.benchmark.polars.PolarsJoinBenchmark;
import com.cupoftea.benchmark.polars.PolarsWindowBenchmark;
import com.cupoftea.benchmark.profile.HardwareCountersProfiler;
import com.cupoftea.benchmark.tpch.TpchAnalyticalBenchmark;
import com.cupoftea.benchmark.tpch.TpchDataGenerator;
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

    @Test
    @DisplayName("Should execute SimdCsvScannerBenchmark across all CSV profiles")
    void testSimdCsvScannerBenchmark() throws IOException {
        SimdCsvScannerBenchmark bench = new SimdCsvScannerBenchmark();
        bench.rows = 200;
        bench.setupTrial();
        try {
            bench.scanStandardWideScalar(noopBlackhole);
            bench.scanStandardWideSimd(noopBlackhole);

            bench.scanHighDensityQuotedScalar(noopBlackhole);
            bench.scanHighDensityQuotedSimd(noopBlackhole);

            bench.scanMixedVariedScalar(noopBlackhole);
            bench.scanMixedVariedSimd(noopBlackhole);

            bench.readStandardWideScalar(noopBlackhole);
            bench.readStandardWideSimd(noopBlackhole);

            bench.readHighDensityQuotedScalar(noopBlackhole);
            bench.readHighDensityQuotedSimd(noopBlackhole);

            bench.readMixedVariedScalar(noopBlackhole);
            bench.readMixedVariedSimd(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute PolarsIngestBenchmark across CSV, TSV, and Parquet modes")
    void testPolarsIngestBenchmark() throws IOException {
        PolarsIngestBenchmark bench = new PolarsIngestBenchmark();
        bench.rows = 200;
        bench.nullRatio = 0.05;
        bench.setupTrial();
        try {
            bench.ingestMixedCsvEager(noopBlackhole);
            bench.ingestMixedCsvLazyScan(noopBlackhole);
            bench.ingestMixedCsvLazyFusedAgg(noopBlackhole);
            bench.ingestMixedTsvEager(noopBlackhole);
            bench.ingestMixedTsvLazyScan(noopBlackhole);
            bench.ingestParquetEager(noopBlackhole);
            bench.ingestParquetLazyPushdown(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute PolarsGroupByBenchmark across low/high cardinality and multivariate aggregations")
    void testPolarsGroupByBenchmark() {
        PolarsGroupByBenchmark bench = new PolarsGroupByBenchmark();
        bench.rows = 500;
        bench.setupTrial();

        bench.groupByLowCardSequential(noopBlackhole);
        bench.groupByLowCardParallel(noopBlackhole);
        bench.groupByHighCardStringParallel(noopBlackhole);
        bench.groupBySkewedZipfIntParallel(noopBlackhole);
        bench.groupByMultiKeyCompositeParallel(noopBlackhole);
        bench.groupByMultivariateSinglePassParallel(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute PolarsJoinBenchmark across 1:1, 1:N, and N:M cardinalities")
    void testPolarsJoinBenchmark() {
        PolarsJoinBenchmark bench = new PolarsJoinBenchmark();
        bench.rows = 500;
        bench.setupTrial();

        bench.innerJoinInt1to1(noopBlackhole);
        bench.leftJoinInt1to1(noopBlackhole);
        bench.innerJoinString1to1(noopBlackhole);
        bench.leftJoinString1to1(noopBlackhole);
        bench.innerJoinInt1toN(noopBlackhole);
        bench.leftJoinString1toN(noopBlackhole);
        bench.innerJoinIntNtoM(noopBlackhole);
        bench.leftJoinStringNtoM(noopBlackhole);
        bench.asOfJoinBackward(noopBlackhole);
        bench.asOfJoinForward(noopBlackhole);
        bench.asOfJoinNearest(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute PolarsFilterVectorBenchmark across selectivity rates and math expressions")
    void testPolarsFilterVectorBenchmark() {
        PolarsFilterVectorBenchmark bench = new PolarsFilterVectorBenchmark();
        bench.rows = 1000;
        bench.setupTrial();

        bench.filterLowSelectivity1PctParallel(noopBlackhole);
        bench.filterMediumSelectivity50PctParallel(noopBlackhole);
        bench.filterHighSelectivity90PctParallel(noopBlackhole);
        bench.filterCompoundBooleanExpressionParallel(noopBlackhole);
        bench.vectorizedMathExpressionSequential(noopBlackhole);
        bench.vectorizedMathExpressionParallel(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute PolarsWindowBenchmark across shift, cumulative, rolling, and ranking operations")
    void testPolarsWindowBenchmark() {
        PolarsWindowBenchmark bench = new PolarsWindowBenchmark();
        bench.rows = 500;
        bench.setupTrial();

        bench.lagShift(noopBlackhole);
        bench.leadShift(noopBlackhole);
        bench.cumsum(noopBlackhole);
        bench.cumprod(noopBlackhole);
        bench.cummin(noopBlackhole);
        bench.cummax(noopBlackhole);
        bench.rollingMeanFixedWindow(noopBlackhole);
        bench.rollingSumFixedWindow(noopBlackhole);
        bench.rowNumber(noopBlackhole);
        bench.rank(noopBlackhole);
        bench.denseRank(noopBlackhole);
        bench.windowPartitionedRank(noopBlackhole);
        bench.windowPartitionedLag(noopBlackhole);
    }

    @Test
    @DisplayName("Should execute OneBrcStandardBenchmark across 1-decimal, 2-decimal, and 3-decimal hybrid fallback modes")
    void testOneBrcStandardBenchmark() throws IOException {
        OneBrcStandardBenchmark bench = new OneBrcStandardBenchmark();
        bench.rows = 500;
        bench.setupTrial();
        try {
            bench.oneBrcLazyStreamingScale1Standard(noopBlackhole);
            bench.oneBrcLazyStreamingScale2Cents(noopBlackhole);
            bench.oneBrcLazyStreamingHybrid3DecimalsFallback(noopBlackhole);

            CupOfTea res1 = OneBrcStandardBenchmark.execute1BrcQuery(bench.fileScale1);
            CupOfTea resHybrid = OneBrcStandardBenchmark.execute1BrcQuery(bench.fileHybrid3Decimals);
            assertThat(res1.size()).isGreaterThan(0);
            assertThat(resHybrid.size()).isEqualTo(res1.size());

            String checksum1 = SyntheticDataGenerator.computeChecksum(res1);
            String checksum1Repeat = SyntheticDataGenerator.computeChecksum(
                    OneBrcStandardBenchmark.execute1BrcQuery(bench.fileScale1));
            assertThat(checksum1).isEqualTo(checksum1Repeat);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute PolarsEgressBenchmark across CSV, TSV, Parquet, and JSON")
    void testPolarsEgressBenchmark() throws IOException {
        PolarsEgressBenchmark bench = new PolarsEgressBenchmark();
        bench.rows = 200;
        bench.setupTrial();
        try {
            bench.writeCsv(noopBlackhole);
            assertThat(bench.outCsvFile.exists()).isTrue();
            assertThat(bench.outCsvFile.length()).isGreaterThan(0);

            bench.writeTsv(noopBlackhole);
            assertThat(bench.outTsvFile.exists()).isTrue();
            assertThat(bench.outTsvFile.length()).isGreaterThan(0);

            bench.writeParquet(noopBlackhole);
            assertThat(bench.outParquetFile.exists()).isTrue();
            assertThat(bench.outParquetFile.length()).isGreaterThan(0);

            bench.writeJson(noopBlackhole);
            assertThat(bench.outJsonFile.exists()).isTrue();
            assertThat(bench.outJsonFile.length()).isGreaterThan(0);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should execute PolarsArrowBenchmark without error")
    void testPolarsArrowBenchmark() {
        PolarsArrowBenchmark bench = new PolarsArrowBenchmark();
        bench.rows = 1000;
        bench.setupTrial();
        try {
            bench.exportToVectorSchemaRoot(noopBlackhole);
            bench.importFromVectorSchemaRoot(noopBlackhole);
            bench.exportToArrowRecordBatch(noopBlackhole);
            bench.importFromArrowRecordBatch(noopBlackhole);
            bench.exportToCDataInterface(noopBlackhole);
            bench.importFromCDataInterface(noopBlackhole);
        } finally {
            bench.tearDownTrial();
        }
    }

    @Test
    @DisplayName("Should resolve BenchmarkRunner preset targets including polars and 1brc")
    void testBenchmarkRunnerPresets() {
        assertThat(BenchmarkRunner.resolvePresetPattern("polars")).isEqualTo("com.cupoftea.benchmark.polars.*");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-comparison")).isEqualTo("com.cupoftea.benchmark.polars.*");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-arrow")).contains("PolarsArrowBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("arrow")).contains("PolarsArrowBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-ingest")).contains("PolarsIngestBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-egress")).contains("PolarsEgressBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-groupby")).contains("PolarsGroupByBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-join")).contains("PolarsJoinBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-filter")).contains("PolarsFilterVectorBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("polars-window")).contains("PolarsWindowBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("window")).contains("PolarsWindowBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("tpch")).contains("TpchAnalyticalBenchmark");
        assertThat(BenchmarkRunner.resolvePresetPattern("1brc")).contains("OneBrcStandardBenchmark");
    }

    @Test
    @DisplayName("Should execute all 22 TPC-H analytical benchmark queries without error")
    void testTpchAnalyticalBenchmark() {
        TpchAnalyticalBenchmark bench = new TpchAnalyticalBenchmark();
        bench.scaleFactor = 0.005; // very small scale for quick smoke verification
        bench.setupTrial();

        bench.q01_pricingSummaryReport(noopBlackhole);
        bench.q01_pricingSummaryReportLazy(noopBlackhole);
        bench.q02_minimumCostSupplier(noopBlackhole);
        bench.q03_shippingPriority(noopBlackhole);
        bench.q04_orderPriorityChecking(noopBlackhole);
        bench.q05_localSupplierVolume(noopBlackhole);
        bench.q06_forecastingRevenueChange(noopBlackhole);
        bench.q06_forecastingRevenueChangeLazy(noopBlackhole);
        bench.q07_volumeShipping(noopBlackhole);
        bench.q08_nationalMarketShare(noopBlackhole);
        bench.q09_productTypeProfitMeasure(noopBlackhole);
        bench.q10_returnedItemReporting(noopBlackhole);
        bench.q11_importantStockIdentification(noopBlackhole);
        bench.q12_shippingModesAndOrderPriority(noopBlackhole);
        bench.q13_customerDistribution(noopBlackhole);
        bench.q14_promotionEffect(noopBlackhole);
        bench.q15_topSupplier(noopBlackhole);
        bench.q16_partsSupplierRelationship(noopBlackhole);
        bench.q17_smallQuantityOrderRevenue(noopBlackhole);
        bench.q18_largeVolumeCustomer(noopBlackhole);
        bench.q19_discountedRevenue(noopBlackhole);
        bench.q20_potentialPartPromotion(noopBlackhole);
        bench.q21_suppliersWhoKeptOrdersWaiting(noopBlackhole);
        bench.q22_globalSalesOpportunity(noopBlackhole);
    }

    @Test
    @DisplayName("Should collect hardware counters or OS RSS via HardwareCountersProfiler")
    void testHardwareCountersProfiler() {
        HardwareCountersProfiler profiler = new HardwareCountersProfiler();
        assertThat(profiler.getDescription()).contains("Hardware PMU");
        profiler.beforeIteration(null, null);
        long sum = 0;
        for (int i = 0; i < 50_000; i++) {
            sum += i;
        }
        noopBlackhole.consume(sum);
        var results = profiler.afterIteration(null, null, null);
        assertThat(results).isNotNull();
    }
}
