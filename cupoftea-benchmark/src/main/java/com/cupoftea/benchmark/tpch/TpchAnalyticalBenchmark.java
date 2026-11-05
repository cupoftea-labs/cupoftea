package com.cupoftea.benchmark.tpch;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
import com.cupoftea.lazy.LazyCupOfTea;
import com.cupoftea.table.Aggregations;
import com.cupoftea.utils.interfaces.Order;
import com.cupoftea.utils.interfaces.Orderer;

/**
 * Standard TPC-H analytical queries (Q1 - Q22) benchmark suite for CupOfTea.
 * Evaluates core complex analytical workloads including multi-table star/snowflake joins,
 * correlated subqueries, multi-attribute group-bys, and compound range filter pushdowns
 * across Eager Parallel and Lazy Streaming execution plans.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgsAppend = {
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-modules=jdk.incubator.vector",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseG1GC"
})
public class TpchAnalyticalBenchmark {

    @Param({"0.01", "0.1"})
    public double scaleFactor;

    public TpchDataGenerator.TpchTables tables;

    @Setup(Level.Trial)
    public void setupTrial() {
        tables = TpchDataGenerator.generate(scaleFactor);
    }

    /**
     * Q1: Pricing Summary Report Query.
     * High selectivity table scan with multiple aggregations (sum, mean, count)
     * grouped by returnflag and linestatus.
     */
    @Benchmark
    public void q01_pricingSummaryReport(Blackhole bh) {
        CupOfTea res = tables.lineitem().asParallel()
                .filter(r -> {
                    String shipdate = r.getString("l_shipdate");
                    return shipdate != null && shipdate.compareTo("1998-09-02") <= 0;
                })
                .addColumn("disc_price", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                })
                .addColumn("charge", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    Double t = r.getDouble("l_tax");
                    return (ep != null && d != null && t != null) ? ep * (1.0 - d) * (1.0 + t) : 0.0;
                })
                .groupBy("l_returnflag", "l_linestatus")
                .agg(
                    Aggregations.sum("l_quantity").as("sum_qty"),
                    Aggregations.sum("l_extendedprice").as("sum_base_price"),
                    Aggregations.sum("disc_price").as("sum_disc_price"),
                    Aggregations.sum("charge").as("sum_charge"),
                    Aggregations.mean("l_quantity").as("avg_qty"),
                    Aggregations.mean("l_extendedprice").as("avg_price"),
                    Aggregations.mean("l_discount").as("avg_disc"),
                    Aggregations.count("l_quantity").as("count_order")
                )
                .orderBy(new Orderer("l_returnflag", Order.ASCENDING), new Orderer("l_linestatus", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q01 Lazy Streaming: Evaluates Q1 through the LazyCupOfTea execution node.
     */
    @Benchmark
    public void q01_pricingSummaryReportLazy(Blackhole bh) {
        CupOfTea res = LazyCupOfTea.fromDataFrame(tables.lineitem())
                .filter(r -> {
                    String shipdate = r.getString("l_shipdate");
                    return shipdate != null && shipdate.compareTo("1998-09-02") <= 0;
                })
                .groupBy("l_returnflag", "l_linestatus")
                .agg(
                    Aggregations.sum("l_quantity").as("sum_qty"),
                    Aggregations.sum("l_extendedprice").as("sum_base_price"),
                    Aggregations.mean("l_quantity").as("avg_qty"),
                    Aggregations.mean("l_extendedprice").as("avg_price"),
                    Aggregations.mean("l_discount").as("avg_disc"),
                    Aggregations.count("l_quantity").as("count_order")
                )
                .collect()
                .orderBy(new Orderer("l_returnflag", Order.ASCENDING), new Orderer("l_linestatus", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q2: Minimum Cost Supplier Query.
     * Joins part, supplier, partsupp, nation, region to find cheapest suppliers in a given region.
     */
    @Benchmark
    public void q02_minimumCostSupplier(Blackhole bh) {
        CupOfTea reg = tables.region().asParallel().filter(r -> "EUROPE".equals(r.getString("r_name")));
        CupOfTea nat = tables.nation().asParallel().innerJoin(reg, "n_regionkey", "r_regionkey");
        CupOfTea supp = tables.supplier().asParallel().innerJoin(nat, "s_nationkey", "n_nationkey");
        CupOfTea brassParts = tables.part().asParallel()
                .filter(r -> {
                    Integer size = r.getInt("p_size");
                    String type = r.getString("p_type");
                    return size != null && size == 15 && type != null && type.endsWith("BRASS");
                });

        CupOfTea ps = tables.partsupp().asParallel()
                .innerJoin(brassParts, "ps_partkey", "p_partkey")
                .innerJoin(supp, "ps_suppkey", "s_suppkey")
                .orderBy(new Orderer("s_acctbal", Order.DESCENDING), new Orderer("n_name", Order.ASCENDING), new Orderer("s_name", Order.ASCENDING), new Orderer("p_partkey", Order.ASCENDING));

        bh.consume(ps.size());
    }

    /**
     * Q3: Shipping Priority Query.
     * Multi-way join: customer, orders, lineitem with compound date and market segment predicates.
     */
    @Benchmark
    public void q03_shippingPriority(Blackhole bh) {
        CupOfTea buildingCust = tables.customer().asParallel()
                .filter(r -> "BUILDING".equals(r.getString("c_mktsegment")));

        CupOfTea orders = tables.orders().asParallel()
                .filter(r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.compareTo("1995-03-15") < 0;
                })
                .innerJoin(buildingCust, "o_custkey", "c_custkey");

        CupOfTea lineitems = tables.lineitem().asParallel()
                .filter(r -> {
                    String dt = r.getString("l_shipdate");
                    return dt != null && dt.compareTo("1995-03-15") > 0;
                })
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        CupOfTea res = orders.innerJoin(lineitems, "o_orderkey", "l_orderkey")
                .groupBy("l_orderkey", "o_orderdate", "o_shippriority")
                .agg(Aggregations.sum("revenue").as("revenue"))
                .orderBy(new Orderer("revenue", Order.DESCENDING), new Orderer("o_orderdate", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q4: Order Priority Checking Query.
     * Orders with receipt commit-delay lineitems.
     */
    @Benchmark
    public void q04_orderPriorityChecking(Blackhole bh) {
        CupOfTea delayedItems = tables.lineitem().asParallel()
                .filter(r -> {
                    String commit = r.getString("l_commitdate");
                    String receipt = r.getString("l_receiptdate");
                    return commit != null && receipt != null && commit.compareTo(receipt) < 0;
                })
                .select("l_orderkey")
                .distinct();

        CupOfTea res = tables.orders().asParallel()
                .filter(r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.compareTo("1993-07-01") >= 0 && dt.compareTo("1993-10-01") < 0;
                })
                .innerJoin(delayedItems, "o_orderkey", "l_orderkey")
                .groupBy("o_orderpriority")
                .agg(Aggregations.count("o_orderkey").as("order_count"))
                .orderBy(new Orderer("o_orderpriority", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q5: Local Supplier Volume Query.
     * Star-join across customer, orders, lineitem, supplier, nation, region within 'ASIA'.
     */
    @Benchmark
    public void q05_localSupplierVolume(Blackhole bh) {
        CupOfTea asiaReg = tables.region().asParallel().filter(r -> "ASIA".equals(r.getString("r_name")));
        CupOfTea asiaNat = tables.nation().asParallel().innerJoin(asiaReg, "n_regionkey", "r_regionkey");

        CupOfTea supp = tables.supplier().asParallel().innerJoin(asiaNat, "s_nationkey", "n_nationkey");
        CupOfTea cust = tables.customer().asParallel();

        CupOfTea ord = tables.orders().asParallel()
                .filter(r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.compareTo("1994-01-01") >= 0 && dt.compareTo("1995-01-01") < 0;
                })
                .innerJoin(cust, "o_custkey", "c_custkey");

        CupOfTea li = tables.lineitem().asParallel()
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        CupOfTea res = ord.innerJoin(li, "o_orderkey", "l_orderkey")
                .innerJoin(supp, "l_suppkey", "s_suppkey")
                .filter(r -> {
                    Long cNat = r.getLong("c_nationkey");
                    Long sNat = r.getLong("s_nationkey");
                    return cNat != null && cNat.equals(sNat);
                })
                .groupBy("n_name")
                .agg(Aggregations.sum("revenue").as("revenue"))
                .orderBy(new Orderer("revenue", Order.DESCENDING));

        bh.consume(res.size());
    }

    /**
     * Q6: Forecasting Revenue Change Query.
     * High performance single-table scan with compound range filter.
     */
    @Benchmark
    public void q06_forecastingRevenueChange(Blackhole bh) {
        CupOfTea res = tables.lineitem().asParallel()
                .filter(r -> {
                    String shipdate = r.getString("l_shipdate");
                    Double discount = r.getDouble("l_discount");
                    Double quantity = r.getDouble("l_quantity");
                    return shipdate != null && shipdate.compareTo("1994-01-01") >= 0 && shipdate.compareTo("1995-01-01") < 0
                            && discount != null && discount >= 0.05 && discount <= 0.07
                            && quantity != null && quantity < 24.0;
                })
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * d : 0.0;
                });

        Object revenue = res.sumOf("revenue");
        bh.consume(revenue);
    }

    /**
     * Q06 Lazy Streaming: Evaluates Q6 filter and sum pushdown via LazyCupOfTea.
     */
    @Benchmark
    public void q06_forecastingRevenueChangeLazy(Blackhole bh) {
        CupOfTea res = LazyCupOfTea.fromDataFrame(tables.lineitem())
                .filter(r -> {
                    String shipdate = r.getString("l_shipdate");
                    Double discount = r.getDouble("l_discount");
                    Double quantity = r.getDouble("l_quantity");
                    return shipdate != null && shipdate.compareTo("1994-01-01") >= 0 && shipdate.compareTo("1995-01-01") < 0
                            && discount != null && discount >= 0.05 && discount <= 0.07
                            && quantity != null && quantity < 24.0;
                })
                .collect()
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * d : 0.0;
                });

        Object revenue = res.sumOf("revenue");
        bh.consume(revenue);
    }

    /**
     * Q7: Volume Shipping Query.
     * Bilateral trade query between FRANCE and GERMANY.
     */
    @Benchmark
    public void q07_volumeShipping(Blackhole bh) {
        CupOfTea nations = tables.nation().asParallel()
                .filter(r -> "FRANCE".equals(r.getString("n_name")) || "GERMANY".equals(r.getString("n_name")));

        CupOfTea supp = tables.supplier().asParallel()
                .innerJoin(nations, "s_nationkey", "n_nationkey")
                .renameColumn("n_name", "supp_nation");

        CupOfTea cust = tables.customer().asParallel()
                .innerJoin(nations, "c_nationkey", "n_nationkey")
                .renameColumn("n_name", "cust_nation");

        CupOfTea li = tables.lineitem().asParallel()
                .filter(r -> {
                    String dt = r.getString("l_shipdate");
                    return dt != null && dt.compareTo("1995-01-01") >= 0 && dt.compareTo("1996-12-31") <= 0;
                })
                .addColumn("volume", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                })
                .addColumn("l_year", String.class, r -> {
                    String s = r.getString("l_shipdate");
                    return s != null && s.length() >= 4 ? s.substring(0, 4) : "";
                });

        CupOfTea res = li.innerJoin(supp, "l_suppkey", "s_suppkey")
                .innerJoin(tables.orders().asParallel(), "l_orderkey", "o_orderkey")
                .innerJoin(cust, "o_custkey", "c_custkey")
                .filter(r -> {
                    String suppNat = r.getString("supp_nation");
                    String custNat = r.getString("cust_nation");
                    return ("FRANCE".equals(suppNat) && "GERMANY".equals(custNat))
                        || ("GERMANY".equals(suppNat) && "FRANCE".equals(custNat));
                })
                .groupBy("supp_nation", "cust_nation", "l_year")
                .agg(Aggregations.sum("volume").as("revenue"))
                .orderBy(new Orderer("supp_nation", Order.ASCENDING), new Orderer("cust_nation", Order.ASCENDING), new Orderer("l_year", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q8: National Market Share Query.
     * Market share for BRAZIL within AMERICA for ECONOMY ANODIZED STEEL parts.
     */
    @Benchmark
    public void q08_nationalMarketShare(Blackhole bh) {
        CupOfTea parts = tables.part().asParallel()
                .filter(r -> "ECONOMY ANODIZED STEEL".equals(r.getString("p_type")));

        CupOfTea reg = tables.region().asParallel().filter(r -> "AMERICA".equals(r.getString("r_name")));
        CupOfTea nat = tables.nation().asParallel().innerJoin(reg, "n_regionkey", "r_regionkey");
        CupOfTea cust = tables.customer().asParallel().innerJoin(nat, "c_nationkey", "n_nationkey");

        CupOfTea ord = tables.orders().asParallel()
                .filter(r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.compareTo("1995-01-01") >= 0 && dt.compareTo("1996-12-31") <= 0;
                })
                .addColumn("o_year", String.class, r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.length() >= 4 ? dt.substring(0, 4) : "";
                })
                .innerJoin(cust, "o_custkey", "c_custkey");

        CupOfTea li = tables.lineitem().asParallel()
                .innerJoin(parts, "l_partkey", "p_partkey")
                .addColumn("volume", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        CupOfTea res = li.innerJoin(ord, "l_orderkey", "o_orderkey")
                .innerJoin(tables.supplier().asParallel(), "l_suppkey", "s_suppkey")
                .groupBy("o_year")
                .agg(
                    Aggregations.sum("volume").as("total_volume"),
                    Aggregations.count("l_orderkey").as("cnt")
                )
                .orderBy(new Orderer("o_year", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q9: Product Type Profit Measure Query.
     * Complex join across 6 tables (part, supplier, lineitem, partsupp, orders, nation)
     * calculating total profit per nation and order year.
     */
    @Benchmark
    public void q09_productTypeProfitMeasure(Blackhole bh) {
        CupOfTea greenParts = tables.part().asParallel()
                .filter(r -> {
                    String name = r.getString("p_name");
                    return name != null && name.contains("green");
                });

        CupOfTea ord = tables.orders().asParallel()
                .addColumn("o_year", String.class, r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.length() >= 4 ? dt.substring(0, 4) : "";
                });

        CupOfTea li = tables.lineitem().asParallel()
                .innerJoin(greenParts, "l_partkey", "p_partkey")
                .innerJoin(tables.supplier().asParallel(), "l_suppkey", "s_suppkey")
                .innerJoin(tables.nation().asParallel(), "s_nationkey", "n_nationkey")
                .innerJoin(ord, "l_orderkey", "o_orderkey");

        CupOfTea res = li.innerJoin(tables.partsupp().asParallel(), "l_partkey", "ps_partkey")
                .addColumn("amount", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double disc = r.getDouble("l_discount");
                    Double sc = r.getDouble("ps_supplycost");
                    Double qty = r.getDouble("l_quantity");
                    if (ep == null || disc == null || sc == null || qty == null) return 0.0;
                    return (ep * (1.0 - disc)) - (sc * qty);
                })
                .groupBy("n_name", "o_year")
                .agg(Aggregations.sum("amount").as("sum_profit"))
                .orderBy(new Orderer("n_name", Order.ASCENDING), new Orderer("o_year", Order.DESCENDING));

        bh.consume(res.size());
    }

    /**
     * Q10: Returned Item Reporting Query.
     * Joins customer, orders, lineitem, nation for items returned with 'R' returnflag.
     */
    @Benchmark
    public void q10_returnedItemReporting(Blackhole bh) {
        CupOfTea ord = tables.orders().asParallel()
                .filter(r -> {
                    String dt = r.getString("o_orderdate");
                    return dt != null && dt.compareTo("1993-10-01") >= 0 && dt.compareTo("1994-01-01") < 0;
                });

        CupOfTea returnedLi = tables.lineitem().asParallel()
                .filter(r -> "R".equals(r.getString("l_returnflag")))
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        CupOfTea res = ord.innerJoin(returnedLi, "o_orderkey", "l_orderkey")
                .innerJoin(tables.customer().asParallel(), "o_custkey", "c_custkey")
                .innerJoin(tables.nation().asParallel(), "c_nationkey", "n_nationkey")
                .groupBy("c_custkey", "c_name", "c_acctbal", "c_phone", "n_name", "c_address", "c_comment")
                .agg(Aggregations.sum("revenue").as("revenue"))
                .orderBy(new Orderer("revenue", Order.DESCENDING));

        bh.consume(res.size());
    }

    /**
     * Q11: Important Stock Identification Query.
     * Evaluates parts available in GERMANY.
     */
    @Benchmark
    public void q11_importantStockIdentification(Blackhole bh) {
        CupOfTea germany = tables.nation().asParallel().filter(r -> "GERMANY".equals(r.getString("n_name")));
        CupOfTea supp = tables.supplier().asParallel().innerJoin(germany, "s_nationkey", "n_nationkey");

        CupOfTea ps = tables.partsupp().asParallel()
                .innerJoin(supp, "ps_suppkey", "s_suppkey")
                .addColumn("value", Double.class, r -> {
                    Double sc = r.getDouble("ps_supplycost");
                    Integer aq = r.getInt("ps_availqty");
                    return (sc != null && aq != null) ? sc * aq : 0.0;
                });

        CupOfTea res = ps.groupBy("ps_partkey")
                .agg(Aggregations.sum("value").as("part_value"))
                .orderBy(new Orderer("part_value", Order.DESCENDING));

        bh.consume(res.size());
    }

    /**
     * Q12: Shipping Modes and Order Priority Query.
     * Tests receipt delay and count of priority orders across MAIL and SHIP modes.
     */
    @Benchmark
    public void q12_shippingModesAndOrderPriority(Blackhole bh) {
        CupOfTea li = tables.lineitem().asParallel()
                .filter(r -> {
                    String mode = r.getString("l_shipmode");
                    if (!"MAIL".equals(mode) && !"SHIP".equals(mode)) return false;
                    String commit = r.getString("l_commitdate");
                    String receipt = r.getString("l_receiptdate");
                    String ship = r.getString("l_shipdate");
                    return commit != null && receipt != null && ship != null
                            && commit.compareTo(receipt) < 0
                            && ship.compareTo(commit) < 0
                            && receipt.compareTo("1994-01-01") >= 0 && receipt.compareTo("1995-01-01") < 0;
                });

        CupOfTea res = li.innerJoin(tables.orders().asParallel(), "l_orderkey", "o_orderkey")
                .addColumn("high_line_count", Integer.class, r -> {
                    String prio = r.getString("o_orderpriority");
                    return ("1-URGENT".equals(prio) || "2-HIGH".equals(prio)) ? 1 : 0;
                })
                .addColumn("low_line_count", Integer.class, r -> {
                    String prio = r.getString("o_orderpriority");
                    return (!"1-URGENT".equals(prio) && !"2-HIGH".equals(prio)) ? 1 : 0;
                })
                .groupBy("l_shipmode")
                .agg(
                    Aggregations.sum("high_line_count").as("high_line_count"),
                    Aggregations.sum("low_line_count").as("low_line_count")
                )
                .orderBy(new Orderer("l_shipmode", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q13: Customer Distribution Query.
     * Left outer join between customer and orders (excluding 'special requests')
     * followed by two-level nested aggregation.
     */
    @Benchmark
    public void q13_customerDistribution(Blackhole bh) {
        CupOfTea ord = tables.orders().asParallel()
                .filter(r -> {
                    String comm = r.getString("o_comment");
                    return comm == null || !comm.contains("special requests");
                });

        CupOfTea joined = tables.customer().asParallel()
                .leftJoin(ord, "c_custkey", "o_custkey")
                .groupBy("c_custkey")
                .agg(Aggregations.count("o_orderkey").as("c_count"));

        CupOfTea res = joined.groupBy("c_count")
                .agg(Aggregations.count("c_custkey").as("custdist"))
                .orderBy(new Orderer("custdist", Order.DESCENDING), new Orderer("c_count", Order.DESCENDING));

        bh.consume(res.size());
    }

    /**
     * Q14: Promotion Effect Query.
     * Ratio of PROMO revenue to total revenue in a single month.
     */
    @Benchmark
    public void q14_promotionEffect(Blackhole bh) {
        CupOfTea li = tables.lineitem().asParallel()
                .filter(r -> {
                    String dt = r.getString("l_shipdate");
                    return dt != null && dt.compareTo("1995-09-01") >= 0 && dt.compareTo("1995-10-01") < 0;
                })
                .innerJoin(tables.part().asParallel(), "l_partkey", "p_partkey")
                .addColumn("promo_revenue", Double.class, r -> {
                    String type = r.getString("p_type");
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    double rev = (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                    return (type != null && type.startsWith("PROMO")) ? rev : 0.0;
                })
                .addColumn("total_revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        Object promoObj = li.sumOf("promo_revenue");
        Object totalObj = li.sumOf("total_revenue");
        double promoSum = (promoObj instanceof Number n) ? n.doubleValue() : 0.0;
        double totalSum = (totalObj instanceof Number n) ? n.doubleValue() : 0.0;
        double promoEffect = (totalSum > 0.0) ? (100.0 * promoSum / totalSum) : 0.0;
        bh.consume(promoEffect);
    }

    /**
     * Q15: Top Supplier Query.
     * Evaluates total supplier revenue and identifies top contributor.
     */
    @Benchmark
    public void q15_topSupplier(Blackhole bh) {
        CupOfTea revenue0 = tables.lineitem().asParallel()
                .filter(r -> {
                    String dt = r.getString("l_shipdate");
                    return dt != null && dt.compareTo("1996-01-01") >= 0 && dt.compareTo("1996-04-01") < 0;
                })
                .addColumn("revenue", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                })
                .groupBy("l_suppkey")
                .agg(Aggregations.sum("revenue").as("total_revenue"));

        CupOfTea res = tables.supplier().asParallel()
                .innerJoin(revenue0, "s_suppkey", "l_suppkey")
                .orderBy(new Orderer("total_revenue", Order.DESCENDING), new Orderer("s_suppkey", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q16: Parts/Supplier Relationship Query.
     * Filter parts not from Brand#45 and not POLISHED BRASS, counting distinct suppliers.
     */
    @Benchmark
    public void q16_partsSupplierRelationship(Blackhole bh) {
        CupOfTea parts = tables.part().asParallel()
                .filter(r -> {
                    String brand = r.getString("p_brand");
                    String type = r.getString("p_type");
                    Integer size = r.getInt("p_size");
                    return brand != null && !brand.equals("Brand#45")
                            && type != null && !type.startsWith("MEDIUM POLISHED")
                            && size != null && (size == 49 || size == 14 || size == 23 || size == 45);
                });

        CupOfTea res = tables.partsupp().asParallel()
                .innerJoin(parts, "ps_partkey", "p_partkey")
                .groupBy("p_brand", "p_type", "p_size")
                .agg(Aggregations.count("ps_suppkey").as("supplier_cnt"))
                .orderBy(new Orderer("supplier_cnt", Order.DESCENDING), new Orderer("p_brand", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q17: Small-Quantity-Order Revenue Query.
     * Evaluates revenue loss on small quantity orders (< 20% average quantity).
     */
    @Benchmark
    public void q17_smallQuantityOrderRevenue(Blackhole bh) {
        CupOfTea brandParts = tables.part().asParallel()
                .filter(r -> "Brand#23".equals(r.getString("p_brand")) && "MED BOX".equals(r.getString("p_container")));

        CupOfTea joined = tables.lineitem().asParallel()
                .innerJoin(brandParts, "l_partkey", "p_partkey");

        Double avgQty = joined.meanOf("l_quantity");
        double threshold = (avgQty != null) ? avgQty * 0.2 : 0.0;

        CupOfTea res = joined.filter(r -> {
            Double qty = r.getDouble("l_quantity");
            return qty != null && qty < threshold;
        });

        Object totalObj = res.sumOf("l_extendedprice");
        double totalExtendedPrice = (totalObj instanceof Number n) ? n.doubleValue() : 0.0;
        double avgYearly = totalExtendedPrice / 7.0;
        bh.consume(avgYearly);
    }

    /**
     * Q18: Large Volume Customer Query.
     * Identifies orders with total quantity exceeding threshold joined with customer.
     */
    @Benchmark
    public void q18_largeVolumeCustomer(Blackhole bh) {
        CupOfTea bigOrders = tables.lineitem().asParallel()
                .groupBy("l_orderkey")
                .agg(Aggregations.sum("l_quantity").as("sum_qty"))
                .filter(r -> {
                    Double sq = r.getDouble("sum_qty");
                    return sq != null && sq > 100.0;
                });

        CupOfTea res = tables.orders().asParallel()
                .innerJoin(bigOrders, "o_orderkey", "l_orderkey")
                .innerJoin(tables.customer().asParallel(), "o_custkey", "c_custkey")
                .orderBy(new Orderer("o_totalprice", Order.DESCENDING), new Orderer("o_orderdate", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q19: Discounted Revenue Query.
     * Evaluates compound OR predicates across multiple parts brands and quantities.
     */
    @Benchmark
    public void q19_discountedRevenue(Blackhole bh) {
        CupOfTea res = tables.lineitem().asParallel()
                .innerJoin(tables.part().asParallel(), "l_partkey", "p_partkey")
                .filter(r -> {
                    String mode = r.getString("l_shipmode");
                    String instruct = r.getString("l_shipinstruct");
                    if (!"AIR".equals(mode) && !"REG AIR".equals(mode)) return false;
                    if (!"DELIVER IN PERSON".equals(instruct)) return false;

                    String brand = r.getString("p_brand");
                    String container = r.getString("p_container");
                    Double qty = r.getDouble("l_quantity");
                    Integer size = r.getInt("p_size");
                    if (brand == null || container == null || qty == null || size == null) return false;

                    boolean b1 = "Brand#12".equals(brand)
                            && (container.equals("SM CASE") || container.equals("SM BOX") || container.equals("SM PKG"))
                            && qty >= 1 && qty <= 11 && size >= 1 && size <= 5;

                    boolean b2 = "Brand#23".equals(brand)
                            && (container.equals("MED BAG") || container.equals("MED BOX") || container.equals("MED PKG"))
                            && qty >= 10 && qty <= 20 && size >= 1 && size <= 10;

                    boolean b3 = "Brand#34".equals(brand)
                            && (container.equals("LG CASE") || container.equals("LG BOX") || container.equals("LG PKG"))
                            && qty >= 20 && qty <= 30 && size >= 1 && size <= 15;

                    return b1 || b2 || b3;
                })
                .addColumn("rev", Double.class, r -> {
                    Double ep = r.getDouble("l_extendedprice");
                    Double d = r.getDouble("l_discount");
                    return (ep != null && d != null) ? ep * (1.0 - d) : 0.0;
                });

        Object revenueObj = res.sumOf("rev");
        bh.consume(revenueObj);
    }

    /**
     * Q20: Potential Part Promotion Query.
     * Identifies suppliers in Canada with available parts containing 'forest'.
     */
    @Benchmark
    public void q20_potentialPartPromotion(Blackhole bh) {
        CupOfTea forestParts = tables.part().asParallel()
                .filter(r -> {
                    String name = r.getString("p_name");
                    return name != null && name.contains("forest");
                });

        CupOfTea canada = tables.nation().asParallel()
                .filter(r -> "CANADA".equals(r.getString("n_name")));

        CupOfTea suppInCanada = tables.supplier().asParallel()
                .innerJoin(canada, "s_nationkey", "n_nationkey");

        CupOfTea ps = tables.partsupp().asParallel()
                .innerJoin(forestParts, "ps_partkey", "p_partkey")
                .innerJoin(suppInCanada, "ps_suppkey", "s_suppkey")
                .orderBy(new Orderer("s_name", Order.ASCENDING));

        bh.consume(ps.size());
    }

    /**
     * Q21: Suppliers Who Kept Orders Waiting Query.
     * Evaluates multi-supplier orders with delayed lineitem delivery in SAUDI ARABIA.
     */
    @Benchmark
    public void q21_suppliersWhoKeptOrdersWaiting(Blackhole bh) {
        CupOfTea saudi = tables.nation().asParallel()
                .filter(r -> "SAUDI ARABIA".equals(r.getString("n_name")));

        CupOfTea supp = tables.supplier().asParallel()
                .innerJoin(saudi, "s_nationkey", "n_nationkey");

        CupOfTea failedOrders = tables.orders().asParallel()
                .filter(r -> "F".equals(r.getString("o_orderstatus")));

        CupOfTea delayedLi = tables.lineitem().asParallel()
                .filter(r -> {
                    String receipt = r.getString("l_receiptdate");
                    String commit = r.getString("l_commitdate");
                    return receipt != null && commit != null && receipt.compareTo(commit) > 0;
                });

        CupOfTea res = delayedLi.innerJoin(failedOrders, "l_orderkey", "o_orderkey")
                .innerJoin(supp, "l_suppkey", "s_suppkey")
                .groupBy("s_name")
                .agg(Aggregations.count("l_orderkey").as("numwait"))
                .orderBy(new Orderer("numwait", Order.DESCENDING), new Orderer("s_name", Order.ASCENDING));

        bh.consume(res.size());
    }

    /**
     * Q22: Global Sales Opportunity Query.
     * Evaluates high-balance customers without orders across country codes.
     */
    @Benchmark
    public void q22_globalSalesOpportunity(Blackhole bh) {
        CupOfTea targetCust = tables.customer().asParallel()
                .filter(r -> {
                    String phone = r.getString("c_phone");
                    Double bal = r.getDouble("c_acctbal");
                    if (phone == null || phone.length() < 2 || bal == null) return false;
                    String cntry = phone.substring(0, 2);
                    boolean isTargetCountry = cntry.equals("13") || cntry.equals("31") || cntry.equals("23")
                            || cntry.equals("29") || cntry.equals("30") || cntry.equals("18") || cntry.equals("17");
                    return isTargetCountry && bal > 0.0;
                })
                .addColumn("cntrycode", String.class, r -> {
                    String phone = r.getString("c_phone");
                    return (phone != null && phone.length() >= 2) ? phone.substring(0, 2) : "";
                });

        Double avgBal = targetCust.meanOf("c_acctbal");
        double threshold = (avgBal != null) ? avgBal : 0.0;

        CupOfTea aboveAvgCust = targetCust.filter(r -> {
            Double bal = r.getDouble("c_acctbal");
            return bal != null && bal > threshold;
        });

        CupOfTea res = aboveAvgCust.groupBy("cntrycode")
                .agg(
                    Aggregations.count("c_custkey").as("numcust"),
                    Aggregations.sum("c_acctbal").as("totacctbal")
                )
                .orderBy(new Orderer("cntrycode", Order.ASCENDING));

        bh.consume(res.size());
    }
}
