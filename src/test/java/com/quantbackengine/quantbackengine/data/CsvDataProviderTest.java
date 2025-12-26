package com.quantbackengine.quantbackengine.data;

import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.data.DataProvider;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class CsvDataProviderTest {

    @Test
    void testLoadAndFilterData() throws Exception {
        DataProvider provider = new CsvDataProvider("data/test_prices.csv");

        BarSeries series = provider.getHistoricalData("TEST",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 10));

        assertEquals(10, series.getBarCount());
        assertEquals("TEST", series.getName());
        assertEquals(100.0, series.getBar(0).getClosePrice().doubleValue());
        assertEquals(90.0, series.getBar(9).getClosePrice().doubleValue());
    }

    @Test
    void testDateRangeFiltering() throws Exception {
        DataProvider provider = new CsvDataProvider("data/test_prices.csv");

        BarSeries series = provider.getHistoricalData("TEST",
                LocalDate.of(2020, 1, 3),
                LocalDate.of(2020, 1, 7));

        assertEquals(5, series.getBarCount());
        assertEquals(120.0, series.getBar(0).getClosePrice().doubleValue());
        assertEquals(100.0, series.getBar(4).getClosePrice().doubleValue());
    }
}