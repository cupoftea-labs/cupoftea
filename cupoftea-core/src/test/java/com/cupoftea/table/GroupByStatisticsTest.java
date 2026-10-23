package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;

public class GroupByStatisticsTest {

    private CupOfTea createDepartmentFrame() {
        CupOfTea df = new CupOfTea();
        df.addColumn("department", String.class)
          .addColumn("employee", String.class)
          .addColumn("salary", Double.class);

        // IT: [60000, 80000, 100000] -> median=80000, mean=80000, var=400000000, std=20000
        df.addRow(Map.of("department", "IT", "employee", "Alice", "salary", 60000.0));
        df.addRow(Map.of("department", "IT", "employee", "Bob", "salary", 80000.0));
        df.addRow(Map.of("department", "IT", "employee", "Charlie", "salary", 100000.0));

        // Sales: [40000, 60000] -> median=50000, mean=50000, var=200000000, std=sqrt(200000000)
        df.addRow(Map.of("department", "Sales", "employee", "Diana", "salary", 40000.0));
        df.addRow(Map.of("department", "Sales", "employee", "Evan", "salary", 60000.0));

        // HR: [70000] -> single element (variance/std should be null)
        df.addRow(Map.of("department", "HR", "employee", "Fiona", "salary", 70000.0));

        return df;
    }

    @Test
    @DisplayName("GroupBy aggregations with median, variance, std, and quantile")
    void test_groupBy_statisticalAggs() {
        CupOfTea df = createDepartmentFrame();

        CupOfTea result = df.groupBy("department").agg(
            Aggregations.median("salary"),
            Aggregations.var("salary"),
            Aggregations.std("salary"),
            Aggregations.quantile("salary", 0.75)
        );

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.getColumnHeader("salaryMedian")).isNotNull();
        assertThat(result.getColumnHeader("salaryVar")).isNotNull();
        assertThat(result.getColumnHeader("salaryStd")).isNotNull();

        // Check IT department
        CupOfTea it = result.filter(r -> "IT".equals(r.get("department")));
        assertThat(it.size()).isEqualTo(1);
        Row itRow = it.getRow(0);
        assertThat(itRow.getDouble("salaryMedian")).isEqualTo(80000.0);
        assertThat(itRow.getDouble("salaryVar")).isEqualTo(400000000.0);
        assertThat(itRow.getDouble("salaryStd")).isEqualTo(20000.0);
        assertThat(itRow.getDouble("salaryQuantile_0_75")).isEqualTo(90000.0);

        // Check HR department (single element: sample variance/std must be null)
        CupOfTea hr = result.filter(r -> "HR".equals(r.get("department")));
        assertThat(hr.size()).isEqualTo(1);
        Row hrRow = hr.getRow(0);
        assertThat(hrRow.getDouble("salaryMedian")).isEqualTo(70000.0);
        assertThat(hrRow.get("salaryVar")).isNull();
        assertThat(hrRow.get("salaryStd")).isNull();
    }

    @Test
    @DisplayName("GroupBy shortcut methods: median, std, var, quantile")
    void test_groupBy_shortcuts() {
        CupOfTea df = createDepartmentFrame();

        CupOfTea med = df.groupBy("department").median("salary");
        assertThat(med.size()).isEqualTo(3);
        assertThat(med.getColumnHeader("salaryMedian")).isNotNull();

        CupOfTea std = df.groupBy("department").std("salary");
        assertThat(std.size()).isEqualTo(3);
        assertThat(std.getColumnHeader("salaryStd")).isNotNull();

        CupOfTea var = df.groupBy("department").var("salary");
        assertThat(var.size()).isEqualTo(3);
        assertThat(var.getColumnHeader("salaryVar")).isNotNull();

        CupOfTea q = df.groupBy("department").quantile("salary", 0.5);
        assertThat(q.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("GroupBy with Aggregation enum integration")
    void test_groupBy_aggregationEnum() {
        CupOfTea df = createDepartmentFrame();

        CupOfTea result = df.groupBy("department").agg(
            Aggregation.MEDIAN.of("salary", "med_sal"),
            Aggregation.STD.of("salary", "std_sal"),
            Aggregation.VAR.of("salary", "var_sal"),
            Aggregation.QUANTILE.of("salary", 0.25, "q25_sal")
        );

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.getColumnHeader("med_sal")).isNotNull();
        assertThat(result.getColumnHeader("std_sal")).isNotNull();
        assertThat(result.getColumnHeader("var_sal")).isNotNull();
        assertThat(result.getColumnHeader("q25_sal")).isNotNull();

        CupOfTea it = result.filter(r -> "IT".equals(r.get("department")));
        Row itRow = it.getRow(0);
        assertThat(itRow.getDouble("med_sal")).isEqualTo(80000.0);
        assertThat(itRow.getDouble("std_sal")).isEqualTo(20000.0);
        assertThat(itRow.getDouble("q25_sal")).isEqualTo(70000.0);
    }

    @Test
    @DisplayName("GroupBy agg overloaded with Aggregation enum directly")
    void test_groupBy_aggEnumDirectly() {
        CupOfTea df = createDepartmentFrame();

        CupOfTea res1 = df.groupBy("department").agg(Aggregation.MEDIAN, "salary");
        assertThat(res1.getColumnHeader("salaryMedian")).isNotNull();

        CupOfTea res2 = df.groupBy("department").agg(Aggregation.STD, "salary", "custom_std");
        assertThat(res2.getColumnHeader("custom_std")).isNotNull();

        CupOfTea res3 = df.groupBy("department").agg(Aggregation.QUANTILE, "salary", 0.75, "q75");
        assertThat(res3.getColumnHeader("q75")).isNotNull();
    }
}
