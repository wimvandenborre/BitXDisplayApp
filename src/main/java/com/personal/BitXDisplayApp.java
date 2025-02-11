package com.personal;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class BitXDisplayApp extends Application {
    private static final int NUM_TRACKS = 8;  // Number of meters
    private static final int TEXT_WIDTH = 700;
    private static final int KNOBS_WIDTH = 500;
    private static final int METERS_WIDTH = 312;
    private static final int HEIGHT = 70;

    private static Canvas metersCanvas;
    private static Canvas knobsCanvas;
    private static TextFlow clipTextFlow;
    private static float[] meterValues = new float[NUM_TRACKS + 1]; // ✅ +1 for master

    private ServerSocket serverSocket;
    private boolean running = true;
    private static Color[] trackColors = new Color[NUM_TRACKS];
    private static Color masterTrackColor = Color.GRAY;

    private static Text pageTitle = new Text("No Page");
    private static Text[] knobLabels = new Text[8];
    private static double[] knobValues = new double[8];

    private static boolean isRunning = false; // Ensure only one instance

    @Override
    public void start(Stage primaryStage) {
        if (isRunning) {
            System.out.println("BitXDisplayApp is already running!");
            return;
        }
        isRunning = true;

        primaryStage.setOnCloseRequest(event -> {
            isRunning = false; // Reset flag when window is closed
            stopServer();
        });

        // Set fixed window size
        primaryStage.setResizable(false); // Prevent resizing
       // primaryStage.setMinHeight(HEIGHT);
       // primaryStage.setMaxHeight(HEIGHT);
        primaryStage.setMinWidth(TEXT_WIDTH + KNOBS_WIDTH + METERS_WIDTH);
        primaryStage.setMaxWidth(TEXT_WIDTH + KNOBS_WIDTH + METERS_WIDTH);

        log("Starting JavaFX application...");


        // 🎵 Left: Clip Text Area (900px)
        clipTextFlow = new TextFlow();
        clipTextFlow.setStyle("-fx-background-color: rgb(65, 65, 65); -fx-padding: 0;");
        clipTextFlow.setMinWidth(TEXT_WIDTH);
        clipTextFlow.setPrefWidth(TEXT_WIDTH);

        Text initialText = new Text("No Clip");
        initialText.setFont(Font.font("Arial", 18));
        initialText.setFill(Color.YELLOW);
        clipTextFlow.getChildren().add(initialText);

        StackPane clipPane = new StackPane(clipTextFlow);
        clipPane.setStyle("-fx-background-color: rgb(65, 65, 65); -fx-padding: 0;");


        // 🎛️ Middle: Knobs Area (300px)
        knobsCanvas = new Canvas(KNOBS_WIDTH, HEIGHT);
        StackPane knobsPane = new StackPane(knobsCanvas);
        knobsPane.setStyle("-fx-background-color: rgb(65, 65, 65);");

        // 🎚️ Right: VU Meters (312px)
        metersCanvas = new Canvas(METERS_WIDTH, HEIGHT);
        StackPane metersPane = new StackPane(metersCanvas);
        metersPane.setStyle("-fx-background-color: rgb(65, 65, 65);");

        // Layout
        HBox root = new HBox(clipPane, knobsPane, metersPane);
        Scene scene = new Scene(root, TEXT_WIDTH + KNOBS_WIDTH + METERS_WIDTH, HEIGHT, Color.BLACK);

        primaryStage.setScene(scene);
        primaryStage.setTitle("BitX Display");
        primaryStage.setAlwaysOnTop(true);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> stopServer());

        log("JavaFX application started. Opening server socket...");
        new Thread(this::listenForUpdates).start();

        new Thread(() -> {
            try {
                Thread.sleep(500); // Allow UI to fully initialize

            } catch (InterruptedException e) {
                logError("Error while requesting Bitwig state update: " + e.getMessage());
            }
        }).start();
    }


    private void stopServer() {
        log("Stopping server...");
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            log("Server socket closed.");
        } catch (Exception e) {
            logError("Error closing server socket: " + e.getMessage());
        }
    }


    private void listenForUpdates() {
        try {
            serverSocket = new ServerSocket(9876);
            log("Server socket opened on port 9876.");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line = reader.readLine();
                clientSocket.close();

                if (line != null && !line.isEmpty()) {
                    handleIncomingData(line);
                }
            }
        } catch (Exception e) {
            if (!running) {
                log("Server stopped.");
            } else {
                logError("Error in listenForUpdates: " + e.getMessage());
            }
        }
    }

    private void handleIncomingData(String line) {
        Platform.runLater(() -> {
            if (line.startsWith("CLIP:")) {
                String clipName = line.substring(5);
                log("Updating clip name to: " + clipName);
                Platform.runLater(() -> updateClipText(clipName));
            }
            if (line.startsWith("PAGE:")) {
                String pageName = line.substring(5);
                updateRemoteControlsPageName(pageName);
            } else if (line.startsWith("KNOB_NAME:")) {
                String[] parts = line.split(":");
                int index = Integer.parseInt(parts[1]);
                updateKnobName(index, parts[2]);
            } else if (line.startsWith("KNOB_VALUE:")) {
                String[] parts = line.split(":");
                int index = Integer.parseInt(parts[1]);
                updateKnobValue(index, Double.parseDouble(parts[2]));
            } else if (line.startsWith("VU:")) {
                String[] parts = line.split(":");
                int trackIndex = Integer.parseInt(parts[1]);
                meterValues[trackIndex] = Float.parseFloat(parts[2]) / 127.0f;
                drawMeters();
            }
            else if (line.startsWith("MASTER_VU:")) {
                String[] parts = line.split(":");
                float masterMeterValue = Float.parseFloat(parts[1]) / 127.0f;

                System.out.println("✅ Master Meter Received: " + masterMeterValue);

                meterValues[NUM_TRACKS] = masterMeterValue; // Store master VU in the extra slot

                Platform.runLater(this::drawMeters); // Redraw all meters
            }
            else if (line.startsWith("COLOR:")) {
                String[] parts = line.split(":");
                int trackIndex = Integer.parseInt(parts[1]);
                int red = Integer.parseInt(parts[2]);
                int green = Integer.parseInt(parts[3]);
                int blue = Integer.parseInt(parts[4]);

                Platform.runLater(() -> {
                    trackColors[trackIndex] = Color.rgb(red, green, blue);
                    drawMeters();
                });
            }

            else if (line.startsWith("MASTER_COLOR:")) {
                String[] parts = line.split(":");
                int red = Integer.parseInt(parts[1]);
                int green = Integer.parseInt(parts[2]);
                int blue = Integer.parseInt(parts[3]);

                masterTrackColor = Color.rgb(red, green, blue); // ✅ Store master color
                System.out.println("✅ Master Track Color Received: " + masterTrackColor);
                drawMeters(); // ✅ Redraw meters with the new color
            }
        });
    }


    private void updateClipText(String clipName) {
        clipTextFlow.getChildren().clear();

        // Create text chunks for wrapping
        Text text = new Text(clipName);
        text.setFont(Font.font("Arial", 22));
        text.setFill(Color.YELLOW);

        clipTextFlow.getChildren().add(text);

        clipTextFlow.setPrefWidth(700);
        clipTextFlow.setMaxHeight(65); // Max height to allow two lines
    }

    private static void updateRemoteControlsPageName(String name) {
        pageTitle.setText(name);
        drawKnobs();
    }

    private static void updateKnobName(int index, String name) {
        knobLabels[index] = new Text(name);
        drawKnobs();
    }

    private static void updateKnobValue(int index, double value) {
        knobValues[index] = value;
        drawKnobs();
    }

    private static void drawKnobs() {
        GraphicsContext gc = knobsCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, KNOBS_WIDTH, HEIGHT);

        // Adjust title font and position
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 8)); // Smaller title font
       // gc.fillText(pageTitle.getText(), (KNOBS_WIDTH - gc.getFont().getSize() * pageTitle.getText().length() / 2) / 2, 15); // Title positioned higher

        // Knob dimensions
        double knobDiameter = 28; // Knob size
        double outerDiameter = knobDiameter + 10; // Outer ring slightly larger
        double spacing = 20; // Spacing between knobs
        double startX = (KNOBS_WIDTH - (8 * (outerDiameter + spacing))); // Center the knobs horizontally
        double startY = 10; // Starting y position for the first row

        for (int i = 0; i < 8; i++) {
            double x = startX + (i % 8) * (outerDiameter + spacing);
            double y = startY;

            // Draw outer progress ring
            gc.setStroke(Color.rgb(103, 103,103));
            gc.setLineWidth(3);
            gc.strokeOval(x, y, outerDiameter, outerDiameter);

            // Draw progress in orange, starting from bottom-left (-140°) and moving clockwise
            double valueAngle = knobValues[i] * 270; // Map knob value (0-1) to 0°-320°
            gc.setStroke(Color.rgb(254,125,17));
            gc.setLineWidth(3);
            gc.strokeArc(x, y, outerDiameter, outerDiameter, -135, -valueAngle, ArcType.OPEN); // Negative value to reverse direction

            // Draw knob base
            gc.setFill(Color.rgb(125,125,125)); // Updated for improved contrast
            gc.fillOval(x + 5, y + 5, knobDiameter, knobDiameter);

            // Draw white stripe, synchronized with the orange arc
            double stripeAngle = knobValues[i] * 270 - 225; // Adjusted angle to start from -140°
            double centerX = x + outerDiameter / 2;
            double centerY = y + outerDiameter / 2;
            double lineX = centerX + Math.cos(Math.toRadians(stripeAngle)) * (knobDiameter / 2 - 2);
            double lineY = centerY + Math.sin(Math.toRadians(stripeAngle)) * (knobDiameter / 2 - 2);

            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeLine(centerX, centerY, lineX, lineY);

            // Draw knob label
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", 10)); // Font size remains the same

// Ensure the label is not null and truncate it to 8 characters
            String label = (knobLabels[i] != null) ? knobLabels[i].getText() : "Knob " + (i + 1);
            label = label.length() > 8 ? label.substring(0, 8) : label; // Truncate to 8 characters

// Draw the truncated label
            gc.fillText(label, x + outerDiameter / 2 - label.length() * 3, y + outerDiameter + 14);

        }
    }

    private static double bitwigValueToDb(int bitwigValue) {
        if (bitwigValue <= 0) return -60.0;  // If zero, assume below -60 dB
        if (bitwigValue >= 128) return 6.0;  // Full scale (128) is now +6 dB

        // Convert Bitwig's 0-128 range into dB scale
        return -60.0 + (bitwigValue / 128.0) * 66.0;  // Scale from -60 dB to +6 dB
    }


    private static double dBToNormalized(double dB) {
        double minDb = -60.0;  // Silence threshold
        double maxDb = 6.0;    // Full scale (Bitwig goes to +6 dB)

        if (dB <= minDb) return 0.0; // Below -60 dB → Consider it silence
        if (dB >= maxDb) return 1.0; // At +6 dB → Full height

        return (dB - minDb) / (maxDb - minDb); // Scale from -60 dB to +6 dB in a 0-1 range
    }


    private void drawMeters() {
        log("Drawing meters...");
        GraphicsContext gc = metersCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, metersCanvas.getWidth(), metersCanvas.getHeight());

        double meterWidth = metersCanvas.getWidth() / (NUM_TRACKS + 1); // Add space for master
        double canvasHeight = metersCanvas.getHeight();
        double meterHeight = canvasHeight * 0.8;
        double loopHeight = canvasHeight * 0.2;

        // 🎚️ Draw Track Meters
        for (int i = 0; i < NUM_TRACKS; i++) {
            drawVuMeter(gc, i, meterWidth, meterHeight, loopHeight, meterValues[i], trackColors[i]);
        }

        // 🎚️ Draw Master Meter
        drawVuMeter(gc, NUM_TRACKS, meterWidth, meterHeight, loopHeight, meterValues[NUM_TRACKS], masterTrackColor);

        log("Meters drawn.");
    }

    /**
     * Draws a single VU meter at a given position.
     */
    private void drawVuMeter(GraphicsContext gc, int index, double meterWidth, double meterHeight, double loopHeight, float vuValue, Color trackColor) {
        double x = index * meterWidth;
        double y = meterHeight - (meterHeight * vuValue);

        // 🎨 Draw Background
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(x + 5, 0, meterWidth - 10, meterHeight);

        // 🎨 Draw Track Color Bar
        gc.setFill(trackColor != null ? trackColor : Color.GRAY);
        gc.fillRect(x + 5, meterHeight + 2, meterWidth - 10, loopHeight - 2);

        // 🎨 Apply RMS-Based Color Mapping
        double dB = bitwigValueToDb((int) (vuValue * 128));
        gc.setFill(getMeterColor(dB));
        gc.fillRect(x + 5, y, meterWidth - 10, meterHeight - y);

        // 🔴 Peak Indicator
        if (dB >= 6.0) {
            gc.setFill(Color.RED);
            gc.fillRect(x + 5, 0, meterWidth - 10, 3);
        }

        // ⚡ Draw Stripes (Reference dB Levels)
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        double[] dbValues = {6, 0, -6, -12, -18, -24, -36, -60};
        for (double dbLevel : dbValues) {
            double normLevel = dBToNormalized(dbLevel);
            double lineY = meterHeight - (meterHeight * normLevel);
            gc.strokeLine(x + 5, lineY, x + meterWidth - 5, lineY);
        }
    }



    private static Color getMeterColor(double dB) {
        if (dB >= 0.1) return Color.web("#800000");  // Bordeaux (Clipping)
        if (dB >= 0) return Color.RED;             // 0 dB → Red (Loud)
        if (dB >= -6) return Color.web("#FF4500"); // -6 dB → Orange-Red (Hot)
        if (dB >= -12) return Color.ORANGE;        // -12 dB → Orange (Safe Zone)
        if (dB >= -18) return Color.YELLOW;        // -18 dB → Yellow (Medium)
        if (dB >= -24) return Color.LIMEGREEN;     // -24 dB → Lime Green (Low)
        if (dB >= -36) return Color.web("#008000"); // -36 dB → Dark Green (Very Low)
        return Color.DARKBLUE;                     // Below -60 dB → Dark Blue (Silent)
    }

    private void log(String message) {
        System.out.println("INFO: " + message);
    }

    private void logError(String message) {
        System.err.println("ERROR: " + message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
