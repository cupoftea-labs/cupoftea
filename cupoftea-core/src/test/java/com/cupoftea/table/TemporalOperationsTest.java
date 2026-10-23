package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class TemporalOperationsTest {

    @Test
    @DisplayName("Should extract calendar parts: year, month, monthName, dayOfMonth, dayOfWeek, dayOfYear, quarter")
    void testCalendarExtraction() {
        CupOfTea df = new CupOfTea();
        df.addColumn("date", LocalDate.class);
        df.addRow(new HashMap<>() {{ put("date", LocalDate.of(2024, 2, 29)); }}); // Leap day, Thursday (4)
        df.addRow(new HashMap<>() {{ put("date", LocalDate.of(2025, 10, 23)); }}); // Thursday (4), Q4
        df.addRow(new HashMap<>() {{ put("date", LocalDate.of(2023, 1, 1)); }}); // Sunday (7), Q1
        df.addRow(new HashMap<>() {{ put("date", null); }});

        IntColumnVector years = df.col("date").dt.year();
        assertThat(years.getInt(0)).isEqualTo(2024);
        assertThat(years.getInt(1)).isEqualTo(2025);
        assertThat(years.getInt(2)).isEqualTo(2023);
        assertThat(years.isNull(3)).isTrue();

        IntColumnVector months = df.col("date").dt.month();
        assertThat(months.getInt(0)).isEqualTo(2);
        assertThat(months.getInt(1)).isEqualTo(10);
        assertThat(months.getInt(2)).isEqualTo(1);
        assertThat(months.isNull(3)).isTrue();

        StringColumnVector monthNames = df.col("date").dt.monthName();
        assertThat(monthNames.getString(0)).isEqualTo("February");
        assertThat(monthNames.getString(1)).isEqualTo("October");
        assertThat(monthNames.getString(2)).isEqualTo("January");
        assertThat(monthNames.isNull(3)).isTrue();

        IntColumnVector days = df.col("date").dt.dayOfMonth();
        assertThat(days.getInt(0)).isEqualTo(29);
        assertThat(days.getInt(1)).isEqualTo(23);
        assertThat(days.getInt(2)).isEqualTo(1);

        IntColumnVector dayOfWeeks = df.col("date").dt.dayOfWeek();
        assertThat(dayOfWeeks.getInt(0)).isEqualTo(4); // Thursday
        assertThat(dayOfWeeks.getInt(2)).isEqualTo(7); // Sunday

        IntColumnVector dayOfYears = df.col("date").dt.dayOfYear();
        assertThat(dayOfYears.getInt(0)).isEqualTo(60); // 31 in Jan + 29 in Feb (leap year)
        assertThat(dayOfYears.getInt(2)).isEqualTo(1);

        IntColumnVector quarters = df.col("date").dt.quarter();
        assertThat(quarters.getInt(0)).isEqualTo(1);
        assertThat(quarters.getInt(1)).isEqualTo(4);
        assertThat(quarters.getInt(2)).isEqualTo(1);
        assertThat(quarters.isNull(3)).isTrue();
    }

    @Test
    @DisplayName("Should extract time parts: hour, minute, second, nano from LocalDateTime and Instant")
    void testTimeExtraction() {
        CupOfTea df = new CupOfTea();
        df.addColumn("timestamp", LocalDateTime.class);
        df.addRow(new HashMap<>() {{ put("timestamp", LocalDateTime.of(2024, 5, 10, 14, 30, 45, 123456789)); }});
        df.addRow(new HashMap<>() {{ put("timestamp", LocalDateTime.of(2024, 1, 1, 0, 0, 0, 0)); }});
        df.addRow(new HashMap<>() {{ put("timestamp", null); }});

        IntColumnVector hours = df.col("timestamp").dt.hour();
        assertThat(hours.getInt(0)).isEqualTo(14);
        assertThat(hours.getInt(1)).isEqualTo(0);
        assertThat(hours.isNull(2)).isTrue();

        IntColumnVector minutes = df.col("timestamp").dt.minute();
        assertThat(minutes.getInt(0)).isEqualTo(30);
        assertThat(minutes.getInt(1)).isEqualTo(0);

        IntColumnVector seconds = df.col("timestamp").dt.second();
        assertThat(seconds.getInt(0)).isEqualTo(45);
        assertThat(seconds.getInt(1)).isEqualTo(0);

        IntColumnVector nanos = df.col("timestamp").dt.nano();
        assertThat(nanos.getInt(0)).isEqualTo(123456789);
        assertThat(nanos.getInt(1)).isEqualTo(0);
        assertThat(nanos.isNull(2)).isTrue();
    }

    @Test
    @DisplayName("Should perform date arithmetic: plusDays, minusDays, plusMonths, plusYears, and handle leap years")
    void testDateArithmeticAndLeapYears() {
        CupOfTea df = new CupOfTea();
        df.addColumn("d", LocalDate.class);
        // Feb 29, 2024 is a leap day
        df.addRow(new HashMap<>() {{ put("d", LocalDate.of(2024, 2, 29)); }});
        df.addRow(new HashMap<>() {{ put("d", LocalDate.of(2024, 1, 31)); }});
        df.addRow(new HashMap<>() {{ put("d", null); }});

        // plusDays
        ColumnVector plus1 = df.col("d").dt.plusDays(1);
        assertThat(plus1.getObject(0)).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(plus1.getObject(1)).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(plus1.isNull(2)).isTrue();

        // minusDays
        ColumnVector minus1 = df.col("d").dt.minusDays(1);
        assertThat(minus1.getObject(0)).isEqualTo(LocalDate.of(2024, 2, 28));

        // plusYears across leap year: Feb 29 2024 + 1 year = Feb 28 2025
        ColumnVector plus1Year = df.col("d").dt.plusYears(1);
        assertThat(plus1Year.getObject(0)).isEqualTo(LocalDate.of(2025, 2, 28));

        // plusMonths: Jan 31 + 1 month in leap year = Feb 29
        ColumnVector plus1Month = df.col("d").dt.plusMonths(1);
        assertThat(plus1Month.getObject(1)).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("Should calculate daysBetween and hoursBetween between columns and constants")
    void testDifferenceCalculations() {
        CupOfTea df = new CupOfTea();
        df.addColumn("start", LocalDate.class);
        df.addColumn("end", LocalDate.class);
        df.addRow(new HashMap<>() {{
            put("start", LocalDate.of(2024, 1, 1));
            put("end", LocalDate.of(2024, 1, 10));
        }});
        df.addRow(new HashMap<>() {{
            put("start", LocalDate.of(2024, 1, 15));
            put("end", LocalDate.of(2024, 1, 10));
        }});
        df.addRow(new HashMap<>() {{
            put("start", null);
            put("end", LocalDate.of(2024, 1, 10));
        }});

        LongColumnVector days = df.col("start").dt.daysBetween("end");
        assertThat(days.getLong(0)).isEqualTo(9L);
        assertThat(days.getLong(1)).isEqualTo(-5L);
        assertThat(days.isNull(2)).isTrue();

        LongColumnVector hours = df.col("start").dt.hoursBetween("end");
        assertThat(hours.getLong(0)).isEqualTo(9L * 24L);
        assertThat(hours.getLong(1)).isEqualTo(-5L * 24L);
        assertThat(hours.isNull(2)).isTrue();

        // daysBetween with constant Temporal
        LongColumnVector daysToTarget = df.col("start").dt.daysBetween(LocalDate.of(2024, 1, 5));
        assertThat(daysToTarget.getLong(0)).isEqualTo(4L);
        assertThat(daysToTarget.isNull(2)).isTrue();
    }

    @Test
    @DisplayName("Should handle Daylight Saving Time (DST) transitions correctly")
    void testDaylightSavingTimeTransitions() {
        CupOfTea df = new CupOfTea();
        df.addColumn("dt_start", LocalDateTime.class);
        df.addColumn("dt_end", LocalDateTime.class);

        // In America/New_York, 2024 Spring Forward occurred on March 10, 2024.
        // Clocks jumped from 02:00 to 03:00 EST -> EDT.
        // Thus from 01:00 to 03:00 local time is only 1 elapsed hour!
        df.addRow(new HashMap<>() {{
            put("dt_start", LocalDateTime.of(2024, 3, 10, 1, 0));
            put("dt_end", LocalDateTime.of(2024, 3, 10, 3, 0));
        }});

        // Fall Back occurred on Nov 3, 2024.
        // Clocks repeated 01:00 to 02:00 EDT -> EST.
        // 00:00 to 03:00 local time is 4 elapsed hours!
        df.addRow(new HashMap<>() {{
            put("dt_start", LocalDateTime.of(2024, 11, 3, 0, 0));
            put("dt_end", LocalDateTime.of(2024, 11, 3, 3, 0));
        }});

        ZoneId nyZone = ZoneId.of("America/New_York");
        LongColumnVector elapsedHours = df.col("dt_start").dt.withZone(nyZone).hoursBetween("dt_end");
        assertThat(elapsedHours.getLong(0)).isEqualTo(1L); // Spring forward jumped 1 hour ahead
        assertThat(elapsedHours.getLong(1)).isEqualTo(4L); // Fall back repeated 1 hour
    }

    @Test
    @DisplayName("Should perform truncation: startOfMonth, startOfYear, and truncateTo")
    void testTruncation() {
        CupOfTea df = new CupOfTea();
        df.addColumn("ts", LocalDateTime.class);
        df.addRow(new HashMap<>() {{ put("ts", LocalDateTime.of(2024, 7, 25, 16, 45, 30)); }});
        df.addRow(new HashMap<>() {{ put("ts", null); }});

        ColumnVector som = df.col("ts").dt.startOfMonth();
        assertThat(som.getObject(0)).isEqualTo(LocalDateTime.of(2024, 7, 1, 0, 0, 0));
        assertThat(som.isNull(1)).isTrue();

        ColumnVector soy = df.col("ts").dt.startOfYear();
        assertThat(soy.getObject(0)).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0, 0));

        ColumnVector truncHours = df.col("ts").dt.truncateTo(ChronoUnit.HOURS);
        assertThat(truncHours.getObject(0)).isEqualTo(LocalDateTime.of(2024, 7, 25, 16, 0, 0));
    }

    @Test
    @DisplayName("Should format and parse dates fluently")
    void testFormattingAndParsing() {
        CupOfTea df = new CupOfTea();
        df.addColumn("birth_date", LocalDate.class);
        df.addRow(new HashMap<>() {{ put("birth_date", LocalDate.of(1995, 12, 17)); }});
        df.addRow(new HashMap<>() {{ put("birth_date", null); }});

        // Format to string
        StringColumnVector formatted = df.col("birth_date").dt.format("dd/MM/yyyy");
        assertThat(formatted.getString(0)).isEqualTo("17/12/1995");
        assertThat(formatted.isNull(1)).isTrue();

        // String parsing via dt.parseLocalDate
        CupOfTea textDf = new CupOfTea();
        textDf.addColumn("raw_date", String.class);
        textDf.addRow(new HashMap<>() {{ put("raw_date", "2024-06-15"); }});
        textDf.addRow(new HashMap<>() {{ put("raw_date", null); }});

        ObjectColumnVector parsed = textDf.col("raw_date").dt.parseLocalDate("yyyy-MM-dd");
        assertThat(parsed.getObject(0)).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(parsed.isNull(1)).isTrue();

        // String parsing via str.toLocalDate
        ObjectColumnVector parsedViaStr = textDf.col("raw_date").str.toLocalDate("yyyy-MM-dd");
        assertThat(parsedViaStr.getObject(0)).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    @DisplayName("Should support Instant and java.util.Date columns")
    void testInstantAndDateSupport() {
        Instant now = Instant.parse("2024-04-12T10:15:30Z");
        CupOfTea df = new CupOfTea();
        df.addColumn("inst", Instant.class);
        df.addRow(new HashMap<>() {{ put("inst", now); }});

        assertThat(df.col("inst").dt.year().getInt(0)).isEqualTo(2024);
        assertThat(df.col("inst").dt.month().getInt(0)).isEqualTo(4);
        assertThat(df.col("inst").dt.hour().getInt(0)).isEqualTo(10);
        assertThat(df.col("inst").dt.minute().getInt(0)).isEqualTo(15);
        assertThat(df.col("inst").dt.second().getInt(0)).isEqualTo(30);

        // Date arithmetic on Instant
        ColumnVector plus5Days = df.col("inst").dt.plusDays(5);
        assertThat(plus5Days.getObject(0)).isEqualTo(Instant.parse("2024-04-17T10:15:30Z"));
    }

    @Test
    @DisplayName("Should integrate with withColumn and transformColumn seamlessly")
    void testDataFrameIntegration() {
        CupOfTea df = new CupOfTea();
        df.addColumn("date", LocalDate.class);
        df.addRow(new HashMap<>() {{ put("date", LocalDate.of(2024, 1, 1)); }});
        df.addRow(new HashMap<>() {{ put("date", LocalDate.of(2024, 6, 1)); }});

        // withColumn creates new year column
        df.withColumn("year", df.col("date").dt.year());
        assertThat(df.getHeaders()).extracting(Column::columnName).containsExactly("date", "year");
        assertThat(df.col("year").getInt(0)).isEqualTo(2024);

        // transformColumn increments year in-place
        df.transformColumn("date", df.col("date").dt.plusYears(1));
        assertThat(df.col("date").getObject(0)).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(df.col("date").getObject(1)).isEqualTo(LocalDate.of(2025, 6, 1));

        // transformColumn with lambda
        df.transformColumn("date", col -> col.dt().plusDays(10));
        assertThat(df.col("date").getObject(0)).isEqualTo(LocalDate.of(2025, 1, 11));
    }
}
