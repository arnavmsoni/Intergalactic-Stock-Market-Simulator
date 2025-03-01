package com.example;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
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

    // -----------------------------
    // Simulation Settings
    // -----------------------------
    private static final int TOTAL_MONTHS = 12;       // Jan - Dec
    private static final int SECONDS_PER_MONTH = 30;  // Each month is 30s => 12 * 30 = 6 min
    private static final int TOTAL_TIME = TOTAL_MONTHS * SECONDS_PER_MONTH; // 360s (6:00)

    // -----------------------------
    // Game State
    // -----------------------------
    private int currentMonthIndex = 0; // 0 = January, 1 = Feb, ...
    private int secondsLeftInMonth = SECONDS_PER_MONTH;
    private int totalTimeLeft = TOTAL_TIME; // 360 at start
    private double startingMoney = 10000.0;
    private double playerMoney = startingMoney;
    private Map<String, Integer> portfolio = new HashMap<>();   // stockName -> shares
    private Map<String, Double> costBasis = new HashMap<>();    // track total money spent on each stock for P/L
    private double lastNetWorth = startingMoney;                // for chart
    private Random random = new Random();

    // Keep track of net worth over time (for the chart)
    private XYChart.Series<Number, Number> netWorthSeries = new XYChart.Series<>();
    private int chartTimeCounter = 0; // x-axis time steps

    // -----------------------------
    // News System
    // -----------------------------
    private TextArea newsArea;
    private Timeline newsTimeline;
    private List<String> possibleNews = new ArrayList<>();
    private Map<String, List<String>> stockGroups = new HashMap<>(); // for complementary/substitute logic

    // -----------------------------
    // UI Elements
    // -----------------------------
    private Label timeLeftLabel;
    private Label monthLabel;
    private Label secondsInMonthLabel;
    private Label totalMoneyLabel;
    private Label profitLossLabel;
    private TableView<Stock> stockTable;
    private TextField buySellSharesField;
    private TextArea marketLogArea;
    private LineChart<Number, Number> netWorthChart;
    private TableView<PortfolioItem> holdingsTable;

    // -----------------------------
    // Stock List
    // -----------------------------
    private ObservableList<Stock> stocks;

    // Format for money
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");

    @Override
    public void start(Stage stage) {
        // Initialize data
        initStockGroups();
        initPossibleNews();
        stocks = generateStocks();

        // Build UI
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top bar: Time left, Month, Seconds in month
        HBox topBar = buildTopBar();
        root.setTop(topBar);

        // Center: Stock Table
        stockTable = buildStockTable();

        // Right panel: Buy/Sell & Market Log
        VBox rightPanel = buildRightPanel();

        // Bottom left: News feed
        newsArea = new TextArea();
        newsArea.setEditable(false);
        newsArea.setPrefHeight(120);
        newsArea.setWrapText(true);
        VBox newsBox = new VBox(new Label("News Feed:"), newsArea);
        newsBox.setSpacing(5);

        // Bottom right: Portfolio (Total money, P/L, chart, holdings)
        VBox portfolioBox = buildPortfolioBox();

        // Layout center + right
        HBox centerBox = new HBox(10, stockTable, rightPanel);
        centerBox.setPrefHeight(300);
        root.setCenter(centerBox);

        // Layout bottom: news on left, portfolio on right
        HBox bottomBox = new HBox(10, newsBox, portfolioBox);
        bottomBox.setPadding(new Insets(10));
        bottomBox.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(bottomBox);

        // Scene
        Scene scene = new Scene(root, 1200, 700);
        stage.setScene(scene);
        stage.setTitle("Intergalactic Stock Market - Year 2100");
        stage.show();

        // Start the timers
        startGameTimer();
        startStockPriceTimer();
        startNewsTimer();
    }

    // --------------------------------
    // Build UI Components
    // --------------------------------

    private HBox buildTopBar() {
        timeLeftLabel = new Label("Time Left: 06:00");
        monthLabel = new Label("Month: January");
        secondsInMonthLabel = new Label("Sec in Month: 30");

        HBox topBar = new HBox(20, timeLeftLabel, monthLabel, secondsInMonthLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        return topBar;
    }

    private TableView<Stock> buildStockTable() {
        TableView<Stock> table = new TableView<>();
        table.setPrefWidth(400);

        TableColumn<Stock, String> nameCol = new TableColumn<>("Investment");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<Stock, Double> priceCol = new TableColumn<>("Price/Share");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setPrefWidth(110);

        TableColumn<Stock, String> moveCol = new TableColumn<>("Movement");
        moveCol.setCellValueFactory(new PropertyValueFactory<>("movementIndicator"));
        moveCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, priceCol, moveCol);
        table.setItems(stocks);

        return table;
    }

    private VBox buildRightPanel() {
        // Buy/Sell controls
        buySellSharesField = new TextField();
        buySellSharesField.setPromptText("Shares");
        buySellSharesField.setPrefWidth(80);

        Button buyButton = new Button("Buy");
        buyButton.setOnAction(e -> buyShares(false));

        Button sellButton = new Button("Sell");
        sellButton.setOnAction(e -> sellShares(false));

        // For quick full or half sells, let's do a small HBox
        Button sellAllButton = new Button("Sell All");
        sellAllButton.setOnAction(e -> sellShares(true));

        Button buyMaxButton = new Button("Buy Max");
        buyMaxButton.setOnAction(e -> buyShares(true));

        HBox quickSellBox = new HBox(5, sellAllButton, buyMaxButton);
        quickSellBox.setAlignment(Pos.CENTER_LEFT);

        VBox buySellBox = new VBox(5,
                new Label("Trade Controls:"),
                new HBox(5, new Label("Shares:"), buySellSharesField),
                new HBox(5, buyButton, sellButton),
                quickSellBox
        );
        buySellBox.setPadding(new Insets(5));
        buySellBox.setAlignment(Pos.CENTER_LEFT);
        buySellBox.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 5;");

        // Market log
        marketLogArea = new TextArea();
        marketLogArea.setEditable(false);
        marketLogArea.setWrapText(true);
        marketLogArea.setPrefHeight(300);

        VBox box = new VBox(10, buySellBox, new Label("Market Log:"), marketLogArea);
        box.setPrefWidth(300);
        return box;
    }

    private VBox buildPortfolioBox() {
        totalMoneyLabel = new Label("Total: $" + MONEY_FMT.format(playerMoney));
        profitLossLabel = new Label("+$0.00 (0.00%)"); // Will update color for up/down
        profitLossLabel.setTextFill(Color.GREEN);

        // Chart for net worth
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        yAxis.setLabel("Net Worth ($)");
        netWorthChart = new LineChart<>(xAxis, yAxis);
        netWorthChart.setPrefSize(400, 200);
        netWorthChart.setAnimated(false);
        netWorthChart.setCreateSymbols(false);
        netWorthSeries.setName("Net Worth");
        netWorthChart.getData().add(netWorthSeries);

        // Holdings table
        holdingsTable = new TableView<>();
        holdingsTable.setPrefHeight(150);

        TableColumn<PortfolioItem, String> hNameCol = new TableColumn<>("Holding");
        hNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        hNameCol.setPrefWidth(100);

        TableColumn<PortfolioItem, Integer> hSharesCol = new TableColumn<>("Shares");
        hSharesCol.setCellValueFactory(new PropertyValueFactory<>("shares"));
        hSharesCol.setPrefWidth(60);

        TableColumn<PortfolioItem, String> hValueCol = new TableColumn<>("Value");
        hValueCol.setCellValueFactory(new PropertyValueFactory<>("valueString"));
        hValueCol.setPrefWidth(80);

        holdingsTable.getColumns().addAll(hNameCol, hSharesCol, hValueCol);

        Button sellHalfBtn = new Button("Sell Half");
        sellHalfBtn.setOnAction(e -> sellHalfSelectedHolding());
        Button sellAllBtn = new Button("Sell All");
        sellAllBtn.setOnAction(e -> sellAllSelectedHolding());
        Button buyMoreBtn = new Button("Buy More");
        buyMoreBtn.setOnAction(e -> buyMoreSelectedHolding());

        HBox holdingsControls = new HBox(5, sellHalfBtn, sellAllBtn, buyMoreBtn);

        VBox portfolioBox = new VBox(5,
                new Label("Portfolio:"),
                totalMoneyLabel,
                profitLossLabel,
                netWorthChart,
                new Label("Your Holdings:"),
                holdingsTable,
                holdingsControls
        );
        portfolioBox.setPadding(new Insets(5));
        portfolioBox.setAlignment(Pos.TOP_LEFT);
        portfolioBox.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 5;");

        return portfolioBox;
    }

    // --------------------------------
    // Timers & Simulation
    // --------------------------------

    private void startGameTimer() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            totalTimeLeft--;
            secondsLeftInMonth--;

            // Update month if needed
            if (secondsLeftInMonth <= 0) {
                // Move to next month
                currentMonthIndex++;
                if (currentMonthIndex >= TOTAL_MONTHS) {
                    // Game ends
                    endGame();
                    return;
                }
                secondsLeftInMonth = SECONDS_PER_MONTH;
                monthLabel.setText("Month: " + monthName(currentMonthIndex));
                // Possibly spawn news events for new month
            }

            // Update labels
            updateTimeLabels();

            if (totalTimeLeft <= 0) {
                endGame();
            }

            // Update net worth chart
            updateNetWorthChart();

            // Update portfolio table
            refreshHoldingsTable();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void startStockPriceTimer() {
        // Price updates more frequently (e.g., every second)
        Timeline stockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            for (Stock s : stocks) {
                s.updatePrice(); // random movement +/- 5 or -8
            }
            stockTable.refresh();
        }));
        stockTimeline.setCycleCount(Timeline.INDEFINITE);
        stockTimeline.play();
    }

    private void startNewsTimer() {
        // We want 2–3 news events per month. Each month is 30s => we can randomly pick times within the month
        newsTimeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            // Possibly trigger a news event. We can do a small random chance every 5s
            if (random.nextDouble() < 0.25) {
                generateNewsEvent();
            }
        }));
        newsTimeline.setCycleCount(Timeline.INDEFINITE);
        newsTimeline.play();
    }

    // --------------------------------
    // Trading Logic
    // --------------------------------

    private void buyShares(boolean buyMax) {
        Stock selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No stock selected to buy.");
            return;
        }
        int sharesToBuy;
        if (buyMax) {
            // Buy as many shares as possible with current money
            sharesToBuy = (int) (playerMoney / selected.getPrice());
            if (sharesToBuy <= 0) {
                logToMarket("Not enough funds to buy even 1 share of " + selected.getName());
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
            logToMarket("Insufficient funds to buy " + sharesToBuy + " shares of " + selected.getName());
            return;
        }
        playerMoney -= cost;
        portfolio.put(selected.getName(), portfolio.getOrDefault(selected.getName(), 0) + sharesToBuy);
        costBasis.put(selected.getName(), costBasis.getOrDefault(selected.getName(), 0.0) + cost);

        logToMarket("Bought " + sharesToBuy + " shares of " + selected.getName() + " @ $" + MONEY_FMT.format(selected.getPrice()) + " each.");

        showBuyAnimation(selected.getPrice());
        updateMoneyLabels();
        refreshHoldingsTable();
    }

    private void sellShares(boolean sellAll) {
        Stock selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No stock selected to sell.");
            return;
        }
        int owned = portfolio.getOrDefault(selected.getName(), 0);
        if (owned == 0) {
            logToMarket("You own 0 shares of " + selected.getName() + ".");
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

        logToMarket("Sold " + sharesToSell + " shares of " + selected.getName() + " @ $" + MONEY_FMT.format(selected.getPrice()) + " each.");
        showSellAnimation(selected.getPrice());
        updateMoneyLabels();
        refreshHoldingsTable();
    }

    private void sellHalfSelectedHolding() {
        PortfolioItem selected = holdingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No holding selected to sell half.");
            return;
        }
        String stockName = selected.getName();
        Stock stockObj = findStockByName(stockName);
        if (stockObj == null) return;

        int owned = selected.getShares();
        int half = owned / 2;
        if (half == 0) {
            logToMarket("You own fewer than 2 shares of " + stockName + "; can't sell half.");
            return;
        }
        double revenue = half * stockObj.getPrice();
        playerMoney += revenue;
        portfolio.put(stockName, owned - half);

        logToMarket("Sold half (" + half + ") of " + stockName + " @ $" + MONEY_FMT.format(stockObj.getPrice()));
        showSellAnimation(stockObj.getPrice());
        updateMoneyLabels();
        refreshHoldingsTable();
    }

    private void sellAllSelectedHolding() {
        PortfolioItem selected = holdingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No holding selected to sell all.");
            return;
        }
        String stockName = selected.getName();
        Stock stockObj = findStockByName(stockName);
        if (stockObj == null) return;

        int owned = selected.getShares();
        double revenue = owned * stockObj.getPrice();
        playerMoney += revenue;
        portfolio.put(stockName, 0);

        logToMarket("Sold ALL (" + owned + ") of " + stockName + " @ $" + MONEY_FMT.format(stockObj.getPrice()));
        showSellAnimation(stockObj.getPrice());
        updateMoneyLabels();
        refreshHoldingsTable();
    }

    private void buyMoreSelectedHolding() {
        PortfolioItem selected = holdingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logToMarket("No holding selected to buy more.");
            return;
        }
        Stock stockObj = findStockByName(selected.getName());
        if (stockObj == null) return;

        // Example: buy 10 shares
        int sharesToBuy = 10;
        double cost = sharesToBuy * stockObj.getPrice();
        if (cost > playerMoney) {
            logToMarket("Not enough funds to buy 10 more shares of " + stockObj.getName());
            return;
        }
        playerMoney -= cost;
        portfolio.put(stockObj.getName(), portfolio.getOrDefault(stockObj.getName(), 0) + sharesToBuy);
        costBasis.put(stockObj.getName(), costBasis.getOrDefault(stockObj.getName(), 0.0) + cost);

        logToMarket("Bought 10 more shares of " + stockObj.getName() + " @ $" + MONEY_FMT.format(stockObj.getPrice()));
        showBuyAnimation(stockObj.getPrice());
        updateMoneyLabels();
        refreshHoldingsTable();
    }

    // --------------------------------
    // News & Price Impacts
    // --------------------------------

    private void generateNewsEvent() {
        if (currentMonthIndex >= TOTAL_MONTHS) return; // game ended

        // Pick a random news item
        String news = possibleNews.get(random.nextInt(possibleNews.size()));
        // Possibly affect a random stock or group
        Stock stock = stocks.get(random.nextInt(stocks.size()));

        // 50% chance we pick a group (complement/substitute) instead
        boolean useGroup = random.nextBoolean();
        if (useGroup) {
            List<String> group = stockGroups.get(stock.getName());
            if (group != null && !group.isEmpty()) {
                // This entire group is impacted
                for (String sName : group) {
                    Stock sObj = findStockByName(sName);
                    if (sObj != null) {
                        schedulePriceImpact(sObj, news);
                    }
                }
            }
        } else {
            // Just this one stock
            schedulePriceImpact(stock, news);
        }

        // Add to news feed
        newsArea.appendText("[" + monthName(currentMonthIndex) + "] " + news + " (Affects " + stock.getName() + ")\n");
    }

    private void schedulePriceImpact(Stock stock, String news) {
        // Positive or negative?
        double direction = random.nextBoolean() ? 1 : -1;
        // We'll spread the total impact over 5 seconds
        double totalImpact = random.nextDouble() * 10.0 * direction; // up to +/- 10
        double step = totalImpact / 5.0; // small step each second

        // Apply gradually
        for (int i = 1; i <= 5; i++) {
            PauseTransition pt = new PauseTransition(Duration.seconds(i));
            pt.setOnFinished(e -> {
                stock.applyNewsImpact(step);
                stockTable.refresh();
            });
            pt.play();
        }
    }

    // --------------------------------
    // End Game
    // --------------------------------
    private void endGame() {
        logToMarket("\nThe year 2100 is over!");
        double finalNetWorth = calculateNetWorth();
        double profit = finalNetWorth - startingMoney;
        String result = "Final Net Worth: $" + MONEY_FMT.format(finalNetWorth) +
                " (P/L: $" + MONEY_FMT.format(profit) + ")";
        logToMarket(result);

        // Disable further trading
        buySellSharesField.setDisable(true);
    }

    // --------------------------------
    // Helpers
    // --------------------------------

    private void logToMarket(String msg) {
        marketLogArea.appendText(msg + "\n");
    }

    private void showBuyAnimation(double price) {
        // A quick fade-in/out text
        Text text = new Text("+" + MONEY_FMT.format(price));
        text.setFill(Color.GREEN);
        FadeTransition ft = new FadeTransition(Duration.seconds(2), text);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.play();
    }

    private void showSellAnimation(double price) {
        Text text = new Text("-" + MONEY_FMT.format(price));
        text.setFill(Color.RED);
        FadeTransition ft = new FadeTransition(Duration.seconds(2), text);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.play();
    }

    private String monthName(int index) {
        String[] months = {"January","February","March","April","May","June","July","August","September","October","November","December"};
        if (index < 0 || index >= months.length) return "Unknown";
        return months[index];
    }

    private void updateTimeLabels() {
        // totalTimeLeft => minutes:seconds
        int minutes = totalTimeLeft / 60;
        int seconds = totalTimeLeft % 60;
        timeLeftLabel.setText(String.format("Time Left: %02d:%02d", minutes, seconds));

        // secondsLeftInMonth
        secondsInMonthLabel.setText("Sec in Month: " + secondsLeftInMonth);

        // month label is updated upon rollover
    }

    private void updateMoneyLabels() {
        totalMoneyLabel.setText("Total: $" + MONEY_FMT.format(playerMoney));
        double netWorth = calculateNetWorth();
        double profit = netWorth - startingMoney;
        double pct = (profit / startingMoney) * 100.0;
        profitLossLabel.setText(String.format("%+$.2f (%.2f%%)", profit, pct));
        if (profit >= 0) {
            profitLossLabel.setTextFill(Color.GREEN);
        } else {
            profitLossLabel.setTextFill(Color.RED);
        }
    }

    private double calculateNetWorth() {
        double net = playerMoney;
        for (Stock s : stocks) {
            int owned = portfolio.getOrDefault(s.getName(), 0);
            net += owned * s.getPrice();
        }
        return net;
    }

    private void updateNetWorthChart() {
        double netWorth = calculateNetWorth();
        chartTimeCounter++;
        netWorthSeries.getData().add(new XYChart.Data<>(chartTimeCounter, netWorth));
        lastNetWorth = netWorth;
        updateMoneyLabels();
    }

    private void refreshHoldingsTable() {
        ObservableList<PortfolioItem> items = FXCollections.observableArrayList();
        for (Stock s : stocks) {
            int owned = portfolio.getOrDefault(s.getName(), 0);
            if (owned > 0) {
                items.add(new PortfolioItem(s.getName(), owned, s.getPrice()));
            }
        }
        holdingsTable.setItems(items);
        holdingsTable.refresh();
    }

    private Stock findStockByName(String name) {
        for (Stock s : stocks) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    // --------------------------------
    // Data Generation
    // --------------------------------

    private ObservableList<Stock> generateStocks() {
        // 10 futuristic stocks
        // We'll pick random initial prices in a range that “makes sense”
        List<Stock> list = new ArrayList<>();
        list.add(new Stock("Asteroid Mining Co", randomPrice(100, 300)));
        list.add(new Stock("Mars Real Estate", randomPrice(150, 400)));
        list.add(new Stock("Space Tourism Inc", randomPrice(80, 200)));
        list.add(new Stock("Galactic Commodities", randomPrice(90, 250)));
        list.add(new Stock("Lunar Energy Corp", randomPrice(60, 150)));
        list.add(new Stock("Orbital Transport", randomPrice(120, 350)));
        list.add(new Stock("Terraform Inc", randomPrice(200, 500)));
        list.add(new Stock("Deep Space Tech", randomPrice(70, 220)));
        list.add(new Stock("Zero-G Manufacturing", randomPrice(100, 250)));
        list.add(new Stock("Quantum Computing Labs", randomPrice(180, 400)));

        return FXCollections.observableArrayList(list);
    }

    private double randomPrice(int min, int max) {
        return min + (max - min) * random.nextDouble();
    }

    private void initStockGroups() {
        // Example: if "Deep Space Tech" goes down, "Orbital Transport" might also go down, etc.
        // You can group them as you see fit
        stockGroups.put("Asteroid Mining Co", Arrays.asList("Lunar Energy Corp", "Galactic Commodities"));
        stockGroups.put("Mars Real Estate", Arrays.asList("Terraform Inc", "Space Tourism Inc"));
        stockGroups.put("Space Tourism Inc", Arrays.asList("Orbital Transport", "Mars Real Estate"));
        // ... add more as needed
    }

    private void initPossibleNews() {
        possibleNews.add("Major breakthrough in quantum thrusters!");
        possibleNews.add("Terraforming regulations tightened on Mars.");
        possibleNews.add("Asteroid Mining Co discovers massive platinum deposit!");
        possibleNews.add("Space Tourism Inc faces lawsuit after safety incident.");
        possibleNews.add("Galactic Commodities sees spike in rare minerals demand.");
        possibleNews.add("Orbital Transport partners with Deep Space Tech for new engines.");
        possibleNews.add("Terraform Inc announces new gene-edited plants for Mars.");
        possibleNews.add("Lunar Energy Corp experiences rocket fuel shortage.");
        possibleNews.add("Zero-G Manufacturing develops advanced 3D printing for space habitats.");
        possibleNews.add("Quantum Computing Labs reveals next-gen AI processor.");
    }

    // --------------------------------
    // Inner Classes
    // --------------------------------

    public class Stock {
        private String name;
        private double price;
        private double lastPrice;
        private String movementIndicator;

        public Stock(String name, double price) {
            this.name = name;
            this.price = price;
            this.lastPrice = price;
            this.movementIndicator = "";
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        public String getMovementIndicator() {
            return movementIndicator;
        }

        // Called every second to randomly move price between +5 and -8
        public void updatePrice() {
            double move = random.nextDouble() * (5 + 8) - 8; // range: -8 to +5
            double oldPrice = price;
            price += move;
            if (price < 1) {
                price = 1; // floor
            }
            updateMovementIndicator(oldPrice, price);
        }

        public void applyNewsImpact(double step) {
            double oldPrice = price;
            price += step;
            if (price < 1) price = 1;
            updateMovementIndicator(oldPrice, price);
        }

        private void updateMovementIndicator(double oldP, double newP) {
            double diff = newP - oldP;
            if (diff > 0) {
                movementIndicator = String.format("+%.2f", diff);
            } else if (diff < 0) {
                movementIndicator = String.format("%.2f", diff);
            } else {
                movementIndicator = "0.00";
            }
        }
    }

    public class PortfolioItem {
        private String name;
        private int shares;
        private double currentPrice;

        public PortfolioItem(String name, int shares, double currentPrice) {
            this.name = name;
            this.shares = shares;
            this.currentPrice = currentPrice;
        }

        public String getName() { return name; }
        public int getShares() { return shares; }

        public String getValueString() {
            double value = shares * currentPrice;
            return "$" + MONEY_FMT.format(value);
        }
    }
}
