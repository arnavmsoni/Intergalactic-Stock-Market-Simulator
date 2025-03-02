package com.example;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // ------------------------------------------------------------------------
    // Simulation Constants
    // ------------------------------------------------------------------------
    private static final int TOTAL_MONTHS = 12;           // January to December
    private static final int SECONDS_PER_MONTH = 60;      // Each month = 60 seconds
    private static final int TOTAL_TIME = TOTAL_MONTHS * SECONDS_PER_MONTH;  // 12 minutes

    private static final int MIN_NEWS_PER_MONTH = 2;
    private static final int MAX_NEWS_PER_MONTH = 3;
    private static final int NEWS_IMPACT_DELAY = 10;  
    private static final int NEWS_IMPACT_DURATION = 15;
    private static final double NEWS_IMPACT_MULTIPLIER = 0.20;

    private static final double PRICE_MOVE_UP = 5;   // Max upward price move
    private static final double PRICE_MOVE_DOWN = 5; // Max downward price move

    // ------------------------------------------------------------------------
    // Game State
    // ------------------------------------------------------------------------
    private int currentMonthIndex = 0;
    private int secondsLeftInMonth = SECONDS_PER_MONTH;
    private int totalTimeLeft = TOTAL_TIME;

    private double startingMoney = 10000.0;
    private double playerMoney = startingMoney;

    // Portfolio: stockName -> shares owned
    private Map<String, Integer> portfolio = new HashMap<>();

    // Net-worth chart series
    private XYChart.Series<Number, Number> netWorthSeries = new XYChart.Series<>();
    private int chartTimeCounter = 0;

    // Monthly news triggers
    private Set<Integer> monthlyNewsTriggers = new HashSet<>();
    private int monthlyNewsCount = 0;

    private Random random = new Random();

    // ------------------------------------------------------------------------
    // JavaFX UI Elements
    // ------------------------------------------------------------------------
    // Top bar
    private Label timeLeftLabel;
    private Label monthLabel;
    private Label secondsInMonthLabel;

    // Portfolio summary
    private Label cashLabel;
    private Label investedLabel;
    private Label netWorthLabel;

    // Center area
    private TableView<Stock> stockTable;
    private VBox stockDetailPane;
    private Label stockDescriptionLabel;
    private LineChart<Number, Number> stockChart;

    // Right panel (Trade controls + Market log)
    private TextField buySellSharesField;
    private TextArea marketLogArea;

    // Bottom area
    private TextArea newsArea;
    private LineChart<Number, Number> netWorthChart;

    // Stock data
    private ObservableList<Stock> stocks;

    // Predefined news headlines
    private List<String> possibleNews = new ArrayList<>();
    // Groups for complementary/substitute logic
    private Map<String, List<String>> stockGroups = new HashMap<>();

    // Formatter for money
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");

    @Override
    public void start(Stage stage) {
        // 1) Initialize data
        initPossibleNews();
        initStockGroups();
        stocks = generateStocks();

        // 2) Build the root layout with a nice background
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #1d2671, #c33764);" + 
            "-fx-font-family: 'Segoe UI', sans-serif;"
        );

        // 3) Create the top bar
        HBox topBar = buildTopBar();
        root.setTop(topBar);

        // 4) Create the center area: a horizontal box with
        //    [ stock table | stock detail pane | trade/log panel ]
        stockTable = buildStockTable();
        stockDetailPane = buildStockDetailPane();
        VBox tradeAndLogPanel = buildRightPanel();

        HBox centerBox = new HBox(15, stockTable, stockDetailPane, tradeAndLogPanel);
        centerBox.setAlignment(Pos.CENTER_LEFT);
        centerBox.setPadding(new Insets(15));
        root.setCenter(centerBox);

        // 5) Create the bottom area: a horizontal box with
        //    [ news feed | portfolio summary & net worth chart ]
        newsArea = buildNewsArea();
        VBox portfolioBox = buildPortfolioBox();

        HBox bottomBox = new HBox(15, newsArea, portfolioBox);
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        bottomBox.setPadding(new Insets(15));
        root.setBottom(bottomBox);

        // 6) Create the scene and stage
        Scene scene = new Scene(root, 1280, 800);
        stage.setTitle("Intergalactic Stock Market - Year 2100");
        stage.setScene(scene);
        stage.show();

        // 7) Set up selection listener for the stock table
        stockTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            updateStockDetailPane(newSel);
        });

        // 8) Start the timers (game, stock price updates, news, etc.)
        startGameTimer();
        startStockPriceTimer();
        startNewsTimer();
        generateMonthlyNewsTriggers();
    }

    // ------------------------------------------------------------------------
    // Top Bar
    // ------------------------------------------------------------------------
    private HBox buildTopBar() {
        timeLeftLabel = new Label("Time Left: 12:00");
        monthLabel = new Label("Month: January");
        secondsInMonthLabel = new Label("Sec in Month: 60");

        // Style for top bar labels
        String labelStyle = "-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: bold;";

        timeLeftLabel.setStyle(labelStyle);
        monthLabel.setStyle(labelStyle);
        secondsInMonthLabel.setStyle(labelStyle);

        HBox topBar = new HBox(40, timeLeftLabel, monthLabel, secondsInMonthLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Slight translucent overlay
        topBar.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.15);" +
            "-fx-background-radius: 8;"
        );
        return topBar;
    }

    // ------------------------------------------------------------------------
    // Stock Table
    // ------------------------------------------------------------------------
    private TableView<Stock> buildStockTable() {
        TableView<Stock> table = new TableView<>();

        TableColumn<Stock, String> nameCol = new TableColumn<>("Investment");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<Stock, Double> priceCol = new TableColumn<>("Price/Share");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(100);

        TableColumn<Stock, String> moveCol = new TableColumn<>("Movement");
        moveCol.setCellValueFactory(new PropertyValueFactory<>("movementIndicator"));
        moveCol.setPrefWidth(90);

        TableColumn<Stock, String> percentCol = new TableColumn<>("% Change");
        percentCol.setPrefWidth(90);
        percentCol.setCellValueFactory(cellData -> cellData.getValue().percentChangeProperty());
        percentCol.setCellFactory(column -> new TableCell<Stock, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTextFill(Color.BLACK);
                } else {
                    setText(item);
                    try {
                        double val = Double.parseDouble(item.replace("%", ""));
                        if (val > 0) {
                            setTextFill(Color.GREEN);
                        } else if (val < 0) {
                            setTextFill(Color.RED);
                        } else {
                            setTextFill(Color.BLACK);
                        }
                    } catch (NumberFormatException e) {
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });

        table.getColumns().addAll(nameCol, priceCol, moveCol, percentCol);
        table.setItems(stocks);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-table-cell-border-color: #eee;"
        );

        return table;
    }

    // ------------------------------------------------------------------------
    // Stock Detail Pane
    // ------------------------------------------------------------------------
    private VBox buildStockDetailPane() {
        stockDescriptionLabel = new Label("Select a stock to see details.");
        stockDescriptionLabel.setWrapText(true);
        stockDescriptionLabel.setPrefWidth(300);
        stockDescriptionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (sec)");
        yAxis.setLabel("Price ($)");

        stockChart = new LineChart<>(xAxis, yAxis);
        stockChart.setPrefSize(350, 250);
        stockChart.setCreateSymbols(false);
        stockChart.setAnimated(false);
        stockChart.setStyle("-fx-background-color: #fafafa;");

        VBox detailPane = new VBox(10, stockDescriptionLabel, stockChart);
        detailPane.setPadding(new Insets(10));
        detailPane.setAlignment(Pos.TOP_LEFT);
        detailPane.setPrefWidth(350);

        detailPane.setStyle(
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-padding: 10;"
        );
        return detailPane;
    }

    private void updateStockDetailPane(Stock stock) {
        if (stock == null) {
            stockDescriptionLabel.setText("Select a stock to see details.");
            stockChart.getData().clear();
            return;
        }
        stockDescriptionLabel.setText(
            stock.getName() + ":\n" + stock.getDescription()
        );
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(stock.getName());
        series.getData().addAll(stock.getPriceHistory());
        stockChart.getData().clear();
        stockChart.getData().add(series);
    }

    // ------------------------------------------------------------------------
    // Right Panel (Trade Controls + Market Log)
    // ------------------------------------------------------------------------
    private VBox buildRightPanel() {
        // Trade controls
        buySellSharesField = new TextField();
        buySellSharesField.setPromptText("Shares");
        buySellSharesField.setPrefWidth(60);

        Button buyButton = new Button("Buy");
        buyButton.setOnAction(e -> buyShares(false));
        Button sellButton = new Button("Sell");
        sellButton.setOnAction(e -> sellShares(false));
        Button buyMaxButton = new Button("Buy Max");
        buyMaxButton.setOnAction(e -> buyShares(true));
        Button sellAllButton = new Button("Sell All");
        sellAllButton.setOnAction(e -> sellShares(true));

        HBox tradeBox1 = new HBox(5, new Label("Shares:"), buySellSharesField);
        tradeBox1.setAlignment(Pos.CENTER_LEFT);

        HBox tradeBox2 = new HBox(5, buyButton, sellButton, buyMaxButton, sellAllButton);
        tradeBox2.setAlignment(Pos.CENTER_LEFT);

        VBox tradeControls = new VBox(8,
            new Label("Trade Controls:"),
            tradeBox1,
            tradeBox2
        );
        tradeControls.setPadding(new Insets(10));
        tradeControls.setStyle(
            "-fx-background-color: #f7f7f7;" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-padding: 10;" +
            "-fx-font-size: 14px;"
        );

        // Market log
        marketLogArea = new TextArea();
        marketLogArea.setEditable(false);
        marketLogArea.setWrapText(true);
        marketLogArea.setPrefHeight(350);
        marketLogArea.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-font-size: 13px;"
        );

        VBox rightPanel = new VBox(10,
            tradeControls,
            new Label("Market Log:"),
            marketLogArea
        );
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle(
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-padding: 10;"
        );

        return rightPanel;
    }

    // ------------------------------------------------------------------------
    // Bottom Left: News Feed
    // ------------------------------------------------------------------------
    private TextArea buildNewsArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefWidth(500);
        area.setPrefHeight(250);
        area.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-font-size: 13px;"
        );
        return area;
    }

    // ------------------------------------------------------------------------
    // Bottom Right: Portfolio Box (Cash, Invested, NetWorth, Chart)
    // ------------------------------------------------------------------------
    private VBox buildPortfolioBox() {
        cashLabel = new Label("Cash: $" + MONEY_FMT.format(playerMoney));
        investedLabel = new Label("Invested: $0.00");
        netWorthLabel = new Label("Net Worth: $" + MONEY_FMT.format(playerMoney));

        cashLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        investedLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        netWorthLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (sec)");
        yAxis.setLabel("Net Worth ($)");

        netWorthChart = new LineChart<>(xAxis, yAxis);
        netWorthChart.setPrefSize(450, 250);
        netWorthChart.setCreateSymbols(false);
        netWorthChart.setAnimated(false);
        netWorthSeries.setName("Net Worth");
        netWorthChart.getData().add(netWorthSeries);
        netWorthChart.setStyle("-fx-background-color: #fafafa;");

        VBox portfolioBox = new VBox(10,
            new Label("Portfolio:"),
            cashLabel, investedLabel, netWorthLabel,
            netWorthChart
        );
        portfolioBox.setPadding(new Insets(10));
        portfolioBox.setStyle(
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-border-color: #ccc;" +
            "-fx-border-radius: 5;" +
            "-fx-padding: 10;"
        );
        return portfolioBox;
    }

    // ------------------------------------------------------------------------
    // Timers & Month Logic
    // ------------------------------------------------------------------------
    private void startGameTimer() {
        Timeline gameTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            totalTimeLeft--;
            secondsLeftInMonth--;

            int secondOfMonth = SECONDS_PER_MONTH - secondsLeftInMonth;
            if (monthlyNewsTriggers.contains(secondOfMonth)) {
                generateNewsEvent();
                monthlyNewsCount++;
            }

            if (secondsLeftInMonth <= 0) {
                currentMonthIndex++;
                if (currentMonthIndex >= TOTAL_MONTHS) {
                    endGame();
                    return;
                }
                secondsLeftInMonth = SECONDS_PER_MONTH;
                monthLabel.setText("Month: " + monthName(currentMonthIndex));
                generateMonthlyNewsTriggers();
            }

            updateTimeLabels();
            if (totalTimeLeft <= 0) {
                endGame();
            }
            updateNetWorthChart();
        }));
        gameTimeline.setCycleCount(Timeline.INDEFINITE);
        gameTimeline.play();
    }

    private void startStockPriceTimer() {
        Timeline priceTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            for (Stock s : stocks) {
                s.updatePrice();
            }
            stockTable.refresh();
        }));
        priceTimeline.setCycleCount(Timeline.INDEFINITE);
        priceTimeline.play();
    }

    private void startNewsTimer() {
        // Optional: a background "random" news every 5s with some probability
        Timeline newsTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            if (random.nextDouble() < 0.25) {
                generateNewsEvent();
            }
        }));
        newsTimeline.setCycleCount(Timeline.INDEFINITE);
        newsTimeline.play();
    }

    private void generateMonthlyNewsTriggers() {
        monthlyNewsTriggers.clear();
        monthlyNewsCount = 0;
        int eventsThisMonth = random.nextInt(MAX_NEWS_PER_MONTH - MIN_NEWS_PER_MONTH + 1) + MIN_NEWS_PER_MONTH;
        while (monthlyNewsTriggers.size() < eventsThisMonth) {
            int randomSec = 1 + random.nextInt(SECONDS_PER_MONTH - 1);
            monthlyNewsTriggers.add(randomSec);
        }
    }

    // ------------------------------------------------------------------------
    // Trading Logic
    // ------------------------------------------------------------------------
    private void buyShares(boolean buyMax) {
        Stock selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No stock selected to buy.");
            return;
        }
        int sharesToBuy;
        if (buyMax) {
            sharesToBuy = (int) (playerMoney / selected.getPrice());
            if (sharesToBuy <= 0) {
                logToMarket("Not enough cash to buy even 1 share of " + selected.getName());
                return;
            }
        } else {
            try {
                sharesToBuy = Integer.parseInt(buySellSharesField.getText().trim());
            } catch (NumberFormatException ex) {
                logToMarket("Invalid share amount.");
                return;
            }
        }
        double cost = sharesToBuy * selected.getPrice();
        if (cost > playerMoney) {
            logToMarket("Insufficient cash to buy " + sharesToBuy + " shares of " + selected.getName());
            return;
        }
        playerMoney -= cost;
        int oldShares = portfolio.getOrDefault(selected.getName(), 0);
        portfolio.put(selected.getName(), oldShares + sharesToBuy);
        showBuyAnimation(cost);
        logToMarket("Bought " + sharesToBuy + " shares of " + selected.getName() + " @ $" + MONEY_FMT.format(selected.getPrice()));
        updateMoneyLabels();
    }

    private void sellShares(boolean sellAll) {
        Stock selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No stock selected to sell.");
            return;
        }
        int owned = portfolio.getOrDefault(selected.getName(), 0);
        if (owned <= 0) {
            logToMarket("You own 0 shares of " + selected.getName());
            return;
        }
        int sharesToSell;
        if (sellAll) {
            sharesToSell = owned;
        } else {
            try {
                sharesToSell = Integer.parseInt(buySellSharesField.getText().trim());
            } catch (NumberFormatException ex) {
                logToMarket("Invalid share amount.");
                return;
            }
            if (sharesToSell > owned) {
                logToMarket("You only own " + owned + " shares of " + selected.getName());
                return;
            }
        }
        double revenue = sharesToSell * selected.getPrice();
        playerMoney += revenue;
        portfolio.put(selected.getName(), owned - sharesToSell);
        showSellAnimation(revenue);
        logToMarket("Sold " + sharesToSell + " shares of " + selected.getName() + " @ $" + MONEY_FMT.format(selected.getPrice()));
        updateMoneyLabels();
    }

    // ------------------------------------------------------------------------
    // News & Price Impact
    // ------------------------------------------------------------------------
    private void generateNewsEvent() {
        if (currentMonthIndex >= TOTAL_MONTHS) return;
        String headline = possibleNews.get(random.nextInt(possibleNews.size()));
        Stock mainStock = stocks.get(random.nextInt(stocks.size()));
        boolean useGroup = random.nextBoolean();
        List<Stock> impacted = new ArrayList<>();
        if (useGroup && stockGroups.containsKey(mainStock.getName())) {
            impacted.addAll(findStocksByNames(stockGroups.get(mainStock.getName())));
        } else {
            impacted.add(mainStock);
        }
        int factor = random.nextInt(5) + 1;
        boolean isPositive = random.nextBoolean();
        for (Stock s : impacted) {
            double impactPercent = factor * NEWS_IMPACT_MULTIPLIER;
            double totalImpact = s.getPrice() * impactPercent;
            totalImpact = isPositive ? Math.abs(totalImpact) : -Math.abs(totalImpact);
            applyNewsImpactOverTime(s, totalImpact, NEWS_IMPACT_DURATION, NEWS_IMPACT_DELAY);
        }
        StringBuilder sb = new StringBuilder();
        for (Stock s : impacted) {
            sb.append(s.getName()).append(", ");
        }
        String affectedNames = sb.toString().replaceAll(", $", "");
        newsArea.appendText("[" + monthName(currentMonthIndex) + "] " + headline + " (Affects " + affectedNames + ")\n");
    }

    private void applyNewsImpactOverTime(Stock stock, double totalImpact, int durationSeconds, int delaySeconds) {
        double step = totalImpact / durationSeconds;
        Timeline timeline = new Timeline();
        for (int i = 1; i <= durationSeconds; i++) {
            KeyFrame kf = new KeyFrame(Duration.seconds(delaySeconds + i), e -> {
                double oldPrice = stock.getPrice();
                double newPrice = oldPrice + step;
                if (newPrice < 1) newPrice = 1;
                stock.setPrice(newPrice);
                stock.updateMovementIndicator(oldPrice, newPrice);
                stock.getPriceHistory().add(new XYChart.Data<>(stock.nextHistoryCounter(), newPrice));
                stockTable.refresh();
            });
            timeline.getKeyFrames().add(kf);
        }
        timeline.play();
    }

    // ------------------------------------------------------------------------
    // End Game
    // ------------------------------------------------------------------------
    private void endGame() {
        logToMarket("\nAll 12 months of the year 2100 have passed!");
        double finalNetWorth = calculateNetWorth();
        double profit = finalNetWorth - startingMoney;
        logToMarket("Final Net Worth: $" + MONEY_FMT.format(finalNetWorth)
                + " (P/L: $" + MONEY_FMT.format(profit) + ")");
        buySellSharesField.setDisable(true);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private void updateTimeLabels() {
        int minutes = totalTimeLeft / 60;
        int seconds = totalTimeLeft % 60;
        timeLeftLabel.setText(String.format("Time Left: %02d:%02d", minutes, seconds));
        secondsInMonthLabel.setText("Sec in Month: " + secondsLeftInMonth);
    }

    private String monthName(int index) {
        String[] months = {
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        };
        return (index >= 0 && index < months.length) ? months[index] : "Unknown";
    }

    private void updateNetWorthChart() {
        double netWorth = calculateNetWorth();
        chartTimeCounter++;
        netWorthSeries.getData().add(new XYChart.Data<>(chartTimeCounter, netWorth));
        updateMoneyLabels();
    }

    private double calculateNetWorth() {
        double total = playerMoney;
        for (Stock s : stocks) {
            int owned = portfolio.getOrDefault(s.getName(), 0);
            total += owned * s.getPrice();
        }
        return total;
    }

    private void updateMoneyLabels() {
        double netWorth = calculateNetWorth();
        double invested = netWorth - playerMoney;
        cashLabel.setText("Cash: $" + MONEY_FMT.format(playerMoney));
        investedLabel.setText("Invested: $" + MONEY_FMT.format(invested));
        netWorthLabel.setText("Net Worth: $" + MONEY_FMT.format(netWorth));
    }

    private void logToMarket(String msg) {
        marketLogArea.appendText(msg + "\n");
    }

    private void showBuyAnimation(double cost) {
        Text text = new Text("+$" + MONEY_FMT.format(cost));
        text.setFill(Color.LIMEGREEN);
        fadeOutText(text);
    }

    private void showSellAnimation(double revenue) {
        Text text = new Text("-$" + MONEY_FMT.format(revenue));
        text.setFill(Color.ORANGERED);
        fadeOutText(text);
    }

    private void fadeOutText(Text text) {
        FadeTransition ft = new FadeTransition(Duration.seconds(2), text);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.play();
    }

    private List<Stock> findStocksByNames(List<String> names) {
        List<Stock> result = new ArrayList<>();
        for (String nm : names) {
            for (Stock s : stocks) {
                if (s.getName().equals(nm)) {
                    result.add(s);
                    break;
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // Data Initialization
    // ------------------------------------------------------------------------
    private void initPossibleNews() {
        possibleNews.add("Major breakthrough in quantum thrusters!");
        possibleNews.add("Terraform Inc unveils new gene-edited seeds for Mars.");
        possibleNews.add("Space Tourism faces safety lawsuit after rocket mishap.");
        possibleNews.add("Asteroid Mining Co finds massive platinum deposit.");
        possibleNews.add("Lunar Energy Corp sees record demand for Helium-3.");
        possibleNews.add("Orbital Transport invests in next-gen propulsion.");
        possibleNews.add("Zero-G Manufacturing perfects 3D printing for space habitats.");
        possibleNews.add("Galactic Commodities surges on rare metal shortage.");
        possibleNews.add("Deep Space Tech announces AI-based navigation system.");
        possibleNews.add("Quantum Computing Labs reveals advanced entangled processor.");
    }

    private void initStockGroups() {
        stockGroups.put("Asteroid Mining Co", Arrays.asList("Lunar Energy Corp", "Galactic Commodities"));
        stockGroups.put("Terraform Inc", Arrays.asList("Mars Real Estate", "Space Tourism"));
        stockGroups.put("Deep Space Tech", Arrays.asList("Orbital Transport", "Quantum Computing Labs"));
    }

    private ObservableList<Stock> generateStocks() {
        List<Stock> list = new ArrayList<>();
        list.add(new Stock("Asteroid Mining Co", randomPrice(100, 300), "Provides mining services on asteroids."));
        list.add(new Stock("Mars Real Estate", randomPrice(150, 400), "Develops real estate on Mars."));
        list.add(new Stock("Space Tourism", randomPrice(80, 200), "Offers leisure trips to space."));
        list.add(new Stock("Galactic Commodities", randomPrice(90, 250), "Trades rare commodities across galaxies."));
        list.add(new Stock("Lunar Energy Corp", randomPrice(60, 150), "Generates energy using lunar resources."));
        list.add(new Stock("Orbital Transport", randomPrice(120, 350), "Provides transportation in orbit."));
        list.add(new Stock("Terraform Inc", randomPrice(200, 500), "Works on terraforming planets."));
        list.add(new Stock("Deep Space Tech", randomPrice(70, 220), "Develops advanced deep-space technology."));
        list.add(new Stock("Zero-G Manufacturing", randomPrice(100, 250), "Manufactures goods in zero gravity."));
        list.add(new Stock("Quantum Computing Labs", randomPrice(180, 400), "Pioneers quantum computing for space apps."));
        return FXCollections.observableArrayList(list);
    }

    private double randomPrice(int min, int max) {
        return min + (max - min) * random.nextDouble();
    }

    // ------------------------------------------------------------------------
    // Inner Class: Stock
    // ------------------------------------------------------------------------
    public class Stock {
        private String name;
        private double price;
        private double initialPrice;
        private String movementIndicator;
        private String description;
        private ObservableList<XYChart.Data<Number, Number>> priceHistory = FXCollections.observableArrayList();
        private int historyCounter = 0;

        public Stock(String name, double price, String description) {
            this.name = name;
            this.price = price;
            this.initialPrice = price;
            this.description = description;
            this.movementIndicator = "";
            priceHistory.add(new XYChart.Data<>(historyCounter++, price));
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getMovementIndicator() { return movementIndicator; }
        public String getDescription() { return description; }
        public ObservableList<XYChart.Data<Number, Number>> getPriceHistory() { return priceHistory; }

        public void setPrice(double newPrice) {
            this.price = newPrice;
        }

        public void updatePrice() {
            double move = random.nextDouble() * (PRICE_MOVE_UP + PRICE_MOVE_DOWN) - PRICE_MOVE_DOWN;
            double oldPrice = price;
            price += move;
            if (price < 1) price = 1;
            updateMovementIndicator(oldPrice, price);
            priceHistory.add(new XYChart.Data<>(historyCounter++, price));
            if (priceHistory.size() > 200) {
                priceHistory.remove(0);
            }
        }

        public void updateMovementIndicator(double oldP, double newP) {
            double diff = newP - oldP;
            if (diff > 0) {
                movementIndicator = String.format("+%.2f", diff);
            } else if (diff < 0) {
                movementIndicator = String.format("%.2f", diff);
            } else {
                movementIndicator = "0.00";
            }
        }

        public SimpleStringProperty percentChangeProperty() {
            double pctChange = ((price - initialPrice) / initialPrice) * 100;
            return new SimpleStringProperty(String.format("%.2f%%", pctChange));
        }

        public int nextHistoryCounter() {
            return historyCounter++;
        }
    }
}
