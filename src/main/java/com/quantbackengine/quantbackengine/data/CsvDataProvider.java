package com.quantbackengine.quantbackengine.data;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

/**
 * A simple DataProvider that loads historical data from a CSV file located in
 * resources.
 * Expected CSV format (Yahoo Finance style):
 * Date,Open,High,Low,Close,Adj Close,Volume
 */
public class CsvDataProvider implements DataProvider {

    private final String resourcePath;

    /**
     * Constructor.
     *
     * @param resourcePath path relative to src/main/resources, e.g.,
     *                     "data/AAPL.csv"
     */
    public CsvDataProvider(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public BarSeries getHistoricalData(String symbol, LocalDate start, LocalDate end) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("CSV file not found in resources: " + resourcePath);
        }

        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        java.io.BufferedReader br = new java.io.BufferedReader(reader);

        // Skip preamble lines until we find the header starting with "date"
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().toLowerCase().startsWith("date")) {
                break;
            }
        }

        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withHeader("Date", "Open", "High", "Low", "Close", "Volume")
                .withIgnoreHeaderCase()
                .withTrim()
                .parse(br);

        List<Bar> bars = new ArrayList<>();
        ZoneId zoneId = ZoneId.of("UTC"); // Yahoo data is typically in UTC

        for (CSVRecord record : records) {
            LocalDate date = LocalDate.parse(record.get("Date"));
            if (date.isBefore(start) || date.isAfter(end)) {
                continue;
            }

            ZonedDateTime zonedDateTime = date.atStartOfDay(zoneId);
            Bar bar = new BaseBar(Duration.ofDays(1), zonedDateTime,
                    record.get("Open"),
                    record.get("High"),
                    record.get("Low"),
                    record.get("Close"),
                    record.get("Volume"));
            bars.add(bar);
        }

        // Sort bars chronologically (oldest first) - TA4J requires this
        Collections.sort(bars, (b1, b2) -> b1.getEndTime().compareTo(b2.getEndTime()));

        BarSeries series = new BaseBarSeries(symbol, bars);
        return series;
    }
}