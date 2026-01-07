package com.quantbackengine.quantbackengine.ui;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.report.Reporter;
import com.quantbackengine.quantbackengine.service.BacktestService;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import java.awt.*;

public class Dashboard extends JFrame {

    private final Reporter reporter;
    private final BacktestService backtestService;

    // UI Components
    private JTextField fileField;
    private JTextField shortSmaField;
    private JTextField longSmaField;
    private JTextArea textArea;
    private ChartPanel chartPanel;

    public Dashboard(Reporter reporter) {
        this.reporter = reporter;
        this.backtestService = new BacktestService();
        initUI();
    }

    private void initUI() {
        setTitle("QuantBackEngine Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null); // Center on screen
        setLayout(new BorderLayout());

        // Top: Control Panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        controlPanel.add(new JLabel("CSV File:"));
        fileField = new JTextField("data/AAPL.csv", 25);
        controlPanel.add(fileField);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseFile());
        controlPanel.add(browseButton);

        controlPanel.add(new JLabel("  Short SMA:"));
        shortSmaField = new JTextField("50", 5);
        controlPanel.add(shortSmaField);

        controlPanel.add(new JLabel("Long SMA:"));
        longSmaField = new JTextField("200", 5);
        controlPanel.add(longSmaField);

        JButton runButton = new JButton("Run Backtest");
        runButton.addActionListener(e -> runBacktest());
        controlPanel.add(runButton);

        JButton optimizeButton = new JButton("Optimize Parameters");
        optimizeButton.addActionListener(e -> runOptimization());
        controlPanel.add(optimizeButton);

        add(controlPanel, BorderLayout.NORTH);

        // Center: Split Pane (Chart + Report)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6); // Chart takes 60%

        // Chart
        chartPanel = new ChartPanel(null);
        chartPanel.setPreferredSize(new Dimension(800, 400));
        splitPane.setTopComponent(chartPanel);

        // Report
        textArea = new JTextArea();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        splitPane.setBottomComponent(scrollPane);

        add(splitPane, BorderLayout.CENTER);
    }

    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser(new java.io.File("."));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            fileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void runBacktest() {
        try {
            String csvPath = fileField.getText();
            int shortSma = Integer.parseInt(shortSmaField.getText());
            int longSma = Integer.parseInt(longSmaField.getText());

            // Run backtest
            BacktestService.RunResult result = backtestService.runBacktest(csvPath, shortSma, longSma);

            // Update Text
            textArea.setText(result.reportString());
            textArea.setCaretPosition(0);

            // Update Chart
            JFreeChart chart = reporter.getEquityCurveChart(result.backtestResult());
            chartPanel.setChart(chart);

            JOptionPane.showMessageDialog(this, "Backtest Finished!");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Backtest Error",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void runOptimization() {
        try {
            String csvPath = fileField.getText();
            textArea.setText(
                    "Optimizing parameters... This may take a minute.\nScanning SMA combinations from 5-50 (short) and 60-200 (long)...\n");

            // Run optimization in background thread to keep UI responsive
            new Thread(() -> {
                try {
                    com.quantbackengine.quantbackengine.service.StrategyOptimizer optimizer = new com.quantbackengine.quantbackengine.service.StrategyOptimizer();
                    var results = optimizer.optimizeSma(csvPath, 5, 50, 60, 200);

                    // Format results
                    StringBuilder sb = new StringBuilder();
                    sb.append("Parameter Optimization Results (Top 10)\n");
                    sb.append("==========================================\n\n");
                    sb.append(String.format("%-8s %-8s %-15s %-12s %-12s %-12s\n",
                            "Short", "Long", "Return", "Sharpe", "Win Rate", "Max DD"));
                    sb.append("─".repeat(80)).append("\n");

                    for (int i = 0; i < Math.min(10, results.size()); i++) {
                        var res = results.get(i);
                        sb.append(String.format("%-8d %-8d %-15.2f%% %-12.3f %-12.2f%% %-12.2f%%\n",
                                res.shortSma(),
                                res.longSma(),
                                res.totalReturn() * 100,
                                res.sharpeRatio(),
                                res.winRate() * 100,
                                res.maxDrawdown() * 100));
                    }

                    sb.append("\n✅ Best Parameters: Short SMA = ").append(results.get(0).shortSma())
                            .append(", Long SMA = ").append(results.get(0).longSma());

                    // Update UI on Swing thread
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        textArea.setText(sb.toString());
                        textArea.setCaretPosition(0);

                        // Auto-fill best parameters
                        shortSmaField.setText(String.valueOf(results.get(0).shortSma()));
                        longSmaField.setText(String.valueOf(results.get(0).longSma()));

                        JOptionPane.showMessageDialog(this,
                                "Optimization complete! Best parameters loaded.\nClick 'Run Backtest' to see full results.");
                    });

                } catch (Exception ex) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Optimization Error: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    });
                }
            }).start();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    public void show(BacktestResult result, String reportText) {
        // Initial view
        textArea.setText(reportText);
        if (result != null) {
            chartPanel.setChart(reporter.getEquityCurveChart(result));
        }
        setVisible(true);
    }
}
