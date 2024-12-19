package image.compress.imagecompress;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class ImageCompressionApp extends Application {

    // CONSTANTS:
    private static final ImageView originalImageView = new ImageView();
    private static final ImageView compressedImageView = new ImageView();
    private static BufferedImage bufferedImage;
    private static final JpegCompress jpegCompress = new JpegCompress();

    public static final Logger LOGGER = Logger.getLogger(ImageCompressionApp.class.getName());
    public static final int DEFAULT_COMPRESSION_LEVEL = 80;


    /**
     * Safely retrieves the value at the specified coordinates in a 2D array.
     * If the specified coordinates are out of bounds, the function returns 0.
     *
     * @param array the 2D array from which the value is to be accessed
     * @param y     the row index of the desired value
     * @param x     the column index of the desired value
     * @return the value at the specified coordinates, or 0 if the coordinates are out of bounds
     */
    public static double getSafeValueAt(double[][] array, int y, int x) {
        return (y < array.length && x < array[0].length) ? array[y][x] : 0;
    }

    /**
     * Safely retrieves the value at the specified coordinates in a 1D array.
     * If the specified coordinates are out of bounds, the function returns 0.
     *
     * @param array the 2D array from which the value is to be accessed
     * @param x     the index of the desired value
     * @return the value at the specified coordinates, or 0 if the coordinates are out of bounds
     */
    public static double getSafeValueAt(int[] array, int x) {
        return (x < array.length) ? array[x] : 0;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Image Compression Tool");

        // UI Components
        Button loadButton = new Button("Load Image");
        Button saveButton = new Button("Save Compressed Image");
        Slider compressionSlider = new Slider(1, 99, DEFAULT_COMPRESSION_LEVEL);
        Label compressionLabel = new Label("Compression Level: " + DEFAULT_COMPRESSION_LEVEL + "%");

        // Layout
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(loadButton, 0, 0);
        grid.add(compressionLabel, 1, 0);
        grid.add(compressionSlider, 1, 1);
        grid.add(saveButton, 2, 0);
        grid.add(originalImageView, 0, 2);
        grid.add(compressedImageView, 1, 2);

        // Event Handlers
        loadButton.setOnAction(e -> loadImage(primaryStage));
        compressionSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            compressionLabel.setText("Compression Level: " + newVal.intValue() + "%");
            if (bufferedImage != null) {
                jpegCompress.compressImage(bufferedImage, newVal.intValue());
            }
        });
        // Scene
        Scene scene = new Scene(grid, 1280, 720);
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    private void loadImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                // Use the selected file directly
                BufferedImage bufferedImage = ImageIO.read(selectedFile);

                Image image = SwingFXUtils.toFXImage(bufferedImage, null);
                originalImageView.setImage(image);

                int IMAGE_WIDTH = 400;
                originalImageView.setFitWidth(IMAGE_WIDTH);
                originalImageView.setPreserveRatio(true);

                jpegCompress.compressImage(bufferedImage, DEFAULT_COMPRESSION_LEVEL);

            } catch (IOException e) {
                e.printStackTrace();
                // Notify the user about the error
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load the image: " + e.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}