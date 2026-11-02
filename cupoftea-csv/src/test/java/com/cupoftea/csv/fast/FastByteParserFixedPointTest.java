package com.cupoftea.csv.fast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FastByteParserFixedPointTest {

    @Test
    @DisplayName("parseFixedPoint1 should accurately parse 1-decimal numbers with SWAR and branchless unpacking")
    void testParseFixedPoint1() {
        String[] cases = {
            "0.0", "0.1", "1.2", "9.9", "12.3", "-0.1", "-1.2", "-9.9", "-12.3",
            "99.9", "-99.9", "100.0", "-100.0", "105.2", "-105.2",
            "   12.3  ", "\t-45.6\r\n", "7.0", "-7.0"
        };
        for (String c : cases) {
            byte[] buf = new byte[c.length() + 16];
            byte[] b = c.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, buf, 0, b.length);

            long actual = FastByteParser.parseFixedPoint1(buf, 0, b.length);
            double expectedDouble = Double.parseDouble(c.trim());
            long expected = Math.round(expectedDouble * 10.0);
            assertThat(actual)
                    .as("Testing case: %s", c)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("parseFixedPoint1 should match Double.parseDouble * 10 over random 1-decimal numbers")
    void testParseFixedPoint1Random() {
        Random rnd = new Random(12345);
        for (int i = 0; i < 10000; i++) {
            double v = (rnd.nextDouble() * 200.0 - 100.0);
            String formatted = String.format(java.util.Locale.US, "%.1f", v);
            byte[] buf = new byte[formatted.length() + 8];
            byte[] b = formatted.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, buf, 0, b.length);

            long actual = FastByteParser.parseFixedPoint1(buf, 0, b.length);
            long expected = Math.round(Double.parseDouble(formatted) * 10.0);
            assertThat(actual)
                    .as("Testing value %s", formatted)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("parseFixedPoint2 should accurately parse 2-decimal numbers with SWAR and branchless unpacking")
    void testParseFixedPoint2() {
        String[] cases = {
            "0.00", "0.01", "0.10", "1.23", "9.99", "12.34", "-0.01", "-1.23", "-9.99", "-12.34",
            "99.99", "-99.99", "100.00", "-100.00", "105.25", "-105.25",
            "   12.34  ", "\t-45.67\r\n", "7.00", "-7.00"
        };
        for (String c : cases) {
            byte[] buf = new byte[c.length() + 16];
            byte[] b = c.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, buf, 0, b.length);

            long actual = FastByteParser.parseFixedPoint2(buf, 0, b.length);
            double expectedDouble = Double.parseDouble(c.trim());
            long expected = Math.round(expectedDouble * 100.0);
            assertThat(actual)
                    .as("Testing case: %s", c)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("parseFixedPoint2 should match Double.parseDouble * 100 over random 2-decimal numbers")
    void testParseFixedPoint2Random() {
        Random rnd = new Random(54321);
        for (int i = 0; i < 10000; i++) {
            double v = (rnd.nextDouble() * 200.0 - 100.0);
            String formatted = String.format(java.util.Locale.US, "%.2f", v);
            byte[] buf = new byte[formatted.length() + 8];
            byte[] b = formatted.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, buf, 0, b.length);

            long actual = FastByteParser.parseFixedPoint2(buf, 0, b.length);
            long expected = Math.round(Double.parseDouble(formatted) * 100.0);
            assertThat(actual)
                    .as("Testing value %s", formatted)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("parseFixedPoint should throw NumberFormatException on empty string")
    void testParseFixedPointEmpty() {
        byte[] buf = "   ".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> FastByteParser.parseFixedPoint1(buf, 0, buf.length))
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> FastByteParser.parseFixedPoint2(buf, 0, buf.length))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("parseInt with SWAR path should parse 1 to 8 digit integers accurately")
    void testParseIntSwar() {
        String[] cases = {
            "0", "1", "9", "12", "99", "100", "999", "1234", "9999",
            "12345", "99999", "123456", "999999", "1234567", "9999999", "12345678",
            "-1", "-12", "-123", "-1234", "-12345", "-123456", "-1234567", "-12345678",
            "  12345  ", "\t-987654\r\n"
        };
        for (String c : cases) {
            byte[] b = c.getBytes(StandardCharsets.US_ASCII);
            byte[] buf = new byte[b.length + 16];
            System.arraycopy(b, 0, buf, 0, b.length);

            int actual = FastByteParser.parseInt(buf, 0, b.length);
            int expected = Integer.parseInt(c.trim());
            assertThat(actual).as("Testing parseInt: %s", c).isEqualTo(expected);
        }
    }
}
