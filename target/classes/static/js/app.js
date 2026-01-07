// Global variables
let equityChart = null;
let fullChartData = []; // Store complete dataset

// File upload handler
document.getElementById('csvFile').addEventListener('change', function (e) {
    const fileName = e.target.files[0]?.name || 'No file chosen';
    document.getElementById('fileName').textContent = fileName;
});

// Run Backtest button
document.getElementById('runBacktest').addEventListener('click', async function () {
    const fileInput = document.getElementById('csvFile');
    const shortSma = document.getElementById('shortSma').value;
    const longSma = document.getElementById('longSma').value;

    if (!fileInput.files[0]) {
        alert('Please upload a CSV file first!');
        return;
    }

    // Show loading, hide results
    document.getElementById('loading').style.display = 'block';
    document.getElementById('results').style.display = 'none';
    document.getElementById('optimizeResults').style.display = 'none';

    // Prepare form data
    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('shortSma', shortSma);
    formData.append('longSma', longSma);

    try {
        const response = await fetch('/api/backtest', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (data.success) {
            displayResults(data);
        } else {
            alert('Error: ' + data.error);
        }
    } catch (error) {
        alert('Request failed: ' + error.message);
    } finally {
        document.getElementById('loading').style.display = 'none';
    }
});

// Optimize button
document.getElementById('optimize').addEventListener('click', async function () {
    const fileInput = document.getElementById('csvFile');

    if (!fileInput.files[0]) {
        alert('Please upload a CSV file first!');
        return;
    }

    // Show loading with specific message
    const loadingDiv = document.getElementById('loading');
    loadingDiv.innerHTML = '<div class="spinner"></div><p>Optimizing parameters... This may take 1-2 minutes.<br>Testing 100+ SMA combinations...</p>';
    loadingDiv.style.display = 'block';
    document.getElementById('results').style.display = 'none';
    document.getElementById('optimizeResults').style.display = 'none';

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        const response = await fetch('/api/optimize', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (data.success) {
            displayOptimizeResults(data.results);
        } else {
            // Show detailed error message
            alert('Optimization Error:\n\n' + data.error +
                '\n\nTips:\n• Ensure CSV has columns: Date,Open,High,Low,Close,Volume\n' +
                '• Dates must be in YYYY-MM-DD format\n' +
                '• Need at least 250+ days of data');
        }
    } catch (error) {
        alert('Request failed: ' + error.message + '\n\nPlease check your CSV file format.');
    } finally {
        loadingDiv.style.display = 'none';
    }
});

// Display backtest results
function displayResults(data) {
    const metrics = data.metrics;

    // Update metrics cards
    document.getElementById('totalReturn').textContent = metrics.totalReturn.toFixed(2) + '%';
    document.getElementById('annualizedReturn').textContent = metrics.annualizedReturn.toFixed(2) + '%';
    document.getElementById('sharpeRatio').textContent = metrics.sharpeRatio.toFixed(3);
    document.getElementById('sortinoRatio').textContent = metrics.sortinoRatio.toFixed(3);
    document.getElementById('winRate').textContent = metrics.winRate.toFixed(2) + '%';
    document.getElementById('profitFactor').textContent = metrics.profitFactor.toFixed(2);
    document.getElementById('maxDrawdown').textContent = metrics.maxDrawdown.toFixed(2) + '%';

    // Display detailed report
    document.getElementById('detailedReport').textContent = data.report;

    // Store full data and draw chart
    fullChartData = data.chartData;
    drawEquityChart(fullChartData);

    // Set up date range inputs
    setupDateRangeInputs(fullChartData);

    // Show results
    document.getElementById('results').style.display = 'block';
}

// Display optimization results
function displayOptimizeResults(results) {
    const tbody = document.getElementById('optimizeTableBody');
    tbody.innerHTML = '';

    results.forEach((result, index) => {
        const row = tbody.insertRow();
        row.innerHTML = `
            <td>${index + 1}</td>
            <td>${result.shortSma}</td>
            <td>${result.longSma}</td>
            <td>${result.totalReturn.toFixed(2)}%</td>
            <td>${result.sharpeRatio.toFixed(3)}</td>
            <td>${result.winRate.toFixed(2)}%</td>
            <td>${result.maxDrawdown.toFixed(2)}%</td>
            <td>
                <button class="use-params-btn" onclick="useParams(${result.shortSma}, ${result.longSma})">
                    Use These
                </button>
            </td>
        `;
    });

    document.getElementById('optimizeResults').style.display = 'block';
}

// Use optimized parameters
function useParams(shortSma, longSma) {
    document.getElementById('shortSma').value = shortSma;
    document.getElementById('longSma').value = longSma;
    alert(`Parameters updated! Short SMA: ${shortSma}, Long SMA: ${longSma}\nClick "Run Backtest" to see full results.`);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Setup date range inputs with min/max values
function setupDateRangeInputs(chartData) {
    if (chartData.length === 0) return;

    const dates = chartData.map(point => new Date(point.date));
    const minDate = new Date(Math.min(...dates));
    const maxDate = new Date(Math.max(...dates));

    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('endDate');

    startInput.min = minDate.toISOString().split('T')[0];
    startInput.max = maxDate.toISOString().split('T')[0];
    startInput.value = minDate.toISOString().split('T')[0];

    endInput.min = minDate.toISOString().split('T')[0];
    endInput.max = maxDate.toISOString().split('T')[0];
    endInput.value = maxDate.toISOString().split('T')[0];
}

// Time period filter buttons
document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', function () {
        // Update active state
        document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
        this.classList.add('active');

        const period = this.dataset.period;
        filterChartByPeriod(period);
    });
});

// Apply custom date range
document.getElementById('applyDateRange').addEventListener('click', function () {
    const startDate = new Date(document.getElementById('startDate').value);
    const endDate = new Date(document.getElementById('endDate').value);

    if (startDate > endDate) {
        alert('Start date must be before end date!');
        return;
    }

    // Remove active state from period buttons
    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));

    filterChartByDateRange(startDate, endDate);
});

// Reset chart to show all data
document.getElementById('resetChart').addEventListener('click', function () {
    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
    document.querySelector('.filter-btn[data-period="all"]').classList.add('active');
    drawEquityChart(fullChartData);
});

// Filter chart by time period
function filterChartByPeriod(period) {
    if (fullChartData.length === 0) return;

    const now = new Date(fullChartData[fullChartData.length - 1].date);
    let startDate = new Date(now);

    switch (period) {
        case '1w':
            startDate.setDate(now.getDate() - 7);
            break;
        case '1m':
            startDate.setMonth(now.getMonth() - 1);
            break;
        case '3m':
            startDate.setMonth(now.getMonth() - 3);
            break;
        case '6m':
            startDate.setMonth(now.getMonth() - 6);
            break;
        case '1y':
            startDate.setFullYear(now.getFullYear() - 1);
            break;
        case 'all':
        default:
            drawEquityChart(fullChartData);
            return;
    }

    filterChartByDateRange(startDate, now);
}

// Filter chart by custom date range
function filterChartByDateRange(startDate, endDate) {
    const filtered = fullChartData.filter(point => {
        const date = new Date(point.date);
        return date >= startDate && date <= endDate;
    });

    if (filtered.length === 0) {
        alert('No data in selected date range!');
        return;
    }

    drawEquityChart(filtered);
}

// Draw equity curve chart
function drawEquityChart(chartData) {
    const ctx = document.getElementById('equityChart').getContext('2d');

    // Destroy existing chart if it exists
    if (equityChart) {
        equityChart.destroy();
    }

    const labels = chartData.map(point => new Date(point.date).toLocaleDateString());
    const values = chartData.map(point => point.value);

    equityChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Portfolio Value ($)',
                data: values,
                borderColor: '#667eea',
                backgroundColor: 'rgba(102, 126, 234, 0.1)',
                borderWidth: 2,
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label: function (context) {
                            return 'Value: $' + context.parsed.y.toLocaleString();
                        }
                    }
                },
                zoom: {
                    pan: {
                        enabled: true,
                        mode: 'x'
                    },
                    zoom: {
                        wheel: {
                            enabled: true
                        },
                        pinch: {
                            enabled: true
                        },
                        mode: 'x'
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: false,
                    ticks: {
                        callback: function (value) {
                            return '$' + value.toLocaleString();
                        }
                    }
                }
            }
        }
    });
}
