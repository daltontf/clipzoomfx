package org.daltontf1212;

import io.vavr.Lazy;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.io.File;
import java.util.Optional;

import static uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurfaceFactory.videoSurfaceForImageView;

public class MainController {
    private Lazy<FileChooser> lazyFileChooser = Lazy.of(FileChooser::new);
    private final MediaPlayerFactory mediaPlayerFactory;

    @FXML private MenuBar menuBar;
    @FXML private MenuItem addVideoMenuItem;
    @FXML private SplitPane splitPane;
    @FXML private ScrollPane treeScrollPane;
    @FXML private TreeView<File> treeView;
    @FXML private VBox videoPane;
    @FXML private ImageView videoImageView;
    private final EmbeddedMediaPlayer embeddedMediaPlayer;

    @FXML private HBox bottomBarPlay;
    @FXML private Button playPause;
    @FXML private Slider playSlider;
    @FXML private Button clip;

    @FXML private StackPane bottomPane;
    @FXML private HBox bottomBarClip;
    @FXML private Button clipPlayStop;
    @FXML private Button clipSetStart;
    @FXML private RangeSlider rangeSlider;
    @FXML private Button clipSetStop;
    @FXML private Button saveClip;
    @FXML private Button cancelClip;

    private static final int MIN_PIXELS = 200;
    private static final String PLAY = "play";
    private static final String PAUSE = "pause";
    private static final String STOP = "stop";

    private double width = 1920.0;
    private double height = 1080.0;

    private Optional<String> loadedMedia;

    public MainController() {
        this.mediaPlayerFactory = new MediaPlayerFactory();
        this.embeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
    }

    public void load(String url) {
        embeddedMediaPlayer.media().play(url);
        embeddedMediaPlayer.controls().setPosition(0.0f);
        reset(videoImageView, width, height);
        loadedMedia = Optional.of(url);
    }

    public void start(Stage stage) {
        videoImageView.setPreserveRatio(true);

        embeddedMediaPlayer.videoSurface().set(videoSurfaceForImageView(this.videoImageView));
        embeddedMediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playSlider.setMax(mediaPlayer.status().length());
                    playPause.setText(PAUSE);
                    clipPlayStop.setText(STOP);
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPause.setText(PLAY);
                    clipPlayStop.setText(PLAY);
                });
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                if (bottomBarPlay.isVisible()) {
                    if (!playSlider.isValueChanging()) {
                        playSlider.setValue(newTime);
                    }
                } else {
                    if (newTime >= rangeSlider.getHighValue()) {
                        mediaPlayer.controls().pause();
                    }
                }
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPause.setText(PLAY);
                    clipPlayStop.setText(PLAY);
                });
            }
        });

        playSlider.valueProperty().addListener((observableValue, number, t1) -> {
            if (playSlider.isValueChanging()) {
                embeddedMediaPlayer.controls().setTime(t1.longValue());
            }
        });

        //reset(videoImageView, width, height);

        videoImageView.fitWidthProperty().bind(videoPane.widthProperty().subtract(10));
        videoImageView.fitHeightProperty().bind(videoPane.heightProperty().subtract(bottomPane.heightProperty()).subtract(5));

        //videoImageView.fitWidthProperty().addListener(new PrintChangeListener("videoImageView.fitWidth"));
        //videoPane.heightProperty().addListener(new PrintChangeListener("videoPane.height"));

        treeView.setCellFactory(tree -> {
            TreeCell<File> treeCell = new TreeCell<>() {
                @Override
                public void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty) ;
                    if (empty) {
                        setText(null);
                    } else if (item != null) {
                        setText(item.getName());
                    }
                }
            };
            treeCell.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    load(treeCell.getTreeItem().getValue().getAbsolutePath());
                }
            });
            return treeCell;
        });

        ObjectProperty<Point2D> mouseDown = new SimpleObjectProperty<>();

        videoImageView.setOnMousePressed(e -> {
            Point2D mousePress = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));
            mouseDown.set(mousePress);
        });

        videoImageView.setOnMouseDragged(e -> {
            Point2D dragPoint = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));
            shift(videoImageView, dragPoint.subtract(mouseDown.get()));
            mouseDown.set(imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY())));
        });

        videoImageView.setOnScroll(e -> {
            double delta = -e.getDeltaY();
            Rectangle2D viewport = videoImageView.getViewport();

            double scale = clamp(Math.pow(1.001, delta),
                    // don't scale so we're zoomed in to fewer than MIN_PIXELS in any direction:
                    Math.min(MIN_PIXELS / viewport.getWidth(), MIN_PIXELS / viewport.getHeight()),

                    // don't scale so that we're bigger than image dimensions:
                    Math.max(width / viewport.getWidth(), height / viewport.getHeight())
            );

            Point2D mouse = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));

            double newWidth = viewport.getWidth() * scale;
            double newHeight = viewport.getHeight() * scale;

            double newMinX = clamp(mouse.getX() - (mouse.getX() - viewport.getMinX()) * scale,
                    0, width - newWidth);
            double newMinY = clamp(mouse.getY() - (mouse.getY() - viewport.getMinY()) * scale,
                    0, height - newHeight);
            videoImageView.setViewport(new Rectangle2D(newMinX, newMinY, newWidth, newHeight));
        });

        playPause.setOnAction(event -> {
            if (playPause.getText().equals(PAUSE)) {
                embeddedMediaPlayer.controls().pause();
            } else {
                embeddedMediaPlayer.controls().play();
            }
        });

        clipPlayStop.setOnAction(event -> {
            if (clipPlayStop.getText().equals(STOP)) {
                embeddedMediaPlayer.controls().pause();
            } else {
                embeddedMediaPlayer.controls().setTime((long) rangeSlider.getLowValue());
                embeddedMediaPlayer.controls().play();
            }
        });

        treeView.setRoot(new TreeItem<>());
        treeView.setShowRoot(false);

        addVideoMenuItem.setOnAction(event -> {
            File file = lazyFileChooser.get().showOpenDialog(stage);

            TreeItem<File> fileTreeItem = new TreeItem<>(file);

            treeView.getRoot().getChildren().add(fileTreeItem);
        });

        clip.setOnAction( event -> {
            bottomBarPlay.setVisible(false);
            bottomBarClip.setVisible(true);
            embeddedMediaPlayer.controls().pause();

            long time = embeddedMediaPlayer.status().time();

            rangeSlider.setMin(Math.max(time - 30000, 0));
            rangeSlider.setMax(Math.min(time + 30000, embeddedMediaPlayer.status().length()));
            rangeSlider.setLowValue(Math.max(time - 10000, 0));
            rangeSlider.setHighValue(Math.min(time + 10000, embeddedMediaPlayer.status().length()));
            embeddedMediaPlayer.controls().setTime(Math.max(time - 10000, 0));
        });

        cancelClip.setOnAction(event -> {
            bottomBarPlay.setVisible(true);
            bottomBarClip.setVisible(false);
        });

        saveClip.setOnAction(event -> {
            loadedMedia.ifPresent( url -> {
                Rectangle2D viewPort = videoImageView.getViewport();
                System.out.printf("ffmpeg -ss %.2f -i %s -vf \"crop=%d:%d:%d:%d\" -t %.2f\n",
                        rangeSlider.getLowValue() / 1000,
                        url,
                        (int) viewPort.getWidth(),
                        (int) viewPort.getHeight(),
                        (int) viewPort.getMinX(),
                        (int) viewPort.getMinY(),
                        (rangeSlider.getHighValue() - rangeSlider.getLowValue()) / 1000
                );
            });
        });

        clipSetStart.setOnAction(event -> {
            rangeSlider.setLowValue(embeddedMediaPlayer.status().time());
        });

        clipSetStop.setOnAction(event -> {
            rangeSlider.setHighValue(embeddedMediaPlayer.status().time());
        });

        splitPane.setDividerPositions(0.20);
    }

    // reset to the top left:
    private void reset(ImageView imageView, double width, double height) {
        imageView.setViewport(new Rectangle2D(0, 0, width, height));
    }

    // shift the viewport of the imageView by the specified delta, clamping so
    // the viewport does not move off the actual image:
    private void shift(ImageView imageView, Point2D delta) {
        Rectangle2D viewport = imageView.getViewport();
        double width = imageView.getImage().getWidth();
        double height = imageView.getImage().getHeight();
        double maxX = width - viewport.getWidth();
        double maxY = height - viewport.getHeight();
        double minX = clamp(viewport.getMinX() - delta.getX(), 0, maxX);
        double minY = clamp(viewport.getMinY() - delta.getY(), 0, maxY);
        if (minX < 0.0) {
            minX = 0.0;
        }
        if (minY < 0.0) {
            minY = 0.0;
        }
        imageView.setViewport(new Rectangle2D(minX, minY, viewport.getWidth(), viewport.getHeight()));
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    // convert mouse coordinates in the imageView to coordinates in the actual image:
    private Point2D imageViewToImage(ImageView imageView, Point2D imageViewCoordinates) {
        double xProportion = imageViewCoordinates.getX() / imageView.getBoundsInLocal().getWidth();
        double yProportion = imageViewCoordinates.getY() / imageView.getBoundsInLocal().getHeight();

        Rectangle2D viewport = imageView.getViewport();
        return new Point2D(
                viewport.getMinX() + xProportion * viewport.getWidth(),
                viewport.getMinY() + yProportion * viewport.getHeight());
    }

    public void stop() {
        embeddedMediaPlayer.controls().stop();
        embeddedMediaPlayer.release();
        mediaPlayerFactory.release();
    }
}
