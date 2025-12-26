package com.quantbackengine.quantbackengine.ui;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.report.Reporter;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import java.awt.*;

public class Dashboard extends JFrame {

    private final Reporter reporter;

    public Dashboard(Reporter reporter) {
        this.reporter = reporter;
        initUI();
    }

    private void initUI() {
        setTitle("QuantBackEngine Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null); // Center on screen
    }

    public void show(BacktestResult result, String reportText) {
        // Main container with vertical split
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6); // Chart takes 60% of height

        // Top: Chart
        JFreeChart chart = reporter.getEquityCurveChart(result);
        ChartPanel chartPanel = new ChartPanel(chart);
        splitPane.setTopComponent(chartPanel);

        // Bottom: Text Report
        JTextArea textArea = new JTextArea(reportText);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        splitPane.setBottomComponent(scrollPane);

        add(splitPane);
        setVisible(true);
    }
}
