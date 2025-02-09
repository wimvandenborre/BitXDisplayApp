package com.personal;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class BitXDisplayApp extends Application {
    private static final int NUM_TRACKS = 8;  // Number of meters (and loops)
    private static Canvas canvas;
    private static TextFlow clipTextFlow; // Text display
    private static float[] meterValues = new float[NUM_TRACKS];
    private static float[] loopProgressValues = new float[NUM_TRACKS]; // Stores loop progress per track

    private ServerSocket serverSocket;
    private boolean running = true;

    private static Color[] trackColors = new Color[NUM_TRACKS];
    private double[] clipLengths = new double[NUM_TRACKS]; // Store clip lengths per track

    @Override
    public void start(Stage primaryStage) {
        log("Starting JavaFX application...");

        // 🎵 Left Side: Clip Name Display
        clipTextFlow = new TextFlow();
        clipTextFlow.setStyle("-fx-background-color: black; -fx-padding: 0; -fx-alignment: top-left;");
        clipTextFlow.setMinWidth(1200);
        clipTextFlow.setPrefWidth(1200);

        // Initialize default text
        Text initialText = new Text("No Clip");
        initialText.setFont(Font.font("Arial", 30));
        initialText.setFill(Color.YELLOW);
        clipTextFlow.getChildren().add(initialText);

        StackPane clipPane = new StackPane(clipTextFlow);
        clipPane.setStyle("-fx-background-color: black; -fx-padding: 0;");

        // 🎚️ Right Side: Meters & Loop Progress (Canvas)
        canvas = new Canvas(312, 70);
        canvas.setStyle("-fx-background-color: black;");

        HBox root = new HBox(clipPane, canvas);
        Scene scene = new Scene(root, 1512, 70, Color.BLACK);

        primaryStage.setScene(scene);
        primaryStage.setTitle("BitX Display");
        primaryStage.setAlwaysOnTop(true);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> stopServer());

        log("JavaFX application started. Opening server socket...");
        new Thread(this::listenForUpdates).start();
    }

    private void stopServer() {
        log("Stopping server and closing connections...");
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
            log("Server socket opened on port 9876. Listening for connections...");

            while (running) {
                log("Waiting for a connection...");
                Socket clientSocket = serverSocket.accept();
                log("Connection received!");

                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line = reader.readLine();
                clientSocket.close();
                log("Received data: " + line);

                if (line != null && !line.isEmpty()) {
                    if (line.startsWith("COLOR:")) {
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
                    if (line.startsWith("CLIP:")) {
                        String clipName = line.substring(5);
                        log("Updating clip name to: " + clipName);
                        Platform.runLater(() -> updateClipText(clipName));
                    }
                    if (line.startsWith("VU:")) {
                        String[] parts = line.split(":");
                        int trackIndex = Integer.parseInt(parts[1]);
                        int vuValue = Integer.parseInt(parts[2]);

                        Platform.runLater(() -> {
                            meterValues[trackIndex] = vuValue / 127.0f; // Normalize to 0.0 - 1.0
                            drawMeters();
                        });
                    }
//                    if (line.startsWith("LOOP:")) {
//                        String[] parts = line.split(":");
//
//                        if (parts.length < 4) {
//                            logError("Invalid LOOP message format: " + line);
//                            return;
//                        }
//
//                        int trackIndex = Integer.parseInt(parts[1]);
//                        float progress = Float.parseFloat(parts[2]);
//                        float clipLength = Float.parseFloat(parts[3]); // ✅ Get clip length
//
//                        log("✅ Updating loop progress: Track " + trackIndex +
//                                " Progress: " + progress + " Length: " + clipLength);
//
//                        Platform.runLater(() -> {
//                            loopProgressValues[trackIndex] = progress;
//                            clipLengths[trackIndex] = clipLength; // ✅ Store clip length
//                            drawMeters();
//                        });
//                    }


                }
            }
        } catch (Exception e) {
            if (!running) {
                log("Server stopped.");
            } else {
                logError("Error in listenForUpdates: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateClipText(String clipName) {
        clipTextFlow.getChildren().clear();

        // Create text chunks for wrapping
        Text text = new Text(clipName);
        text.setFont(Font.font("Arial", 30));
        text.setFill(Color.YELLOW);

        clipTextFlow.getChildren().add(text);

        clipTextFlow.setPrefWidth(1200);
        clipTextFlow.setMaxHeight(60); // Max height to allow two lines
    }

    private void drawMeters() {
        log("Drawing meters...");
        long startTime = System.nanoTime();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double meterWidth = canvas.getWidth() / NUM_TRACKS;
        double canvasHeight = canvas.getHeight();
        double meterHeight = canvasHeight * 0.8;  // 80% height for meters
        double loopHeight = canvasHeight * 0.2;   // 20% height for loop progress

        for (int i = 0; i < NUM_TRACKS; i++) {
            double x = i * meterWidth;
            double y = meterHeight - (meterHeight * meterValues[i]);

            // Draw track color background under the meter
            gc.setFill(trackColors[i] != null ? trackColors[i] : Color.GRAY);
            gc.fillRect(x + 5, meterHeight + 2, meterWidth - 10, loopHeight - 2); // Draw color bar

            // Draw meter background
            gc.setFill(Color.DARKGRAY);
            gc.fillRect(x + 5, 0, meterWidth - 10, meterHeight);

            // Draw meter level
            gc.setFill(getMeterColor(meterValues[i]));
            gc.fillRect(x + 5, y, meterWidth - 10, meterHeight * meterValues[i]);

            // ✅ Use track color for the loop progress bar
//            if (loopProgressValues[i] > 0) {
//                Color trackColor = trackColors[i] != null ? trackColors[i] : Color.LIMEGREEN;
//                gc.setFill(trackColor);
//                double loopWidth = (meterWidth - 10) * loopProgressValues[i]; // Ensure same width as meter
//                gc.fillRect(x + 5, meterHeight, loopWidth, loopHeight);
//            }
        }

        long endTime = System.nanoTime();
        log("Meters drawn in " + (endTime - startTime) / 1_000_000.0 + " ms.");
    }


    private Color getMeterColor(double level) {
        if (level > 0.8) return Color.RED;
        if (level > 0.6) return Color.ORANGE;
        if (level > 0.4) return Color.YELLOW;
        if (level > 0.2) return Color.LIME;
        return Color.DARKBLUE;
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
