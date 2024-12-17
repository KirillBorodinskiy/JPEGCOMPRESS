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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ImageCompressionApp extends Application {

    private final ImageView originalImageView = new ImageView();
    private final ImageView compressedImageView = new ImageView();
    private BufferedImage bufferedImage;
    private final int DEFAULT_COMPRESSION_LEVEL = 80;
    private final int BLOCK_SIZE = 8;

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
                compressImage(newVal.intValue());
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
                BufferedImage bufferedImage = ImageIO.read(new File(selectedFile.toURI().toString()));

                Image image = SwingFXUtils.toFXImage(bufferedImage, null);
                originalImageView.setImage(image);
                int IMAGE_WIDTH = 400;
                originalImageView.setFitWidth(IMAGE_WIDTH);
                originalImageView.setPreserveRatio(true);
                compressImage(DEFAULT_COMPRESSION_LEVEL);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void compressImage(int compressionLevel) {
        /*
        1.Convert RGB to YCrCb
        2.Crominance downsapling
        3.Discrete Cosine Transform
        4.Quantization
        5.RLE and huffman
         */
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        double[][] YValue = new double[height][width];
        double[][] CbValue = new double[height][width];
        double[][] CrValue = new double[height][width];
        // Step 1: Convert RGB to YCrCb
        // The result will be inside the arrays
        convertToYCbCr(bufferedImage, YValue, CbValue, CrValue);

        // Step 2: Crominance downsapling
        //THE SIZE OF AN ARRAY IS 4 TIMES SMALLER
        double[][] DCbValue = downsample(CbValue, height, width);
        double[][] DCrValue = downsample(CrValue, height, width);

        // Step 3: DCT
        DCT(YValue);
        DCT(DCbValue);
        DCT(DCrValue);
    }

    private void convertToYCbCr(BufferedImage input, double[][] YValue, double[][] CbValue, double[][] CrValue) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = input.getRGB(x, y);
                Color color = new Color(rgb);

                int red = color.getRed();
                int green = color.getGreen();
                int blue = color.getBlue();

                YValue[x][y] = 0.299 * red + 0.587 * green + 0.114 * blue;
                CbValue[x][y] = 128 - 0.168736 * red - 0.331264 * green + 0.5 * blue;
                CrValue[x][y] = 128 + 0.5 * red - 0.418688 * green - 0.081312 * blue;

            }
        }
    }

    private double[][] downsample(double[][] Color, int height, int width) {

        double[][] DownsampledColor = new double[height / 2][width / 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                DownsampledColor[y / 2][x / 2] = (Color[y][x] + Color[y + 1][x] + Color[y + 1][x + 1] + Color[y][x + 1]) / 4;
            }
        }

        return DownsampledColor;
    }

    private void DCT(double[][] input) {
        //Blocks of 8x8
        for (int i = 0; i < input.length; i += BLOCK_SIZE) {
            for (int j = 0; j < input.length; j += BLOCK_SIZE) {
                double[][] block = extractBlocks(input, i, j);
                double[][] DCTblock = performDCT(block);
            }
        }
    }

    private double[][] extractBlocks(double[][] input, int i, int j) {
        //Now we have to extract one block from this position
        double[][] block = new double[8][8];

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                //Get the correct element of an array
                block[x][y] = input[i + x][j + y];
                // Preparation for DCT
                block[x][y] = block[x][y] - 128;
            }
        }
        return block;

    }


    private double[][] performDCT(double[][] block) {

        // Magic numbers from the formula
        double c1 = Math.sqrt((double) 1 / BLOCK_SIZE);
        double c2 = Math.sqrt((double) 2 / BLOCK_SIZE);

        double[][] temp_block = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int p = 0; p < BLOCK_SIZE - 1; p++) {
            for (int q = 0; q < BLOCK_SIZE - 1; q++) {

                double sum = 0;
                for (int m = 0; m < BLOCK_SIZE - 1; m++) {
                    for (int n = 0; n < BLOCK_SIZE - 1; n++) {
                        // Formula from
                        // https://www.mathworks.com/help/images/discrete-cosine-transform.html
                        sum += block[m][n] * Math.cos(Math.PI * (2 * m + 1)) / (2 * m) * Math.cos(Math.PI * (2 * n + 1)) / (2 * n);

                    }
                }
                double alfaP = (p == 0) ? c1 : c2;
                double alfaQ = (q == 0) ? c1 : c2;
                temp_block[p][q] = sum * alfaP * alfaQ;
            }
        }
        return temp_block;
    }

    public static void main(String[] args) {
        launch(args);
    }
}