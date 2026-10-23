package com.cupoftea.table.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.Schema;
import static com.cupoftea.table.Schema.DOUBLE;
import static com.cupoftea.table.Schema.INT;
import static com.cupoftea.table.Schema.STRING;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

class WindowAnalyticTest {

    @Test
    @DisplayName("lead and lag offsets with null boundaries")
    void testLeadAndLag() {
        CupOfTea df = new CupOfTea(Schema.of("id", INT, "val", STRING));
        for (int i = 0; i < 4; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", i + 1);
            row.put("val", "item" + (i + 1));
            df.addRow(row);
        }

        df.lead("val", 1, "next_val");
        df.lag("val", 1, "prev_val");

        // Row 0: val=item1, next=item2, prev=null
        assertThat(df.getRow(0).get("val")).isEqualTo("item1");
        assertThat(df.getRow(0).get("next_val")).isEqualTo("item2");
        assertThat(df.getRow(0).get("prev_val")).isNull();

        // Row 1: val=item2, next=item3, prev=item1
        assertThat(df.getRow(1).get("next_val")).isEqualTo("item3");
        assertThat(df.getRow(1).get("prev_val")).isEqualTo("item1");

        // Row 3: val=item4, next=null, prev=item3
        assertThat(df.getRow(3).get("next_val")).isNull();
        assertThat(df.getRow(3).get("prev_val")).isEqualTo("item3");
    }

    @Test
    @DisplayName("diff and pctChange discrete difference")
    void testDiffAndPctChange() {
        CupOfTea df = new CupOfTea(Schema.of("price", DOUBLE));
        for (double p : new double[]{10.0, 15.0, 12.0, 24.0}) {
            Map<String, Object> row = new HashMap<>();
            row.put("price", p);
            df.addRow(row);
        }

        df.diff("price", 1, "price_diff");
        df.pctChange("price", 1, "price_pct");

        // Row 0: null diff and pct
        assertThat(df.getRow(0).get("price_diff")).isNull();
        assertThat(df.getRow(0).get("price_pct")).isNull();

        // Row 1: 15 - 10 = 5.0, (15 - 10)/10 = 0.5
        assertThat(((Number) df.getRow(1).get("price_diff")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) df.getRow(1).get("price_pct")).doubleValue()).isCloseTo(0.5, within(1e-6));

        // Row 2: 12 - 15 = -3.0, (12 - 15)/15 = -0.2
        assertThat(((Number) df.getRow(2).get("price_diff")).doubleValue()).isEqualTo(-3.0);
        assertThat(((Number) df.getRow(2).get("price_pct")).doubleValue()).isCloseTo(-0.2, within(1e-6));

        // Row 3: 24 - 12 = 12.0, (24 - 12)/12 = 1.0
        assertThat(((Number) df.getRow(3).get("price_diff")).doubleValue()).isEqualTo(12.0);
        assertThat(((Number) df.getRow(3).get("price_pct")).doubleValue()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("rank, denseRank, percentRank, rowNumber across ordered partitions")
    void testRankingFunctions() {
        CupOfTea df = new CupOfTea(Schema.of("dept", STRING, "salary", INT));
        Object[][] data = {
                {"IT", 70000},
                {"HR", 50000},
                {"IT", 80000},
                {"IT", 80000},
                {"HR", 60000},
                {"IT", 90000}
        };

        for (Object[] r : data) {
            Map<String, Object> row = new HashMap<>();
            row.put("dept", r[0]);
            row.put("salary", r[1]);
            df.addRow(row);
        }

        // Window partition by department, order by salary ascending
        CupOfTea ranked = df.window()
                .partitionBy("dept")
                .orderBy("salary")
                .rank("rank");
        ranked.window()
                .partitionBy("dept")
                .orderBy("salary")
                .denseRank("dense_rank");
        ranked.window()
                .partitionBy("dept")
                .orderBy("salary")
                .percentRank("pct_rank");
        ranked.window()
                .partitionBy("dept")
                .orderBy("salary")
                .rowNumber("row_num");

        // In IT partition: salaries are 70k, 80k, 80k, 90k
        // row 0: IT 70000 -> rank=1, dense=1, row_num=1, pct_rank=0.0
        assertThat(ranked.getRow(0).get("dept")).isEqualTo("IT");
        assertThat(((Number) ranked.getRow(0).get("salary")).intValue()).isEqualTo(70000);
        assertThat(((Number) ranked.getRow(0).get("rank")).intValue()).isEqualTo(1);
        assertThat(((Number) ranked.getRow(0).get("dense_rank")).intValue()).isEqualTo(1);
        assertThat(((Number) ranked.getRow(0).get("row_num")).intValue()).isEqualTo(1);
        assertThat(((Number) ranked.getRow(0).get("pct_rank")).doubleValue()).isEqualTo(0.0);

        // row 2: IT 80000 (tied) -> rank=2, dense=2, row_num=2 or 3, pct_rank=1/3 = 0.333333
        assertThat(ranked.getRow(2).get("dept")).isEqualTo("IT");
        assertThat(((Number) ranked.getRow(2).get("salary")).intValue()).isEqualTo(80000);
        assertThat(((Number) ranked.getRow(2).get("rank")).intValue()).isEqualTo(2);
        assertThat(((Number) ranked.getRow(2).get("dense_rank")).intValue()).isEqualTo(2);
        assertThat(((Number) ranked.getRow(2).get("pct_rank")).doubleValue()).isCloseTo(0.333333, within(1e-4));

        // row 3: IT 80000 (tied) -> rank=2, dense=2, pct_rank=1/3 = 0.333333
        assertThat(ranked.getRow(3).get("dept")).isEqualTo("IT");
        assertThat(((Number) ranked.getRow(3).get("salary")).intValue()).isEqualTo(80000);
        assertThat(((Number) ranked.getRow(3).get("rank")).intValue()).isEqualTo(2);
        assertThat(((Number) ranked.getRow(3).get("dense_rank")).intValue()).isEqualTo(2);

        // row 5: IT 90000 -> rank=4 (gap after two 2s!), dense=3 (no gap!), row_num=4, pct_rank=3/3 = 1.0
        assertThat(ranked.getRow(5).get("dept")).isEqualTo("IT");
        assertThat(((Number) ranked.getRow(5).get("salary")).intValue()).isEqualTo(90000);
        assertThat(((Number) ranked.getRow(5).get("rank")).intValue()).isEqualTo(4);
        assertThat(((Number) ranked.getRow(5).get("dense_rank")).intValue()).isEqualTo(3);
        assertThat(((Number) ranked.getRow(5).get("pct_rank")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) ranked.getRow(5).get("row_num")).intValue()).isEqualTo(4);

        // In HR partition: salaries are 50k, 60k
        // row 1: HR 50000 -> rank=1, row_num=1
        assertThat(ranked.getRow(1).get("dept")).isEqualTo("HR");
        assertThat(((Number) ranked.getRow(1).get("rank")).intValue()).isEqualTo(1);
        assertThat(((Number) ranked.getRow(1).get("row_num")).intValue()).isEqualTo(1);

        // row 4: HR 60000 -> rank=2, row_num=2
        assertThat(ranked.getRow(4).get("dept")).isEqualTo("HR");
        assertThat(((Number) ranked.getRow(4).get("rank")).intValue()).isEqualTo(2);
        assertThat(((Number) ranked.getRow(4).get("row_num")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("Window partition with descending order")
    void testWindowDescendingOrder() {
        CupOfTea df = new CupOfTea(Schema.of("dept", STRING, "score", INT));
        for (int s : new int[]{10, 30, 20}) {
            Map<String, Object> row = new HashMap<>();
            row.put("dept", "A");
            row.put("score", s);
            df.addRow(row);
        }

        CupOfTea ranked = df.window()
                .partitionBy("dept")
                .orderBy(new Orderer("score", Order.DESCENDING))
                .rank("rank");

        // Scores: 10, 30, 20. In DESC order: 30 (rank 1), 20 (rank 2), 10 (rank 3)
        // row 0: score 10 -> rank 3
        assertThat(((Number) ranked.getRow(0).get("rank")).intValue()).isEqualTo(3);
        // row 1: score 30 -> rank 1
        assertThat(((Number) ranked.getRow(1).get("rank")).intValue()).isEqualTo(1);
        // row 2: score 20 -> rank 2
        assertThat(((Number) ranked.getRow(2).get("rank")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("Direct rank and rowNumber on DataFrame without partition")
    void testDirectRankOnDataFrame() {
        CupOfTea df = new CupOfTea(Schema.of("score", INT));
        for (int s : new int[]{100, 200, 150}) {
            Map<String, Object> row = new HashMap<>();
            row.put("score", s);
            df.addRow(row);
        }

        CupOfTea res = df.rank("score", "score_rank").rowNumber("seq");

        // 100 -> rank 1, 150 -> rank 2, 200 -> rank 3
        assertThat(((Number) res.getRow(0).get("score_rank")).intValue()).isEqualTo(1);
        assertThat(((Number) res.getRow(1).get("score_rank")).intValue()).isEqualTo(3);
        assertThat(((Number) res.getRow(2).get("score_rank")).intValue()).isEqualTo(2);

        // rowNumber in original sequence
        assertThat(((Number) res.getRow(0).get("seq")).intValue()).isEqualTo(1);
        assertThat(((Number) res.getRow(1).get("seq")).intValue()).isEqualTo(2);
        assertThat(((Number) res.getRow(2).get("seq")).intValue()).isEqualTo(3);
    }
}
