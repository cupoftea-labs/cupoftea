package com.cupoftea.plot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cupoftea.CupOfTea;
import com.cupoftea.exceptions.CupOfTeaException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlotRegistryTest {

    @Test
    @DisplayName("Should throw CupOfTeaException when cupoftea-plot is not on classpath")
    void testMissingPlotProviderException() {
        CupOfTea df = new CupOfTea();

        assertThatThrownBy(df::plot)
                .isInstanceOf(CupOfTeaException.class)
                .hasMessageContaining("No plot provider found on the classpath")
                .hasMessageContaining("cupoftea-plot");
    }
}
