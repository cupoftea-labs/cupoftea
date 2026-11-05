package com.cupoftea.benchmark.tpch;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.ColumnVector;
import com.cupoftea.table.vector.DoubleColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.LongColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

/**
 * Deterministic standard TPC-H synthetic dataset generator for benchmarks.
 * Generates synthetic TPC-H tables (lineitem, orders, customer, part, supplier, partsupp, nation, region)
 * according to TPC-H scale factor definitions (SF 0.01, SF 0.1, SF 1, etc.) or custom row counts.
 * Uses deterministic PRNG sequences rooted at a fixed seed ({@link #DEFAULT_TPCH_SEED})
 * ensuring byte-for-byte reproducibility across JVM and Polars benchmarking suites.
 */
public final class TpchDataGenerator {

    public static final long DEFAULT_TPCH_SEED = 0x545043482D534545L; // "TPCH-SEE" in hex

    private static final String[] REGIONS = {
        "AFRICA", "AMERICA", "ASIA", "EUROPE", "MIDDLE EAST"
    };

    private static final String[] NATIONS = {
        "ALGERIA", "ARGENTINA", "BRAZIL", "CANADA", "EGYPT",
        "ETHIOPIA", "FRANCE", "GERMANY", "INDIA", "INDONESIA",
        "IRAN", "IRAQ", "JAPAN", "JORDAN", "KENYA",
        "MOROCCO", "MOZAMBIQUE", "PERU", "CHINA", "ROMANIA",
        "SAUDI ARABIA", "VIETNAM", "RUSSIA", "UNITED KINGDOM", "UNITED STATES"
    };

    private static final int[] NATION_REGION_MAP = {
        0, 1, 1, 1, 4,
        0, 3, 3, 2, 2,
        4, 4, 2, 4, 0,
        0, 0, 1, 2, 3,
        4, 2, 3, 3, 1
    };

    private static final String[] SEGMENTS = {
        "AUTOMOBILE", "BUILDING", "FURNITURE", "MACHINERY", "HOUSEHOLD"
    };

    private static final String[] SHIP_MODES = {
        "REG AIR", "AIR", "RAIL", "TRUCK", "MAIL", "FOB", "SHIP"
    };

    private static final String[] SHIP_INSTRUCT = {
        "DELIVER IN PERSON", "COLLECT COD", "NONE", "TAKE BACK RETURN"
    };

    private static final String[] RETURN_FLAGS = {"R", "A", "N"};
    private static final String[] LINE_STATUSES = {"O", "F"};
    private static final String[] ORDER_STATUSES = {"O", "F", "P"};
    private static final String[] ORDER_PRIORITIES = {
        "1-URGENT", "2-HIGH", "3-MEDIUM", "4-NOT SPECIFIED", "5-LOW"
    };

    private static final String[] PART_TYPES_SYLLABLE1 = {"STANDARD", "SMALL", "MEDIUM", "LARGE", "ECONOMY", "PROMO"};
    private static final String[] PART_TYPES_SYLLABLE2 = {"ANODIZED", "BURNISHED", "PLATED", "POLISHED", "BRUSHED"};
    private static final String[] PART_TYPES_SYLLABLE3 = {"TIN", "NICKEL", "BRASS", "STEEL", "COPPER"};

    private static final String[] PART_CONTAINERS_SYLLABLE1 = {"SM", "MED", "LG", "JUMBO", "WRAP"};
    private static final String[] PART_CONTAINERS_SYLLABLE2 = {"CASE", "BOX", "BAG", "JAR", "PKG", "PACK", "CAN", "DRUM"};

    private TpchDataGenerator() {
    }

    /**
     * Complete in-memory TPC-H dataset container.
     */
    public record TpchTables(
        CupOfTea lineitem,
        CupOfTea orders,
        CupOfTea customer,
        CupOfTea part,
        CupOfTea supplier,
        CupOfTea partsupp,
        CupOfTea nation,
        CupOfTea region
    ) {}

    /**
     * Generates all TPC-H tables for a given scale factor (e.g., 0.01 for ~60k lineitems, 0.1 for ~600k lineitems).
     */
    public static TpchTables generate(double scaleFactor) {
        return generate(scaleFactor, DEFAULT_TPCH_SEED);
    }

    /**
     * Generates all TPC-H tables for a given scale factor and custom seed.
     */
    public static TpchTables generate(double scaleFactor, long seed) {
        int orderCount = Math.max(1_000, (int) Math.round(150_000 * scaleFactor));
        int customerCount = Math.max(100, (int) Math.round(30_000 * scaleFactor));
        int partCount = Math.max(200, (int) Math.round(20_000 * scaleFactor));
        int supplierCount = Math.max(10, (int) Math.round(1_000 * scaleFactor));

        SplittableRandom rng = new SplittableRandom(seed);

        CupOfTea region = generateRegion();
        CupOfTea nation = generateNation();
        CupOfTea supplier = generateSupplier(supplierCount, rng.split());
        CupOfTea part = generatePart(partCount, rng.split());
        CupOfTea partsupp = generatePartSupp(partCount, supplierCount, rng.split());
        CupOfTea customer = generateCustomer(customerCount, rng.split());
        CupOfTea orders = generateOrders(orderCount, customerCount, rng.split());
        CupOfTea lineitem = generateLineitem(orders, partCount, supplierCount, rng.split());

        return new TpchTables(lineitem, orders, customer, part, supplier, partsupp, nation, region);
    }

    public static CupOfTea generateRegion() {
        int n = REGIONS.length;
        long[] regionkeys = new long[n];
        String[] names = new String[n];
        String[] comments = new String[n];
        for (int i = 0; i < n; i++) {
            regionkeys[i] = i;
            names[i] = REGIONS[i];
            comments[i] = "Comment for region " + REGIONS[i];
        }
        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("r_regionkey", regionkeys, new BitSet(), n),
            new StringColumnVector("r_name", names, new BitSet(), n),
            new StringColumnVector("r_comment", comments, new BitSet(), n)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generateNation() {
        int n = NATIONS.length;
        long[] nationkeys = new long[n];
        String[] names = new String[n];
        long[] regionkeys = new long[n];
        String[] comments = new String[n];
        for (int i = 0; i < n; i++) {
            nationkeys[i] = i;
            names[i] = NATIONS[i];
            regionkeys[i] = NATION_REGION_MAP[i];
            comments[i] = "Comment for nation " + NATIONS[i];
        }
        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("n_nationkey", nationkeys, new BitSet(), n),
            new StringColumnVector("n_name", names, new BitSet(), n),
            new LongColumnVector("n_regionkey", regionkeys, new BitSet(), n),
            new StringColumnVector("n_comment", comments, new BitSet(), n)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generateSupplier(int count, SplittableRandom rng) {
        long[] suppkeys = new long[count];
        String[] names = new String[count];
        String[] addresses = new String[count];
        long[] nationkeys = new long[count];
        String[] phones = new String[count];
        double[] acctbals = new double[count];
        String[] comments = new String[count];

        for (int i = 0; i < count; i++) {
            suppkeys[i] = i + 1;
            names[i] = String.format(Locale.ROOT, "Supplier#%09d", i + 1);
            addresses[i] = "Address_" + (i + 1);
            nationkeys[i] = rng.nextInt(0, NATIONS.length);
            phones[i] = String.format(Locale.ROOT, "%02d-%03d-%03d-%04d", (i % 25) + 10, rng.nextInt(100, 999), rng.nextInt(100, 999), rng.nextInt(1000, 9999));
            acctbals[i] = Math.round((-999.99 + rng.nextDouble() * 10999.98) * 100.0) / 100.0;
            comments[i] = (i % 10 == 0) ? "special Customer Complaints recorded" : "normal supplier comment";
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("s_suppkey", suppkeys, new BitSet(), count),
            new StringColumnVector("s_name", names, new BitSet(), count),
            new StringColumnVector("s_address", addresses, new BitSet(), count),
            new LongColumnVector("s_nationkey", nationkeys, new BitSet(), count),
            new StringColumnVector("s_phone", phones, new BitSet(), count),
            new DoubleColumnVector("s_acctbal", acctbals, new BitSet(), count),
            new StringColumnVector("s_comment", comments, new BitSet(), count)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generatePart(int count, SplittableRandom rng) {
        long[] partkeys = new long[count];
        String[] names = new String[count];
        String[] mfgrs = new String[count];
        String[] brands = new String[count];
        String[] types = new String[count];
        int[] sizes = new int[count];
        String[] containers = new String[count];
        double[] retailprices = new double[count];
        String[] comments = new String[count];

        for (int i = 0; i < count; i++) {
            partkeys[i] = i + 1;
            names[i] = "Part_" + (i + 1);
            int m = rng.nextInt(1, 6);
            int b = rng.nextInt(1, 6);
            mfgrs[i] = "Manufacturer#" + m;
            brands[i] = "Brand#" + m + "" + b;
            types[i] = PART_TYPES_SYLLABLE1[rng.nextInt(0, PART_TYPES_SYLLABLE1.length)] + " "
                     + PART_TYPES_SYLLABLE2[rng.nextInt(0, PART_TYPES_SYLLABLE2.length)] + " "
                     + PART_TYPES_SYLLABLE3[rng.nextInt(0, PART_TYPES_SYLLABLE3.length)];
            sizes[i] = rng.nextInt(1, 51);
            containers[i] = PART_CONTAINERS_SYLLABLE1[rng.nextInt(0, PART_CONTAINERS_SYLLABLE1.length)] + " "
                          + PART_CONTAINERS_SYLLABLE2[rng.nextInt(0, PART_CONTAINERS_SYLLABLE2.length)];
            retailprices[i] = Math.round((900.0 + (i % 1000) + rng.nextDouble() * 100.0) * 100.0) / 100.0;
            comments[i] = "Part comment " + (i + 1);
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("p_partkey", partkeys, new BitSet(), count),
            new StringColumnVector("p_name", names, new BitSet(), count),
            new StringColumnVector("p_mfgr", mfgrs, new BitSet(), count),
            new StringColumnVector("p_brand", brands, new BitSet(), count),
            new StringColumnVector("p_type", types, new BitSet(), count),
            new IntColumnVector("p_size", sizes, new BitSet(), count),
            new StringColumnVector("p_container", containers, new BitSet(), count),
            new DoubleColumnVector("p_retailprice", retailprices, new BitSet(), count),
            new StringColumnVector("p_comment", comments, new BitSet(), count)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generatePartSupp(int partCount, int supplierCount, SplittableRandom rng) {
        int count = partCount * 4;
        long[] partkeys = new long[count];
        long[] suppkeys = new long[count];
        int[] availqtys = new int[count];
        double[] supplycosts = new double[count];
        String[] comments = new String[count];

        int idx = 0;
        for (int p = 1; p <= partCount; p++) {
            for (int s = 0; s < 4; s++) {
                partkeys[idx] = p;
                suppkeys[idx] = ((p + s * (partCount / 4 + 1)) % supplierCount) + 1;
                availqtys[idx] = rng.nextInt(1, 10_000);
                supplycosts[idx] = Math.round((1.0 + rng.nextDouble() * 1000.0) * 100.0) / 100.0;
                comments[idx] = "PartSupp comment";
                idx++;
            }
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("ps_partkey", partkeys, new BitSet(), count),
            new LongColumnVector("ps_suppkey", suppkeys, new BitSet(), count),
            new IntColumnVector("ps_availqty", availqtys, new BitSet(), count),
            new DoubleColumnVector("ps_supplycost", supplycosts, new BitSet(), count),
            new StringColumnVector("ps_comment", comments, new BitSet(), count)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generateCustomer(int count, SplittableRandom rng) {
        long[] custkeys = new long[count];
        String[] names = new String[count];
        String[] addresses = new String[count];
        long[] nationkeys = new long[count];
        String[] phones = new String[count];
        double[] acctbals = new double[count];
        String[] mktsegments = new String[count];
        String[] comments = new String[count];

        for (int i = 0; i < count; i++) {
            custkeys[i] = i + 1;
            names[i] = String.format(Locale.ROOT, "Customer#%09d", i + 1);
            addresses[i] = "CustAddress_" + (i + 1);
            nationkeys[i] = rng.nextInt(0, NATIONS.length);
            phones[i] = String.format(Locale.ROOT, "%02d-%03d-%03d-%04d", (i % 25) + 10, rng.nextInt(100, 999), rng.nextInt(100, 999), rng.nextInt(1000, 9999));
            acctbals[i] = Math.round((-999.99 + rng.nextDouble() * 10999.98) * 100.0) / 100.0;
            mktsegments[i] = SEGMENTS[rng.nextInt(0, SEGMENTS.length)];
            comments[i] = "Customer comment " + (i + 1);
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("c_custkey", custkeys, new BitSet(), count),
            new StringColumnVector("c_name", names, new BitSet(), count),
            new StringColumnVector("c_address", addresses, new BitSet(), count),
            new LongColumnVector("c_nationkey", nationkeys, new BitSet(), count),
            new StringColumnVector("c_phone", phones, new BitSet(), count),
            new DoubleColumnVector("c_acctbal", acctbals, new BitSet(), count),
            new StringColumnVector("c_mktsegment", mktsegments, new BitSet(), count),
            new StringColumnVector("c_comment", comments, new BitSet(), count)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generateOrders(int orderCount, int customerCount, SplittableRandom rng) {
        long[] orderkeys = new long[orderCount];
        long[] custkeys = new long[orderCount];
        String[] orderstatuses = new String[orderCount];
        double[] totalprices = new double[orderCount];
        String[] orderdates = new String[orderCount];
        String[] orderpriorities = new String[orderCount];
        String[] clerks = new String[orderCount];
        int[] shippriorities = new int[orderCount];
        String[] comments = new String[orderCount];

        LocalDate startDate = LocalDate.of(1992, 1, 1);
        int totalDays = 2400; // 1992-01-01 to ~1998-08-01

        for (int i = 0; i < orderCount; i++) {
            orderkeys[i] = i + 1;
            custkeys[i] = rng.nextInt(1, customerCount + 1);
            orderstatuses[i] = ORDER_STATUSES[rng.nextInt(0, ORDER_STATUSES.length)];
            totalprices[i] = 0.0; // updated or simulated
            int dayOffset = rng.nextInt(0, totalDays);
            orderdates[i] = startDate.plusDays(dayOffset).toString();
            orderpriorities[i] = ORDER_PRIORITIES[rng.nextInt(0, ORDER_PRIORITIES.length)];
            clerks[i] = String.format(Locale.ROOT, "Clerk#%09d", rng.nextInt(1, 1000));
            shippriorities[i] = 0;
            comments[i] = "Order comment " + (i + 1);
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("o_orderkey", orderkeys, new BitSet(), orderCount),
            new LongColumnVector("o_custkey", custkeys, new BitSet(), orderCount),
            new StringColumnVector("o_orderstatus", orderstatuses, new BitSet(), orderCount),
            new DoubleColumnVector("o_totalprice", totalprices, new BitSet(), orderCount),
            new StringColumnVector("o_orderdate", orderdates, new BitSet(), orderCount),
            new StringColumnVector("o_orderpriority", orderpriorities, new BitSet(), orderCount),
            new StringColumnVector("o_clerk", clerks, new BitSet(), orderCount),
            new IntColumnVector("o_shippriority", shippriorities, new BitSet(), orderCount),
            new StringColumnVector("o_comment", comments, new BitSet(), orderCount)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    public static CupOfTea generateLineitem(CupOfTea ordersDf, int partCount, int supplierCount, SplittableRandom rng) {
        int orderCount = ordersDf.size();
        LongColumnVector orderkeyVec = (LongColumnVector) ordersDf.getColumnVector("o_orderkey");
        StringColumnVector orderdateVec = (StringColumnVector) ordersDf.getColumnVector("o_orderdate");

        // ~4 lineitems per order
        int estimatedLineitems = orderCount * 4;
        long[] orderkeys = new long[estimatedLineitems];
        long[] partkeys = new long[estimatedLineitems];
        long[] suppkeys = new long[estimatedLineitems];
        int[] linenumbers = new int[estimatedLineitems];
        double[] quantities = new double[estimatedLineitems];
        double[] extendedprices = new double[estimatedLineitems];
        double[] discounts = new double[estimatedLineitems];
        double[] taxes = new double[estimatedLineitems];
        String[] returnflags = new String[estimatedLineitems];
        String[] linestatuses = new String[estimatedLineitems];
        String[] shipdates = new String[estimatedLineitems];
        String[] commitdates = new String[estimatedLineitems];
        String[] receiptdates = new String[estimatedLineitems];
        String[] shipinstructs = new String[estimatedLineitems];
        String[] shipmodes = new String[estimatedLineitems];
        String[] comments = new String[estimatedLineitems];

        int cur = 0;
        for (int i = 0; i < orderCount; i++) {
            long okey = orderkeyVec.getLong(i);
            LocalDate odate = LocalDate.parse(orderdateVec.getString(i));
            int lines = 4; // fixed 4 for stability or rng.nextInt(1, 8)
            for (int l = 1; l <= lines; l++) {
                orderkeys[cur] = okey;
                partkeys[cur] = rng.nextInt(1, partCount + 1);
                suppkeys[cur] = rng.nextInt(1, supplierCount + 1);
                linenumbers[cur] = l;
                double qty = rng.nextInt(1, 51);
                quantities[cur] = qty;
                double pricePerUnit = 100.0 + (partkeys[cur] % 1000) + rng.nextDouble() * 50.0;
                extendedprices[cur] = Math.round(qty * pricePerUnit * 100.0) / 100.0;
                discounts[cur] = Math.round(rng.nextDouble(0.00, 0.11) * 100.0) / 100.0;
                taxes[cur] = Math.round(rng.nextDouble(0.00, 0.09) * 100.0) / 100.0;

                int shipDelay = rng.nextInt(1, 122);
                LocalDate sDate = odate.plusDays(shipDelay);
                LocalDate cDate = odate.plusDays(rng.nextInt(30, 91));
                LocalDate rDate = sDate.plusDays(rng.nextInt(1, 31));

                shipdates[cur] = sDate.toString();
                commitdates[cur] = cDate.toString();
                receiptdates[cur] = rDate.toString();

                returnflags[cur] = RETURN_FLAGS[rng.nextInt(0, RETURN_FLAGS.length)];
                linestatuses[cur] = (sDate.isAfter(LocalDate.of(1995, 6, 17))) ? "O" : "F";
                shipinstructs[cur] = SHIP_INSTRUCT[rng.nextInt(0, SHIP_INSTRUCT.length)];
                shipmodes[cur] = SHIP_MODES[rng.nextInt(0, SHIP_MODES.length)];
                comments[cur] = "Lineitem comment " + cur;
                cur++;
            }
        }

        return CupOfTea.fromVectors(List.of(
            new LongColumnVector("l_orderkey", orderkeys, new BitSet(), cur),
            new LongColumnVector("l_partkey", partkeys, new BitSet(), cur),
            new LongColumnVector("l_suppkey", suppkeys, new BitSet(), cur),
            new IntColumnVector("l_linenumber", linenumbers, new BitSet(), cur),
            new DoubleColumnVector("l_quantity", quantities, new BitSet(), cur),
            new DoubleColumnVector("l_extendedprice", extendedprices, new BitSet(), cur),
            new DoubleColumnVector("l_discount", discounts, new BitSet(), cur),
            new DoubleColumnVector("l_tax", taxes, new BitSet(), cur),
            new StringColumnVector("l_returnflag", returnflags, new BitSet(), cur),
            new StringColumnVector("l_linestatus", linestatuses, new BitSet(), cur),
            new StringColumnVector("l_shipdate", shipdates, new BitSet(), cur),
            new StringColumnVector("l_commitdate", commitdates, new BitSet(), cur),
            new StringColumnVector("l_receiptdate", receiptdates, new BitSet(), cur),
            new StringColumnVector("l_shipinstruct", shipinstructs, new BitSet(), cur),
            new StringColumnVector("l_shipmode", shipmodes, new BitSet(), cur),
            new StringColumnVector("l_comment", comments, new BitSet(), cur)
        )).withMutationMode(MutationMode.COPY_ON_WRITE);
    }

    /**
     * Exports a DataFrame as standard pipe-separated or comma-separated TPC-H file.
     */
    public static void writeCsv(CupOfTea df, File destination, char delimiter) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter bw = new BufferedWriter(new java.io.OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destination)), StandardCharsets.UTF_8))) {
            List<String> colNames = df.getSchema().getColumnNames();
            int colCount = colNames.size();
            for (int i = 0; i < colCount; i++) {
                if (i > 0) bw.write(delimiter);
                bw.write(colNames.get(i));
            }
            bw.newLine();

            int rows = df.size();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < colCount; c++) {
                    if (c > 0) bw.write(delimiter);
                    Object val = df.getColumnVector(colNames.get(c)).getObject(r);
                    if (val != null) {
                        bw.write(val.toString());
                    }
                }
                bw.newLine();
            }
        }
    }
}
