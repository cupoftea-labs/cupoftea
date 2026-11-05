package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.SchemaMismatchException;
import com.cupoftea.table.interfaces.IDataFrame;

public class AsOfJoinTest {

    @Test
    @DisplayName("Should perform backward asOfJoin without by column")
    void testBackwardAsOfJoinNoBy() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("time", Long.class)
              .addColumn("bid", Double.class);
        quotes.addRow(Map.of("time", 1000L, "bid", 150.0));
        quotes.addRow(Map.of("time", 1005L, "bid", 150.5));
        quotes.addRow(Map.of("time", 1010L, "bid", 151.0));

        CupOfTea trades = new CupOfTea();
        trades.addColumn("time", Long.class)
              .addColumn("price", Double.class);
        trades.addRow(Map.of("time", 999L, "price", 149.9));   // prior to first quote -> null
        trades.addRow(Map.of("time", 1000L, "price", 150.0));  // exact match -> 150.0
        trades.addRow(Map.of("time", 1002L, "price", 150.2));  // between 1000 and 1005 -> 150.0
        trades.addRow(Map.of("time", 1005L, "price", 150.5));  // exact match -> 150.5
        trades.addRow(Map.of("time", 1008L, "price", 150.8));  // between 1005 and 1010 -> 150.5
        trades.addRow(Map.of("time", 1015L, "price", 151.5));  // after last quote -> 151.0

        CupOfTea joined = trades.asOfJoin(quotes, "time");

        assertThat(joined.size()).isEqualTo(6);
        assertThat(joined.getHeaders().stream().map(Column::columnName).toList())
                .containsExactly("time", "price", "bid");

        assertThat(joined.getRow(0).get("bid")).isNull();
        assertThat(joined.getRow(1).getDouble("bid")).isEqualTo(150.0);
        assertThat(joined.getRow(2).getDouble("bid")).isEqualTo(150.0);
        assertThat(joined.getRow(3).getDouble("bid")).isEqualTo(150.5);
        assertThat(joined.getRow(4).getDouble("bid")).isEqualTo(150.5);
        assertThat(joined.getRow(5).getDouble("bid")).isEqualTo(151.0);
    }

    @Test
    @DisplayName("Should perform forward asOfJoin without by column")
    void testForwardAsOfJoinNoBy() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("time", Long.class)
              .addColumn("bid", Double.class);
        quotes.addRow(Map.of("time", 1000L, "bid", 150.0));
        quotes.addRow(Map.of("time", 1005L, "bid", 150.5));
        quotes.addRow(Map.of("time", 1010L, "bid", 151.0));

        CupOfTea trades = new CupOfTea();
        trades.addColumn("time", Long.class)
              .addColumn("price", Double.class);
        trades.addRow(Map.of("time", 999L, "price", 149.9));   // forwards to 1000 -> 150.0
        trades.addRow(Map.of("time", 1000L, "price", 150.0));  // exact match -> 150.0
        trades.addRow(Map.of("time", 1002L, "price", 150.2));  // forwards to 1005 -> 150.5
        trades.addRow(Map.of("time", 1005L, "price", 150.5));  // exact match -> 150.5
        trades.addRow(Map.of("time", 1008L, "price", 150.8));  // forwards to 1010 -> 151.0
        trades.addRow(Map.of("time", 1015L, "price", 151.5));  // after last quote -> null

        CupOfTea joined = trades.asOfJoin(quotes, "time", AsOfDirection.FORWARD);

        assertThat(joined.size()).isEqualTo(6);
        assertThat(joined.getRow(0).getDouble("bid")).isEqualTo(150.0);
        assertThat(joined.getRow(1).getDouble("bid")).isEqualTo(150.0);
        assertThat(joined.getRow(2).getDouble("bid")).isEqualTo(150.5);
        assertThat(joined.getRow(3).getDouble("bid")).isEqualTo(150.5);
        assertThat(joined.getRow(4).getDouble("bid")).isEqualTo(151.0);
        assertThat(joined.getRow(5).get("bid")).isNull();
    }

    @Test
    @DisplayName("Should perform nearest asOfJoin without by column with tie-breaking")
    void testNearestAsOfJoinNoBy() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("time", Long.class)
              .addColumn("bid", Double.class);
        quotes.addRow(Map.of("time", 10L, "bid", 1.0));
        quotes.addRow(Map.of("time", 20L, "bid", 2.0));

        CupOfTea trades = new CupOfTea();
        trades.addColumn("time", Long.class)
              .addColumn("price", Double.class);
        trades.addRow(Map.of("time", 14L, "price", 1.4)); // closer to 10 -> 1.0
        trades.addRow(Map.of("time", 15L, "price", 1.5)); // equidistant tie -> prefers forward 20 -> 2.0
        trades.addRow(Map.of("time", 16L, "price", 1.6)); // closer to 20 -> 2.0

        CupOfTea joined = trades.asOfJoin(quotes, "time", AsOfDirection.NEAREST);

        assertThat(joined.size()).isEqualTo(3);
        assertThat(joined.getRow(0).getDouble("bid")).isEqualTo(1.0);
        assertThat(joined.getRow(1).getDouble("bid")).isEqualTo(2.0);
        assertThat(joined.getRow(2).getDouble("bid")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should perform backward asOfJoin partitioned by ticker")
    void testBackwardAsOfJoinWithByTicker() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("ticker", String.class)
              .addColumn("time", Long.class)
              .addColumn("bid", Double.class);
        quotes.addRow(Map.of("ticker", "AAPL", "time", 1000L, "bid", 150.0));
        quotes.addRow(Map.of("ticker", "AAPL", "time", 1005L, "bid", 150.5));
        quotes.addRow(Map.of("ticker", "GOOG", "time", 1000L, "bid", 2800.0));
        quotes.addRow(Map.of("ticker", "GOOG", "time", 1004L, "bid", 2801.0));

        CupOfTea trades = new CupOfTea();
        trades.addColumn("ticker", String.class)
              .addColumn("time", Long.class)
              .addColumn("price", Double.class);
        trades.addRow(Map.of("ticker", "AAPL", "time", 1001L, "price", 150.1)); // matches AAPL @ 1000
        trades.addRow(Map.of("ticker", "AAPL", "time", 1006L, "price", 150.6)); // matches AAPL @ 1005
        trades.addRow(Map.of("ticker", "GOOG", "time", 1003L, "price", 2800.5)); // matches GOOG @ 1000
        trades.addRow(Map.of("ticker", "MSFT", "time", 1000L, "price", 300.0)); // unmatched ticker -> null

        CupOfTea joined = trades.asOfJoin(quotes, "time", "ticker", AsOfDirection.BACKWARD);

        assertThat(joined.size()).isEqualTo(4);
        assertThat(joined.getHeaders().stream().map(Column::columnName).toList())
                .containsExactly("ticker", "time", "price", "bid");

        assertThat(joined.getRow(0).getDouble("bid")).isEqualTo(150.0);
        assertThat(joined.getRow(1).getDouble("bid")).isEqualTo(150.5);
        assertThat(joined.getRow(2).getDouble("bid")).isEqualTo(2800.0);
        assertThat(joined.getRow(3).get("bid")).isNull();
    }

    @Test
    @DisplayName("Should handle colliding column names with custom suffixes")
    void testAsOfJoinCollidingColumns() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("time", Long.class)
              .addColumn("val", Double.class)
              .addColumn("bid", Double.class);
        quotes.addRow(Map.of("time", 1000L, "val", 10.0, "bid", 150.0));

        CupOfTea trades = new CupOfTea();
        trades.addColumn("time", Long.class)
              .addColumn("val", Double.class)
              .addColumn("price", Double.class);
        trades.addRow(Map.of("time", 1001L, "val", 20.0, "price", 150.1));

        CupOfTea joined = trades.asOfJoin(quotes, "time", "time", List.of(), List.of(), AsOfDirection.BACKWARD, "_l", "_r");

        assertThat(joined.getHeaders().stream().map(Column::columnName).toList())
                .containsExactly("time", "val_l", "price", "val_r", "bid");
        assertThat(joined.getRow(0).getDouble("val_l")).isEqualTo(20.0);
        assertThat(joined.getRow(0).getDouble("val_r")).isEqualTo(10.0);
        assertThat(joined.getRow(0).getDouble("bid")).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Should work on parallel DataFrames")
    void testParallelAsOfJoin() {
        CupOfTea quotes = new CupOfTea();
        quotes.addColumn("ticker", String.class).addColumn("time", Long.class).addColumn("bid", Double.class);
        for (int i = 0; i < 2000; i++) {
            quotes.addRow(Map.of("ticker", "TICKER_" + (i % 10), "time", (long) i * 10L, "bid", 100.0 + i));
        }
        quotes = quotes.asParallel();

        CupOfTea trades = new CupOfTea();
        trades.addColumn("ticker", String.class).addColumn("time", Long.class).addColumn("price", Double.class);
        for (int i = 0; i < 5000; i++) {
            trades.addRow(Map.of("ticker", "TICKER_" + (i % 10), "time", (long) i * 5L + 2L, "price", 100.0 + i));
        }
        trades = trades.asParallel();

        CupOfTea joined = trades.asOfJoin(quotes, "time", "ticker", AsOfDirection.BACKWARD);
        assertThat(joined.size()).isEqualTo(5000);
    }
    @Test
    @DisplayName("Should throw IllegalArgumentException / SchemaMismatchException on bad arguments")
    void testAsOfJoinValidations() {
        CupOfTea df = new CupOfTea();
        df.addColumn("t", Long.class).addRow(Map.of("t", 10L));

        assertThatThrownBy(() -> df.asOfJoin((CupOfTea) null, "t"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> df.asOfJoin(df, "nonExistent"))
                .isInstanceOf(SchemaMismatchException.class);

        assertThatThrownBy(() -> df.asOfJoin(df, "t", "badBy"))
                .isInstanceOf(SchemaMismatchException.class);
    }

}
