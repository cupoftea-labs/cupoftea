package com.cupoftea.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.MutationMode;
import com.cupoftea.table.vector.BooleanColumnVector;
import com.cupoftea.table.vector.IntColumnVector;
import com.cupoftea.table.vector.ObjectColumnVector;
import com.cupoftea.table.vector.StringColumnVector;

public class StringOperationsTest {

    @Test
    @DisplayName("Should perform toLowerCase, toUpperCase, and capitalize correctly")
    void testCasingOperations() {
        CupOfTea df = new CupOfTea();
        df.addColumn("text", String.class);
        df.addRow(new HashMap<>() {{ put("text", "hELlo WoRLD"); }});
        df.addRow(new HashMap<>() {{ put("text", "JAVA PROGRAMMING"); }});
        df.addRow(new HashMap<>() {{ put("text", "cup_of_tea"); }});
        df.addRow(new HashMap<>() {{ put("text", null); }});

        // toLowerCase
        StringColumnVector lower = df.col("text").str.toLowerCase();
        assertThat(lower.getString(0)).isEqualTo("hello world");
        assertThat(lower.getString(1)).isEqualTo("java programming");
        assertThat(lower.getString(2)).isEqualTo("cup_of_tea");
        assertThat(lower.isNull(3)).isTrue();

        // toUpperCase
        StringColumnVector upper = df.col("text").str().toUpperCase();
        assertThat(upper.getString(0)).isEqualTo("HELLO WORLD");
        assertThat(upper.getString(1)).isEqualTo("JAVA PROGRAMMING");
        assertThat(upper.getString(2)).isEqualTo("CUP_OF_TEA");
        assertThat(upper.isNull(3)).isTrue();

        // capitalize
        StringColumnVector cap = df.col("text").str.capitalize();
        assertThat(cap.getString(0)).isEqualTo("Hello world");
        assertThat(cap.getString(1)).isEqualTo("Java programming");
        assertThat(cap.getString(2)).isEqualTo("Cup_of_tea");
        assertThat(cap.isNull(3)).isTrue();
    }

    @Test
    @DisplayName("Should perform trim, strip, lstrip, and rstrip")
    void testWhitespaceTrimming() {
        CupOfTea df = new CupOfTea();
        df.addColumn("text", String.class);
        df.addRow(new HashMap<>() {{ put("text", "   hello world   "); }});
        df.addRow(new HashMap<>() {{ put("text", "\t\n  unicode space \u2003  "); }});
        df.addRow(new HashMap<>() {{ put("text", null); }});

        StringColumnVector trim = df.col("text").str.trim();
        assertThat(trim.getString(0)).isEqualTo("hello world");
        assertThat(trim.isNull(2)).isTrue();

        StringColumnVector strip = df.col("text").str.strip();
        assertThat(strip.getString(0)).isEqualTo("hello world");
        assertThat(strip.getString(1)).isEqualTo("unicode space");
        assertThat(strip.isNull(2)).isTrue();

        StringColumnVector lstrip = df.col("text").str.lstrip();
        assertThat(lstrip.getString(0)).isEqualTo("hello world   ");
        assertThat(lstrip.isNull(2)).isTrue();

        StringColumnVector rstrip = df.col("text").str.rstrip();
        assertThat(rstrip.getString(0)).isEqualTo("   hello world");
        assertThat(rstrip.isNull(2)).isTrue();
    }

    @Test
    @DisplayName("Should perform contains, startsWith, and endsWith returning BooleanColumnVector")
    void testContainmentAndMatching() {
        CupOfTea df = new CupOfTea();
        df.addColumn("email", String.class);
        df.addRow(new HashMap<>() {{ put("email", "john.doe@example.com"); }});
        df.addRow(new HashMap<>() {{ put("email", "alice@corp.org"); }});
        df.addRow(new HashMap<>() {{ put("email", "admin@example.com"); }});
        df.addRow(new HashMap<>() {{ put("email", null); }});

        BooleanColumnVector contains = df.col("email").str.contains("example");
        assertThat(contains.getBoolean(0)).isTrue();
        assertThat(contains.getBoolean(1)).isFalse();
        assertThat(contains.getBoolean(2)).isTrue();
        assertThat(contains.isNull(3)).isTrue();

        BooleanColumnVector starts = df.col("email").str.startsWith("john");
        assertThat(starts.getBoolean(0)).isTrue();
        assertThat(starts.getBoolean(1)).isFalse();
        assertThat(starts.isNull(3)).isTrue();

        BooleanColumnVector ends = df.col("email").str.endsWith(".com");
        assertThat(ends.getBoolean(0)).isTrue();
        assertThat(ends.getBoolean(1)).isFalse();
        assertThat(ends.getBoolean(2)).isTrue();
        assertThat(ends.isNull(3)).isTrue();
    }

    @Test
    @DisplayName("Should perform replace and replaceAll with regex")
    void testReplaceOperations() {
        CupOfTea df = new CupOfTea();
        df.addColumn("data", String.class);
        df.addRow(new HashMap<>() {{ put("data", "item-123-ABC-123"); }});
        df.addRow(new HashMap<>() {{ put("data", "code-999-XYZ"); }});
        df.addRow(new HashMap<>() {{ put("data", null); }});

        StringColumnVector replaced = df.col("data").str.replace("123", "000");
        assertThat(replaced.getString(0)).isEqualTo("item-000-ABC-000");
        assertThat(replaced.getString(1)).isEqualTo("code-999-XYZ");
        assertThat(replaced.isNull(2)).isTrue();

        StringColumnVector regexReplaced = df.col("data").str.replaceAll("\\d+", "#");
        assertThat(regexReplaced.getString(0)).isEqualTo("item-#-ABC-#");
        assertThat(regexReplaced.getString(1)).isEqualTo("code-#-XYZ");
        assertThat(regexReplaced.isNull(2)).isTrue();
    }

    @Test
    @DisplayName("Should perform split returning ObjectColumnVector of String arrays")
    void testSplit() {
        CupOfTea df = new CupOfTea();
        df.addColumn("csv", String.class);
        df.addRow(new HashMap<>() {{ put("csv", "apple,banana,orange,grape"); }});
        df.addRow(new HashMap<>() {{ put("csv", "red,blue"); }});
        df.addRow(new HashMap<>() {{ put("csv", null); }});

        ObjectColumnVector splitN = df.col("csv").str.split(",", 2);
        String[] parts0 = (String[]) splitN.getObject(0);
        assertThat(parts0).containsExactly("apple", "banana,orange,grape");
        String[] parts1 = (String[]) splitN.getObject(1);
        assertThat(parts1).containsExactly("red", "blue");
        assertThat(splitN.isNull(2)).isTrue();

        ObjectColumnVector splitAll = df.col("csv").str.split(",");
        String[] partsAll0 = (String[]) splitAll.getObject(0);
        assertThat(partsAll0).containsExactly("apple", "banana", "orange", "grape");
    }

    @Test
    @DisplayName("Should perform substring, slice, and length")
    void testSubstringAndLength() {
        CupOfTea df = new CupOfTea();
        df.addColumn("word", String.class);
        df.addRow(new HashMap<>() {{ put("word", "CupOfTea"); }});
        df.addRow(new HashMap<>() {{ put("word", "DataFrame"); }});
        df.addRow(new HashMap<>() {{ put("word", "AI"); }});
        df.addRow(new HashMap<>() {{ put("word", null); }});

        StringColumnVector sub = df.col("word").str.substring(0, 3);
        assertThat(sub.getString(0)).isEqualTo("Cup");
        assertThat(sub.getString(1)).isEqualTo("Dat");
        assertThat(sub.getString(2)).isEqualTo("AI");
        assertThat(sub.isNull(3)).isTrue();

        // slice with negative index
        StringColumnVector sliced = df.col("word").str.slice(0, -3);
        assertThat(sliced.getString(0)).isEqualTo("CupOf");
        assertThat(sliced.getString(1)).isEqualTo("DataFr");
        assertThat(sliced.getString(2)).isEqualTo("");
        assertThat(sliced.isNull(3)).isTrue();

        // length
        IntColumnVector len = df.col("word").str.length();
        assertThat(len.getInt(0)).isEqualTo(8);
        assertThat(len.getInt(1)).isEqualTo(9);
        assertThat(len.getInt(2)).isEqualTo(2);
        assertThat(len.isNull(3)).isTrue();
    }

    @Test
    @DisplayName("Should perform padLeft and padRight")
    void testPadding() {
        CupOfTea df = new CupOfTea();
        df.addColumn("id", String.class);
        df.addRow(new HashMap<>() {{ put("id", "42"); }});
        df.addRow(new HashMap<>() {{ put("id", "12345"); }});
        df.addRow(new HashMap<>() {{ put("id", null); }});

        StringColumnVector left = df.col("id").str.padLeft(5, '0');
        assertThat(left.getString(0)).isEqualTo("00042");
        assertThat(left.getString(1)).isEqualTo("12345");
        assertThat(left.isNull(2)).isTrue();

        StringColumnVector right = df.col("id").str.padRight(5, '-');
        assertThat(right.getString(0)).isEqualTo("42---");
        assertThat(right.getString(1)).isEqualTo("12345");
        assertThat(right.isNull(2)).isTrue();
    }

    @Test
    @DisplayName("Should correctly handle Unicode, special characters, and emojis")
    void testUnicodeAndSpecialCharacters() {
        CupOfTea df = new CupOfTea();
        df.addColumn("intl", String.class);
        df.addRow(new HashMap<>() {{ put("intl", "  café  "); }});
        df.addRow(new HashMap<>() {{ put("intl", "naïve approach"); }});
        df.addRow(new HashMap<>() {{ put("intl", "🚀 rocket space 🛸"); }});
        df.addRow(new HashMap<>() {{ put("intl", "こんにちは 世界"); }});
        df.addRow(new HashMap<>() {{ put("intl", "Line1\nLine2\tTabbed"); }});

        assertThat(df.col("intl").str.trim().getString(0)).isEqualTo("café");
        assertThat(df.col("intl").str.toUpperCase().getString(0)).isEqualTo("  CAFÉ  ");
        assertThat(df.col("intl").str.capitalize().getString(1)).isEqualTo("Naïve approach");
        assertThat(df.col("intl").str.contains("rocket").getBoolean(2)).isTrue();
        assertThat(df.col("intl").str.contains("世界").getBoolean(3)).isTrue();
        assertThat(df.col("intl").str.replaceAll("\\s+", " ").getString(4)).isEqualTo("Line1 Line2 Tabbed");
    }

    @Test
    @DisplayName("Should support both withColumn projection and transformColumn in-place")
    void testWithColumnAndTransformColumn() {
        CupOfTea df = new CupOfTea();
        df.addColumn("name", String.class);
        df.addRow(new HashMap<>() {{ put("name", "Alice"); }});
        df.addRow(new HashMap<>() {{ put("name", "Bob"); }});

        // withColumn generates a new column
        df.withColumn("name_upper", df.col("name").str.toUpperCase());
        assertThat(df.getHeaders()).extracting(Column::columnName).containsExactly("name", "name_upper");
        assertThat(df.col("name").getString(0)).isEqualTo("Alice");
        assertThat(df.col("name_upper").getString(0)).isEqualTo("ALICE");

        // transformColumn modifies column in-place
        df.transformColumn("name", df.col("name").str.toLowerCase());
        assertThat(df.col("name").getString(0)).isEqualTo("alice");
        assertThat(df.col("name").getString(1)).isEqualTo("bob");

        // transformColumn with lambda
        df.transformColumn("name", col -> col.str().capitalize());
        assertThat(df.col("name").getString(0)).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Should respect MutationMode COPY_ON_WRITE vs IN_PLACE")
    void testMutationModes() {
        CupOfTea original = new CupOfTea(MutationMode.COPY_ON_WRITE)
            .addColumn("city", String.class)
            .addRow(new HashMap<>() {{ put("city", "san francisco"); }});

        CupOfTea mutated = original.withColumn("city_clean", original.col("city").str.capitalize());
        assertThat(mutated).isNotSameAs(original);
        assertThat(original.getHeaders()).hasSize(1);
        assertThat(mutated.getHeaders()).hasSize(2);
        assertThat(mutated.col("city_clean").getString(0)).isEqualTo("San francisco");

        // In place mode
        CupOfTea inPlace = new CupOfTea(MutationMode.IN_PLACE);
        inPlace.addColumn("city", String.class);
        inPlace.addRow(new HashMap<>() {{ put("city", "san francisco"); }});
        CupOfTea same = inPlace.withColumn("city_clean", inPlace.col("city").str.capitalize());
        assertThat(same).isSameAs(inPlace);
        assertThat(inPlace.getHeaders()).hasSize(2);
    }

    @Test
    @DisplayName("Should support direct methods on StringColumnVector instance")
    void testDirectStringColumnVectorMethods() {
        StringColumnVector vec = new StringColumnVector("raw", 4, true);
        vec.appendString("  alpha  ");
        vec.appendString("BETA");
        vec.appendNull();
        vec.appendString("gamma");

        StringColumnVector trimmed = vec.trim();
        assertThat(trimmed.getString(0)).isEqualTo("alpha");

        StringColumnVector upper = vec.toUpperCase();
        assertThat(upper.getString(0)).isEqualTo("  ALPHA  ");

        StringColumnVector cap = vec.capitalize();
        assertThat(cap.getString(1)).isEqualTo("Beta");

        IntColumnVector len = vec.length();
        assertThat(len.getInt(1)).isEqualTo(4);
        assertThat(len.isNull(2)).isTrue();

        // In-place modification
        vec.trimInPlace();
        assertThat(vec.getString(0)).isEqualTo("alpha");
    }

    @Test
    @DisplayName("Should throw clear exception for non-existent columns in col() or transformColumn()")
    void testErrorHandling() {
        CupOfTea df = new CupOfTea();
        df.addColumn("x", String.class);

        assertThatThrownBy(() -> df.col("non_existent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non_existent");

        assertThatThrownBy(() -> df.transformColumn("non_existent", new StringColumnVector("x", 0, true)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
