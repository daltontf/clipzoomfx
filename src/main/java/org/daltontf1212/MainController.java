package org.daltontf1212;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vavr.Lazy;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurfaceFactory.videoSurfaceForImageView;

public class MainController {
    private final static double VIDEO_WIDTH = 1920.0;
    private final static double VIDEO_HEIGHT = 1080.0;

    public final ImageView PLAY_ICON = new ImageView("play.png");
    public final ImageView CLIP_PLAY_ICON = new ImageView("play.png");
    public final ImageView PAUSE_ICON = new ImageView("pause.png");
    public final ImageView STOP_ICON = new ImageView("stop.png");

    private Lazy<FileChooser> lazyFileChooser = Lazy.of(() -> {
        var chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files",
                "*.mp4", "*.mts", "*.MTS", "*.m4v", "*.m2ts"));

        return chooser;
    });
    private final MediaPlayerFactory mediaPlayerFactory;

    @FXML private MenuItem addVideo;
    @FXML private MenuItem loadProject;
    @FXML private MenuItem saveProject;
    @FXML private MenuItem saveProjectAs;
    @FXML private MenuItem renderScript;
    @FXML private ListView<File> files;
    @FXML private ListView<Clip> clips;
    @FXML private VBox videoPane;
    @FXML private ImageView videoImageView;
    private final EmbeddedMediaPlayer embeddedMediaPlayer;

    @FXML private HBox bottomBarPlay;
    @FXML private Button playPause;
    @FXML private Slider playSlider;

    @FXML private StackPane bottomPane;
    @FXML private HBox bottomBarClip;
    @FXML private Button clipPlayStop;
    @FXML private Button saveClip;
    @FXML private RangeSlider rangeSlider;

    private static final int MIN_PIXELS = 200;

    private Optional<File> loadedMedia = Optional.empty();
    private Optional<Clip> editingClip = Optional.empty();
    private SimpleBooleanProperty projectDirty = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty clipDirty = new SimpleBooleanProperty(false);
    private SimpleObjectProperty<File> projectFile = new SimpleObjectProperty<>(null);

    public MainController() {
        this.mediaPlayerFactory = new MediaPlayerFactory();
        this.embeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
    }

    public boolean load(File file) {
        bottomBarPlay.setVisible(true);
        bottomBarClip.setVisible(false);
        reset(videoImageView, VIDEO_WIDTH, VIDEO_HEIGHT);
        embeddedMediaPlayer.controls().stop();
        embeddedMediaPlayer.media().play(file.getAbsolutePath());
        loadedMedia = Optional.of(file);
        // TODO figure out bad file, gripe and return false;
        return true;
    }

    @FXML
    public void skipBackward(ActionEvent event) {
        if (embeddedMediaPlayer.status().isPlaying()) {
            long time = embeddedMediaPlayer.status().time();
            embeddedMediaPlayer.controls().setTime(Math.max(time - 10000, 0));
        }
    }

    @FXML
    public void playPause(ActionEvent event) {
        if (playPause.getGraphic().equals(PAUSE_ICON)) {
            embeddedMediaPlayer.controls().pause();
        } else {
            embeddedMediaPlayer.controls().play();
        }
    }

    @FXML
    public void skipForward(ActionEvent event) {
        if (embeddedMediaPlayer.status().isPlaying()) {
            long time = embeddedMediaPlayer.status().time();
            embeddedMediaPlayer.controls().setTime(Math.min(time + 10000, embeddedMediaPlayer.status().length()));
        }
    }

    @FXML
    public void clipPlayStop(ActionEvent event) {
        if (clipPlayStop.getGraphic().equals(STOP_ICON)) {
            embeddedMediaPlayer.controls().pause();
        } else {
            embeddedMediaPlayer.controls().setTime((long) rangeSlider.getLowValue() * 1000);
            embeddedMediaPlayer.controls().play();
        }
    }

    @FXML
    public void addClip(ActionEvent event) {
        embeddedMediaPlayer.controls().pause();

        long time = embeddedMediaPlayer.status().time();

        activateClipMode(
                Math.max(time - 30000, 0),
                Math.max(time - 5000, 0),
                Math.min(time + 5000, embeddedMediaPlayer.status().length()),
                Math.min(time + 30000, embeddedMediaPlayer.status().length()),
                Optional.empty()
        );

        clipDirty.setValue(true);

        embeddedMediaPlayer.controls().setTime(Math.max((int) time - 5000, 0));
    }

    @FXML
    public void cancelClip(ActionEvent event) {
        if (clipDirty.get()) {
            var dialog = new Dialog<ButtonType>();
            dialog.setTitle("Confirm Cancel Clip");
            dialog.setContentText("Do wish save the clip before exiting clip editor?");
            dialog.getDialogPane().getButtonTypes().addAll(
                    Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
            );
            var choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (choice == ButtonType.CANCEL) {
                return;
            }
            if (choice == ButtonType.YES) {
                saveClip(event);
            }
        }
        bottomBarPlay.setVisible(true);
        bottomBarClip.setVisible(false);

        playSlider.setValue(embeddedMediaPlayer.status().time() / 1000.0);
    }

    @FXML
    public void saveClip(ActionEvent event) {
        clipDirty.setValue(false);
        projectDirty.setValue(true);
        var newClip = new Clip(
                loadedMedia.get(),
                rangeSlider.getMin() * 1000.0,
                rangeSlider.getLowValue() * 1000.0,
                rangeSlider.getHighValue() * 1000.0,
                rangeSlider.getMax() * 1000.0,
                videoImageView.getViewport()
        );
        if (editingClip.isPresent()) {
            ObservableList<Clip> items = clips.getItems();
            int idx = items.indexOf(editingClip.get());
            items.remove(idx);
            items.add(idx, newClip);
        } else {
            clips.getItems().add(newClip);
        }
        editingClip = Optional.of(newClip);
    }

    @FXML
    public void clipSetStart(ActionEvent event) {
        clipDirty.setValue(true);
        rangeSlider.setLowValue(embeddedMediaPlayer.status().time() / 1000.0);
    }

    @FXML
    public void clipSetStop(ActionEvent event) {
        clipDirty.setValue(true);
        rangeSlider.setHighValue(embeddedMediaPlayer.status().time() / 1000.0);
    }

    @FXML
    public void keyPressedFiles(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            projectDirty.setValue(true);
            files.getItems().removeAll(files.getSelectionModel().getSelectedItems());
        }
    }
    @FXML
    public void keyPressedClips(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            projectDirty.setValue(true);
            clips.getItems().removeAll(clips.getSelectionModel().getSelectedItems());
        }
    }

    @FXML
    public void sortFiles(ActionEvent event) {
        projectDirty.setValue(true);
        files.getItems().sort(Comparator.comparing(File::getName));
    }

    @FXML
    public void sortClips(ActionEvent event) {
        projectDirty.setValue(true);
        clips.getItems().sort(Comparator.comparing(Clip::itemRepresentation));
    }

    public void start(Stage stage) {
        stage.getIcons()
            .add(new Image(this.getClass().getResourceAsStream( "/icon48.png" )));

        clips.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        files.setCellFactory(listView -> {
            var listCell = new ListCell<File>() {
                @Override
                public void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText(null);
                    } else if (item != null) {
                        setText(item.getName());
                    }
                }
            };

            listCell.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    if (!load(listCell.getItem())) {
                        files.getItems().remove(listCell.getItem());
                    }
                }
            });
            return listCell;
        });

        clips.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        clips.setCellFactory(listView -> {
            var listCell = new ListCell<Clip>() {
                @Override
                public void updateItem(Clip item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText(null);
                    } else if (item != null) {
                        setText(item.itemRepresentation());
                    }
                }
            };

            listCell.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    loadClip(listCell.getItem());
                }
            });
            return listCell;
        });

        embeddedMediaPlayer.videoSurface().set(videoSurfaceForImageView(this.videoImageView));
        embeddedMediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void mediaPlayerReady(MediaPlayer mediaPlayer) {
                Platform.runLater(() ->  playSlider.setMax(mediaPlayer.status().length() / 1000.0));
            }

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPause.setGraphic(PAUSE_ICON);
                    clipPlayStop.setGraphic(STOP_ICON);
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPause.setGraphic(PLAY_ICON);
                    clipPlayStop.setGraphic(CLIP_PLAY_ICON);
                });
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                if (bottomBarPlay.isVisible()) {
                    if (!playSlider.isValueChanging()) {
                        playSlider.setValue(newTime / 1000.0);
                    }
                } else {
                    if (newTime >= rangeSlider.getHighValue() * 1000) {
                        mediaPlayer.controls().pause();
                    }
                }
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPause.setGraphic(PLAY_ICON);
                    clipPlayStop.setGraphic(CLIP_PLAY_ICON);
                });
            }
        });

        playSlider.valueProperty().addListener((observableValue, number, newValue) -> {
            if (playSlider.isValueChanging()) {
                embeddedMediaPlayer.controls().setTime(newValue.longValue() * 1000);
            }
        });

        videoImageView.fitWidthProperty().bind(videoPane.widthProperty().subtract(10));
        videoImageView.fitHeightProperty().bind(videoPane.heightProperty().subtract(bottomPane.heightProperty()).subtract(5));

        var mouseDown = new SimpleObjectProperty<Point2D>();

        videoImageView.setOnMousePressed(e -> {
            Point2D mousePress = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));
            mouseDown.set(mousePress);
        });

        videoImageView.setOnMouseDragged(e -> {
            clipDirty.setValue(true);
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
                    Math.max(VIDEO_WIDTH / viewport.getWidth(), VIDEO_HEIGHT / viewport.getHeight())
            );

            Point2D mouse = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));

            double newWidth = viewport.getWidth() * scale;
            double newHeight = viewport.getHeight() * scale;

            double newMinX = clamp(mouse.getX() - (mouse.getX() - viewport.getMinX()) * scale,
                    0, VIDEO_WIDTH - newWidth);
            double newMinY = clamp(mouse.getY() - (mouse.getY() - viewport.getMinY()) * scale,
                    0, VIDEO_HEIGHT - newHeight);
            if (newMinX != viewport.getMinX()
                    || newMinY != viewport.getMinY()
                    || newWidth != viewport.getWidth()
                    || newHeight != viewport.getHeight()) {
                clipDirty.setValue(true);
            }

            videoImageView.setViewport(new Rectangle2D(newMinX, newMinY, newWidth, newHeight));
        });

        addVideo.setOnAction(event -> {
            List<File> choosen = lazyFileChooser.get().showOpenMultipleDialog(stage);
            if (choosen != null) {
                files.getItems().addAll(choosen);
            }
            projectDirty.setValue(true);
        });

        loadProject.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files",
                            "*.json"));
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                ObjectMapper mapper = new ObjectMapper();
                SimpleModule module = new SimpleModule();
                module.addDeserializer(Rectangle2D.class, new RectangleDeserializer(Rectangle2D.class));
                mapper.registerModule(module);

                files.getItems().clear();
                clips.getItems().clear();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    files.getItems().addAll(mapper.readValue(reader.readLine(), new TypeReference<List<File>>() {
                    }));
                    String content = reader.readLine();
                    clips.getItems().addAll(mapper.readValue(content, new TypeReference<List<Clip>>() {
                    }));
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                }
                projectDirty.setValue(false);
                projectFile.setValue(file);
            }
        });

        saveProject.setOnAction(event -> {
            saveProject();
        });

        projectDirty.addListener(
            (observableValue, oldValue, newValue) -> {
                saveProject.setDisable(projectFile.get() == null || !newValue);
            });
        saveProject.setDisable(true);

        saveProjectAs.setOnAction(event -> {
            saveProjectAs(stage);
        });

        renderScript.setOnAction(event -> {
            File dir = new DirectoryChooser().showDialog(stage);
            try (Writer runWriter = new BufferedWriter(new FileWriter(new File(dir, "run.sh")));
                 Writer concatWriter = new BufferedWriter(new FileWriter(new File(dir, "concat_files.txt")))
            ) {
                for (Clip clip : clips.getSelectionModel().getSelectedItems()) {
                    Rectangle2D viewPort = clip.viewportRect;
                    runWriter.append(String.format("ffmpeg -ss %.2f -i %s -vf \"crop=%d:%d:%d:%d, scale=%d:%d\" -t %.2f %s.mp4\n",
                            clip.lowValue / 1000,
                            clip.file,
                            (int) viewPort.getWidth(),
                            (int) viewPort.getHeight(),
                            (int) viewPort.getMinX(),
                            (int) viewPort.getMinY(),
                            (int) VIDEO_WIDTH,
                            (int) VIDEO_HEIGHT,
                            (clip.highValue - clip.lowValue) / 1000,
                            clip.itemRepresentation()
                    ));

                    concatWriter.append(String.format("file %s.mp4\n", clip.itemRepresentation()));
                }

                runWriter.append("ffmpeg -f concat -i concat_files.txt -c copy concatenated.mp4\n");
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        });

        saveClip.disableProperty().bind(clipDirty.not());

        stage.setOnCloseRequest(windowEvent -> {
            if (projectDirty.get()) {
                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.setTitle("Confirm Exit");
                dialog.setContentText("Save project changes before exit?");
                dialog.getDialogPane().getButtonTypes().addAll(
                        Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
                );
                ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
                if (choice == ButtonType.CANCEL) {
                    windowEvent.consume();
                    return;
                }
                if (choice == ButtonType.YES) {
                    var file = projectFile.getValue();
                    if (file == null) {
                        saveProjectAs(stage);
                    } else {
                        saveProject();
                    }
                }
            }
        });
    }

    private void saveProjectFile(File file) {
        ObjectMapper mapper = new ObjectMapper();

        try (Writer writer = new BufferedWriter(new FileWriter(file))) {
            writer.append(mapper.writeValueAsString(files.getItems()));
            writer.append('\n');
            writer.append(mapper.writeValueAsString(clips.getItems()));
            writer.append('\n');
            projectDirty.setValue(false);
            projectFile.setValue(file);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

    private void saveProject() {
        saveProjectFile(projectFile.getValue());
    }

    private void saveProjectAs(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files",
                        "*.json"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            saveProjectFile(file);
        }
    }

    private void loadClip(Clip clip) {
        embeddedMediaPlayer.controls().stop();
        load(clip.file);
        activateClipMode(clip.minValue, clip.lowValue, clip.highValue, clip.maxValue, Optional.of(clip));
        clipDirty.setValue(false);
        videoImageView.setViewport(clip.viewportRect);
        embeddedMediaPlayer.controls().setTime((long) clip.lowValue);
    }

    private void activateClipMode(double minValue, double lowValue, double highValue, double maxValue, Optional<Clip> editingClip) {
        bottomBarPlay.setVisible(false);
        bottomBarClip.setVisible(true);

        rangeSlider.setMin(Math.round(minValue / 1000.0));
        rangeSlider.setMax(Math.round(maxValue / 1000.0));
        rangeSlider.setHighValue(highValue / 1000.0);
        rangeSlider.setLowValue(lowValue / 1000.0);
        rangeSlider.setHighValue(highValue / 1000.0);

        this.editingClip = editingClip;
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

    public static class Clip {
        public final File file;
        public final double minValue;
        public final double lowValue;
        public final double highValue;
        public final double maxValue;
        public final Rectangle2D viewportRect;

        @JsonCreator
        public Clip(
            @JsonProperty("file") File file,
            @JsonProperty("minValue") double minValue,
            @JsonProperty("lowValue") double lowValue,
            @JsonProperty("highValue") double highValue,
            @JsonProperty("maxValue") double maxValue,
            @JsonProperty("viewportRect") Rectangle2D viewportRect) {
            this.file = file;
            this.minValue = minValue;
            this.lowValue = lowValue;
            this.highValue = highValue;
            this.maxValue = maxValue;
            this.viewportRect = viewportRect;
        }

        public String itemRepresentation() {
            return String.format("%s_%d",
                    file.getName().replaceFirst("[.][^.]+$", ""),
                    (long) (lowValue * 1000));
        }
    }

    public static class RectangleDeserializer extends StdDeserializer<Rectangle2D> {
        public RectangleDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Rectangle2D deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            return new Rectangle2D(
                    node.get("minX").doubleValue(),
                    node.get("minY").doubleValue(),
                    node.get("width").doubleValue(),
                    node.get("height").doubleValue()
            );
        }
    }
}
