package org.daltontf1212;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.Stream;
import io.vavr.Lazy;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.RangeSlider;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.LibVlcConst;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurfaceFactory.videoSurfaceForImageView;

public class MainController {
    @FXML private MenuItem closeProject;
    @FXML private MenuItem saveProject;
    @FXML private MenuItem saveProjectAs;
    @FXML private MenuItem generateVideo;
    @FXML private ListView<File> files;
    @FXML private TableView<Clip> clips;
    @FXML private VBox videoPane;
    @FXML private ImageView videoImageView;
    @FXML private HBox bottomBarPlay;
    @FXML private Button playPause;
    @FXML private Slider playSlider;
    @FXML private StackPane bottomPane;
    @FXML private HBox bottomBarClip;
    @FXML private Button clipPlayStop;
    @FXML private Button saveClip;
    @FXML private RangeSlider rangeSlider;

    private static final int MIN_PIXELS = 200;
    private static final int SKIP_TIME_MILLIS = 10000;

    public final ImageView PLAY_ICON = new ImageView("play.png");
    public final ImageView CLIP_PLAY_ICON = new ImageView("play.png");
    public final ImageView PAUSE_ICON = new ImageView("pause.png");
    public final ImageView STOP_ICON = new ImageView("stop.png");

    private final MediaPlayerFactory mediaPlayerFactory;
    private final EmbeddedMediaPlayer embeddedMediaPlayer;

    private final Lazy<FileChooser> lazyFileChooser = Lazy.of(() -> {
        var chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files",
                        "*.mp4", "*.mts", "*.MTS", "*.m4v", "*.m2ts", "*.avi"));

        return chooser;
    });

    private final SimpleBooleanProperty projectDirty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty clipDirty = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<File> projectFile = new SimpleObjectProperty<>(null);
    private final Map<File, List<Clip>> fileToClips = new HashMap<>();

    private double videoWidth = 1920.0;
    private double videoHeight = 1080.0;

    private Optional<File> loadedMedia = Optional.empty();
    private Optional<Clip> editingClip = Optional.empty();
    private Optional<ClipDescriptionData> clipDescription = Optional.empty();

    private Stage stage;

    public MainController() {
        this.mediaPlayerFactory = new MediaPlayerFactory();
        this.embeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
    }

    public boolean playFile(File file) {
        videoPane.setVisible(true);
        bottomBarPlay.setVisible(true);
        bottomBarClip.setVisible(false);
        embeddedMediaPlayer.controls().stop();
        embeddedMediaPlayer.media().play(file.getAbsolutePath());
        loadedMedia = Optional.of(file);
        // TODO figure out bad file, gripe and return false;
        return true;
    }

    @FXML
    public void doAddVideoFile(ActionEvent event) {
        List<File> chosen = lazyFileChooser.get().showOpenMultipleDialog(stage);
        if (chosen != null) {
            files.getItems().addAll(chosen);
            lazyFileChooser.get().setInitialDirectory(chosen.get(chosen.size() - 1).getParentFile());
            projectDirty.setValue(true);
        }
    }

    @FXML
    public void doLoadProject(ActionEvent event) {
        if (maybeSaveDirtyProject(stage,
                "Save Changes",
                "Save existing changes before loading new project?")) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files",
                            "*.json"));
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                embeddedMediaPlayer.controls().stop();
                videoPane.setVisible(false);
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
                    List<Clip> loadedClips = mapper.readValue(content, new TypeReference<>() { });
                    for (Clip clip : loadedClips) {
                        fileToClips.computeIfAbsent(clip.file, x -> new ArrayList<>()).add(clip);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                    showAlert("Unexpected Error", "Error loading project: " + ex.getMessage());
                }
                projectFile.setValue(file);
                projectDirty.setValue(false);
                closeProject.setDisable(false);
            }
        }
    }

    @FXML
    public void doCloseProject(ActionEvent event) {
        if (!maybeSaveDirtyProject(stage, "Confirm Project Close", "Save project changes before closing?")) {
            event.consume();
        }

        embeddedMediaPlayer.controls().stop();

        videoPane.setVisible(false);

        files.getItems().clear();
        clips.getItems().clear();

        projectDirty.setValue(false);
        projectFile.setValue(null);
    }

    @FXML
    public void doSaveProject() {
        if (projectFile.getValue() != null) {
            saveProjectFile(projectFile.getValue());
        } else {
            doSaveProjectAs();
        }
    }

    @FXML
    public void doSaveProjectAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files",
                        "*.json"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            if (!file.getName().contains(".")) {
                file = new File(file.getParent(), file.getName() + ".json");
            }

            saveProjectFile(file);
        }
    }

    @FXML
    public void doGenerateVideo(ActionEvent event) {
        selectClipsDialog(stage)
                .ifPresent(clips -> generateVideo(stage, clips));
    }

    @FXML
    public void doSkipBackward(ActionEvent event) {
        long time = embeddedMediaPlayer.status().time();
        embeddedMediaPlayer.controls().setTime(Math.max(time - SKIP_TIME_MILLIS, 0));
    }

    @FXML
    public void doPlayPause(ActionEvent event) {
        if (playPause.getGraphic().equals(PAUSE_ICON)) {
            embeddedMediaPlayer.controls().pause();
        } else {
            embeddedMediaPlayer.controls().play();
            embeddedMediaPlayer.audio().setVolume(LibVlcConst.MAX_VOLUME / 2);
        }
    }

    @FXML
    public void doSkipForward(ActionEvent event) {
        long time = embeddedMediaPlayer.status().time();
        embeddedMediaPlayer.controls().setTime(Math.min(time + SKIP_TIME_MILLIS, embeddedMediaPlayer.status().length()));
    }

    @FXML
    public void doClipPlayStop(ActionEvent event) {
        if (clipPlayStop.getGraphic().equals(STOP_ICON)) {
            embeddedMediaPlayer.controls().pause();
        } else {
            embeddedMediaPlayer.controls().setTime((long) rangeSlider.getLowValue() * 1000);
            embeddedMediaPlayer.controls().play();
        }
    }

    @FXML
    public void doAddClip(ActionEvent event) {
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
    public void doDescribeClip(ActionEvent event) {
        Dialog<ClipDescriptionData> dialog = createStyledDialog(((Node) event.getSource()).getScene());
        dialog.setTitle("Describe Clip");
        dialog.setHeaderText("Enter description for this clip:");

        VBox dialogRoot = new VBox();
        TextArea descriptionText = new TextArea(clipDescription.map(ClipDescriptionData::getDescription).orElse(""));
        dialogRoot.getChildren().add(descriptionText);
        ComboBox<Integer> ratingCombo = new ComboBox<>();
        ratingCombo.getItems().addAll(1, 2, 3, 4, 5);
        dialogRoot.getChildren().add(new HBox(new Label("Rating:"), ratingCombo));

        ratingCombo.setValue(clipDescription.map(ClipDescriptionData::getRating).orElse(null));

        dialog.getDialogPane().setContent(dialogRoot);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new ClipDescriptionData(
                        descriptionText.getText(),
                        ratingCombo.getValue()
                );
            } else {
                return null;
            }
        });

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ClipDescriptionData> result = dialog.showAndWait();
        result.ifPresent(value -> {
            clipDescription = Optional.of(value);
            clipDirty.setValue(true);
        });
    }

    @FXML
    public void doCancelClip(ActionEvent event) {
        if (clipDirty.get()) {
            if (saveOrDiscardClip(event,"Do wish save the clip before exiting clip editor?") == ButtonType.CANCEL) {
                return;
            }
        }
        bottomBarPlay.setVisible(true);
        bottomBarClip.setVisible(false);

        playSlider.setValue(embeddedMediaPlayer.status().time() / 1000.0);
        resetViewport();
    }

    @FXML
    public void doSaveClip(Event event) {
        clipDirty.setValue(false);
        projectDirty.setValue(true);
        var newClip = new Clip(
                loadedMedia.get(),
                clipDescription.map(ClipDescriptionData::getDescription).orElse(null),
                clipDescription.map(ClipDescriptionData::getRating).orElse(null),
                rangeSlider.getMin() * 1000.0,
                rangeSlider.getLowValue() * 1000.0,
                rangeSlider.getHighValue() * 1000.0,
                rangeSlider.getMax() * 1000.0,
                videoImageView.getViewport()
        );
        List<Clip> items = fileToClips.get(newClip.file);
        editingClip.ifPresentOrElse(clip -> {
            int idx = items.indexOf(clip);
            items.remove(idx);
            items.add(idx, newClip);

        },() -> clips.getItems().add(newClip));

        clips.getItems().clear();
        clips.getItems().addAll(items);

        editingClip = Optional.of(newClip);
    }

    @FXML
    public void doClipSetStart(ActionEvent event) {
        clipDirty.setValue(true);
        rangeSlider.setLowValue(embeddedMediaPlayer.status().time() / 1000.0);
    }

    @FXML
    public void doClipSetStop(ActionEvent event) {
        clipDirty.setValue(true);
        rangeSlider.setHighValue(embeddedMediaPlayer.status().time() / 1000.0);
    }

    @FXML
    public void doClipZoomOut(ActionEvent event) {
        double minValue = rangeSlider.getMin();
        double maxValue = rangeSlider.getMax();
        double lowValue = rangeSlider.getLowValue();
        double highValue = rangeSlider.getHighValue();
        double fullRange = maxValue - minValue;
        double rangeRange = highValue - lowValue;
        double midPoint = lowValue + rangeRange / 2;
        double newRange = fullRange * 1.25;
        double newMin = Math.max(0.0, midPoint - newRange / 2);
        rangeSlider.setMin(newMin);
        rangeSlider.setMax(Math.min(playSlider.getMax(), newMin + newRange));
    }

    @FXML
    public void doClipZoomIn(ActionEvent event) {
        double minValue = rangeSlider.getMin();
        double maxValue = rangeSlider.getMax();
        double lowValue = rangeSlider.getLowValue();
        double highValue = rangeSlider.getHighValue();
        double fullRange = maxValue - minValue;
        double rangeRange = highValue - lowValue;
        if (fullRange > 1) {
            double midPoint = lowValue + rangeRange / 2;
            double newRange = fullRange / 1.25;
            double newMin = Math.max(midPoint - newRange / 2, 0.0);
            rangeSlider.setMin(newMin);
            rangeSlider.setMax(newMin + newRange);
        }
    }

    @FXML
    public void doKeyPressedFiles(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            projectDirty.setValue(true);
            List<File> selectedItems = files.getSelectionModel().getSelectedItems();
            loadedMedia.ifPresent( file -> {
                if (selectedItems.contains(file)) {
                    embeddedMediaPlayer.controls().stop();
                    videoPane.setVisible(false);
                    loadedMedia = Optional.empty();
                    clips.getItems().clear();
                }
            });
            files.getItems().removeAll(selectedItems);
        }
    }
    @FXML
    public void doKeyPressedClips(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            for (File file : files.getSelectionModel().getSelectedItems()) {
                projectDirty.setValue(true);
                List<Clip> fileClips = fileToClips.get(file);
                for (Clip clip : clips.getSelectionModel().getSelectedItems()) {
                    if (editingClip.filter(clip::equals).isEmpty()) {
                        fileClips.remove(clip);
                        clips.getItems().remove(clip);
                    } else {
                        showAlert("Alert", "Can't delete current editing clip");
                    }
                }
            }
        }
    }

    @FXML
    public void doSortFiles(ActionEvent event) {
        projectDirty.setValue(true);
        files.getItems().sort(Comparator.comparing(File::getName));
    }

    @FXML
    public void doSortClips(ActionEvent event) {
        projectDirty.setValue(true);
        clips.getItems().sort(Comparator.comparing(Clip::label));
    }

    private ButtonType saveOrDiscardClip(Event event, String contentText) {
        Dialog<ButtonType> dialog = createStyledDialog(((Node) event.getSource()).getScene());
        dialog.setTitle("Discard Clip Changes?");
        dialog.setContentText(contentText);
        dialog.getDialogPane().getButtonTypes().addAll(
                Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
        );
        var choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.YES) {
            doSaveClip(event);
        }
        return choice;
    }

    private void initializeMediaPlayer() {
        embeddedMediaPlayer.videoSurface().set(videoSurfaceForImageView(this.videoImageView));
        embeddedMediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void mediaPlayerReady(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    if (isPlayerMode()) {
                        videoHeight = mediaPlayer.video().videoDimension().getHeight();
                        videoWidth = mediaPlayer.video().videoDimension().getWidth();
                        resetViewport();
                    }
                    // If we exit clip mode, it is already set.
                    playSlider.setMax(mediaPlayer.status().length() / 1000.0);
                });
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
                if (isPlayerMode()) {
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
    }

    private void initializeVideoImageView() {
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
                    // don't scale, so we're zoomed in to fewer than MIN_PIXELS in any direction:
                    Math.min(MIN_PIXELS / viewport.getWidth(), MIN_PIXELS / viewport.getHeight()),

                    // don't scale so that we're bigger than image dimensions:
                    Math.max(videoWidth / viewport.getWidth(), videoHeight / viewport.getHeight())
            );

            Point2D mouse = imageViewToImage(videoImageView, new Point2D(e.getX(), e.getY()));

            double newWidth = viewport.getWidth() * scale;
            double newHeight = viewport.getHeight() * scale;

            double newMinX = clamp(mouse.getX() - (mouse.getX() - viewport.getMinX()) * scale,
                    0, videoWidth - newWidth);
            double newMinY = clamp(mouse.getY() - (mouse.getY() - viewport.getMinY()) * scale,
                    0, videoHeight - newHeight);
            if (newMinX != viewport.getMinX()
                    || newMinY != viewport.getMinY()
                    || newWidth != viewport.getWidth()
                    || newHeight != viewport.getHeight()) {
                clipDirty.setValue(true);
            }

            videoImageView.setViewport(new Rectangle2D(newMinX, newMinY, newWidth, newHeight));
        });
    }

    private void initializeFilesTable() {
        files.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        files.setCellFactory(listView -> {
            var listCell = new ListCell<File>() {
                private final Tooltip tooltip = new Tooltip();

                @Override
                public void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty);
                    setTooltip(tooltip);
                    if (empty) {
                        setText(null);
                        tooltip.setText(null);
                    } else if (item != null) {
                        setText(item.getName());
                        tooltip.setText(item.getName());
                    }
                }
            };

            listCell.setOnMouseClicked(event -> {
                clips.getItems().clear();
                clips.getItems().addAll(fileToClips.getOrDefault(listCell.getItem(), Collections.emptyList()));
                if (event.getClickCount() > 1) {
                    if (!playFile(listCell.getItem())) {
                        files.getItems().remove(listCell.getItem());
                    }
                }
            });
            return listCell;
        });
    }

    private void initializeClipsTable() {
        clips.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        clips.getColumns().addAll(
                createClipNameColumn(row -> new SimpleStringProperty(row.getValue().label())),
                createClipRatingColumn(row -> new SimpleObjectProperty<>(row.getValue().rating)));
        clips.setRowFactory(listView -> {
            var tableRow = new TableRow<Clip>() {
                private final Tooltip tooltip = new Tooltip();

                @Override
                protected void updateItem(Clip clip, boolean empty) {
                    super.updateItem(clip, empty);
                    setTooltip(tooltip);
                    tooltip.setText(clip != null ? clip.label() : null);
                }
            };

            tableRow.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    Clip clickedItem = tableRow.getItem();
                    if (clickedItem != null) {
                        if (!isPlayerMode() && clipDirty.get() && editingClip
                                .map(item -> clickedItem != null && clickedItem != item).orElse(false)) {
                            if (saveOrDiscardClip(event, "Do wish save the clip before switching to another?") != ButtonType.CANCEL) {
                                loadClip(clickedItem);
                            }
                        } else {
                            loadClip(clickedItem);
                        }
                    }
                }
            });
            return tableRow;
        });
        clips.getColumns().get(0).prefWidthProperty().bind(clips.widthProperty().subtract(50));
    }

    public void start(Stage stage) {
        this.stage = stage;

        stage.getIcons()
                .add(new Image(this.getClass().getResourceAsStream( "/icon48.png" )));

        initializeFilesTable();
        initializeClipsTable();
        initializeMediaPlayer();
        initializeVideoImageView();

        playSlider.valueProperty().addListener((observableValue, number, newValue) -> {
            if (playSlider.isValueChanging()) {
                embeddedMediaPlayer.controls().setTime(newValue.longValue() * 1000);
            }
        });

        projectDirty.addListener(
            (observableValue, wasDirty, nowDirty) -> {
                saveProject.setDisable(!nowDirty);
                saveProjectAs.setDisable(!nowDirty);
                closeProject.setDisable(projectFile.get() == null && !nowDirty);
            });

        saveClip.disableProperty().bind(clipDirty.not());

        files.getSelectionModel().getSelectedItems()
                .addListener((ListChangeListener<File>) change -> generateVideo.setDisable(change.getList().size() == 0));

        stage.setOnCloseRequest(windowEvent -> {
            if (!maybeSaveDirtyProject(stage, "Confirm Exit", "Save project changes before exit?")) {
                windowEvent.consume();
            }
        });

        ChangeListener<Boolean> dirtyClip = (observableValue, oldValue, newValue) -> clipDirty.setValue(true);

        rangeSlider.lowValueChangingProperty().addListener(dirtyClip);
        rangeSlider.highValueChangingProperty().addListener(dirtyClip);

        saveProject.setDisable(true);
        closeProject.setDisable(true);
        saveProjectAs.setDisable(true);
        generateVideo.setDisable(true);
    }

    private <T> TableColumn<T, String> createClipNameColumn(
        Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>> nameCellFactory
    ) {
        TableColumn<T, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(nameCellFactory);
        nameCol.setResizable(false);
        nameCol.setSortable(false);
        return nameCol;
    }

    private <T> TableColumn<T, Integer> createClipRatingColumn(
        Callback<TableColumn.CellDataFeatures<T, Integer>, ObservableValue<Integer>> ratingCellFactory
    ) {
        TableColumn<T, Integer> ratingCol = new TableColumn<>("\u2605");
        ratingCol.setCellValueFactory(ratingCellFactory);
        ratingCol.setMaxWidth(30);
        ratingCol.setMinWidth(30);
        ratingCol.setSortable(false);
        ratingCol.setStyle( "-fx-alignment: center;");
        ratingCol.setResizable(false);
        return ratingCol;
    }

    private boolean isPlayerMode() {
        return bottomBarPlay.isVisible();
    }

    private Optional<FileFFProbeData> startClipFFProbeMetaFile(File file) {
        try {
            return FFprobe.atPath()
                    .setShowStreams(true)
                    .setInput(file.getAbsolutePath())
                    .execute()
                    .getStreams().stream()
                    .filter(it -> it.getCodecType() == StreamType.VIDEO)
                    .findFirst()
                    .map(FileFFProbeData::new);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            showAlert("Error Processing File",
                String.format("Could not determine start time for video %s\n" +
                              "Clips within may having inaccuracies in starting time\n" +
                              "Error %s", file.getAbsolutePath(), ex.getMessage()));
        }
        return Optional.empty();
    }

    private void showAlert(String title, String contentText) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(contentText);
            alert.showAndWait();
        });
    }

    private boolean maybeSaveDirtyProject(Stage stage, String title, String text) {
        if (!projectDirty.get()) {
            return true;
        }
        Dialog<ButtonType> dialog = createStyledDialog(stage.getScene());
        dialog.setTitle(title);
        dialog.setContentText(text);
        dialog.getDialogPane().getButtonTypes().addAll(
                Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
        );
        ButtonType choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.CANCEL) {
            return false;
        }
        if (choice == ButtonType.YES) {
            var file = projectFile.getValue();
            if (file == null) {
                doSaveProjectAs();
            } else {
                doSaveProject();
            }
        }
        return true;
    }

    private Dialog<ButtonType> createGenerateDialog(Stage stage, Label videoLabel, ProgressBar progressBar) {
        Dialog<ButtonType> dialog = createStyledDialog(stage.getScene());
        dialog.setTitle("Generate Video");
        dialog.setHeaderText("Generating Video...");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        HBox hbox = new HBox();
        hbox.getChildren().add(videoLabel);
        hbox.getChildren().add(progressBar);
        dialog.getDialogPane().setContent(hbox);
        return dialog;
    }

    private String getFileExtension(File file) {
        String fileName = file.getName();
        int index = fileName.lastIndexOf(".");
        if (index >= 0 && index < fileName.length()) {
            return fileName.substring(index);
        }
        return null;
    }

    private <T> Dialog<T> createStyledDialog(Scene scene) {
        Dialog<T> dialog = new Dialog<>();
        dialog.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        return dialog;
    }

    private class SelectableData<T> {
        public boolean selected;
        public final T data;

        public SelectableData(boolean selected, T data) {
            this.selected = selected;
            this.data = data;
        }
    }

    private Optional<ClipGenerationData> selectClipsDialog(Stage stage) {
        Dialog<ClipGenerationData> dialog = createStyledDialog(stage.getScene());

        TableView<SelectableData<Clip>> clipsTable = new TableView<>();

        TableColumn<SelectableData<Clip>, Boolean> selectedCol = new TableColumn<>("");
        selectedCol.setCellFactory(column ->
             new TableCell<>() {
                final CheckBox check = new CheckBox();

                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        check.setSelected(item);
                        setGraphic(check);
                    }
                }
            }
        );
        selectedCol.setCellValueFactory(row -> new SimpleBooleanProperty(row.getValue().selected));
        selectedCol.setResizable(false);
        selectedCol.setSortable(false);
        selectedCol.setEditable(false);
        selectedCol.setPrefWidth(30);

        TableColumn<SelectableData<Clip>, String> fileCol =  new TableColumn<>("File");
        fileCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().data.file.getName()));

        TableColumn<SelectableData<Clip>, String> nameCol = createClipNameColumn(
                row -> new SimpleStringProperty(row.getValue().data.label()));
        TableColumn<SelectableData<Clip>, Integer> ratingCol = createClipRatingColumn(
                row -> new SimpleObjectProperty<>(row.getValue().data.rating));

        clipsTable.getColumns().addAll(selectedCol, fileCol, nameCol, ratingCol);

        clipsTable.getItems().addAll(files.getSelectionModel().getSelectedItems().stream()
            .flatMap(file -> fileToClips.getOrDefault(file, Collections.emptyList()).stream())
            .map(clip -> new SelectableData<>(true, clip))
            .collect(Collectors.toList())
        );
        clipsTable.setMinWidth(650);
        nameCol.prefWidthProperty().bind(clipsTable.widthProperty().subtract(fileCol.widthProperty()).subtract(80));
        clipsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        HBox selectOperationRow = new HBox(10);

        Button select = new Button("Select");
        Button deSelect = new Button("De-select");

        ComboBox<String> rating = new ComboBox<>();
        rating.getItems().addAll("all", "not rated", "1", "2", "3", "4", "5");
        rating.getSelectionModel().select(0);

        selectOperationRow.getChildren().addAll(select, deSelect, rating);

        EventHandler<ActionEvent> eventHandler = event -> {
            boolean selected = event.getSource() == select;
            boolean targeted = false;
            String ratingValue = rating.getValue();
            for (int i = 0, max = clipsTable.getItems().size(); i < max; i++) {
                SelectableData<Clip> row = clipsTable.getItems().get(i);
                switch (ratingValue) {
                    case "all": targeted = true; break;
                    case "not rated": targeted = row.data.rating == null; break;
                    default: targeted = row.data.rating != null && Integer.parseInt(ratingValue) == row.data.rating; break;
                }
                if (targeted) {
                    row.selected = selected;
                }
            }
            clipsTable.refresh();
        };

        select.setOnAction(eventHandler);
        deSelect.setOnAction(eventHandler);

        HBox outputFileRow = new HBox();

        TextField fileNameText = new TextField();
        fileNameText.setText("concatenated");
        fileNameText.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && fileNameText.getText().isBlank()) {
                fileNameText.setText("concatenated");
            }
        });
        ComboBox<String> preset = new ComboBox<>();

        preset.getItems().addAll("faster", "fast", "medium", "slow", "slower");
        preset.getSelectionModel().select("medium");

        outputFileRow.getChildren().addAll(new Label("Output File:"), fileNameText, new Label("Preset"), preset);
        HBox.setHgrow(fileNameText, Priority.ALWAYS);

        VBox vbox = new VBox();

        vbox.getChildren().addAll(
                clipsTable,
                selectOperationRow,
                new Separator(),
                outputFileRow
        );

        dialog.getDialogPane().setContent(vbox);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ClipGenerationData(
                    clipsTable.getItems().stream()
                            .filter(it -> it.selected)
                            .map(it -> it.data)
                            .collect(Collectors.toList()),
                    fileNameText.getText(),
                    preset.getValue()
                );
            } else {
                return null;
            }
        });
        clipsTable.requestFocus();

        return dialog.showAndWait();
    }

    private void generateVideo(Stage stage, ClipGenerationData clipGenerationData) {
        if (clipGenerationData.clips.isEmpty()) {
            showAlert("Error", "No Clips Selected");
            return;
        }

        String extension = getFileExtension(clipGenerationData.clips.get(0).file);

        if (extension == null) {
            showAlert("Error", "File extension can not be determined");
            return;
        }
        String concatenatedFileName = clipGenerationData.baseFileName + extension;

        File dir = new DirectoryChooser().showDialog(stage);
        File concatFile = new File(dir, "concat_files.txt");

        Label videoLabel = new Label();
        ProgressBar progressBar = new ProgressBar();
        videoLabel.setMinWidth(325.0);
        progressBar.setMinWidth(125.0);
        Dialog<ButtonType> dialog = createGenerateDialog(stage, videoLabel, progressBar);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);

        dialog.show();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                double totalFrameCount = 0;
                try {
                try (Writer concatWriter = new BufferedWriter(new FileWriter(concatFile))) {
                    Map<File, Optional<FileFFProbeData>> fileFFProbeDataMap = new HashMap<>();
                    int clipCount = 0;
                    int total = clipGenerationData.clips.size();
                    for (Clip clip : clipGenerationData.clips) {
                        if (!dialog.isShowing()) {
                            Platform.runLater(() -> dialog.setHeaderText("Cancelling..."));
                            return null;
                        }
                        String headerText = String.format("Generating Clip Video (%d of %d).", ++clipCount, total);
                        Platform.runLater(() -> {
                            dialog.setHeaderText(headerText);
                            videoLabel.setText(clip.itemRepresentation());
                            progressBar.setProgress(0.0);
                        });
                        Optional<FileFFProbeData> fileMeta = fileFFProbeDataMap.computeIfAbsent(clip.file, MainController.this::startClipFFProbeMetaFile);
                        float startTime = fileMeta.map(FileFFProbeData::getStartTime).orElse(0.0f);
                        double frameRate = fileMeta.map(FileFFProbeData::getAvgFrameRate).orElse(29.97);
                        double clipDurationSeconds = (clip.highValue - clip.lowValue) / 1000;
                        double estimatedFrameCount = clipDurationSeconds * frameRate;
                        totalFrameCount += estimatedFrameCount;
                        Rectangle2D viewPort = clip.viewportRect;
                        String outputFile = new File(dir, clip.itemRepresentation() + extension).getAbsolutePath();
                        FFmpeg.atPath()
                                .addInput(
                                        UrlInput.fromUrl(clip.file.getAbsolutePath())
                                                .setPosition(Math.max(0.0f, (clip.lowValue / 1000) - startTime), TimeUnit.SECONDS)
                                                .setDuration(clipDurationSeconds, TimeUnit.SECONDS)
                                )
                                .setProgressListener(progress -> fileMeta.ifPresent(fm ->
                                    Platform.runLater(() -> progressBar.setProgress((float) progress.getFrame() / estimatedFrameCount))))
                                .setFilter(StreamType.VIDEO,
                                        String.format("crop=%d:%d:%d:%d, scale=%d:%d, yadif",
                                                (int) viewPort.getWidth(),
                                                (int) viewPort.getHeight(),
                                                (int) viewPort.getMinX(),
                                                (int) viewPort.getMinY(),
                                                (int) videoWidth,
                                                (int) videoHeight))
                                .setOverwriteOutput(true)
                                .addArguments("-c:v", "libx264")
                                .addArguments("-preset", clipGenerationData.preset)
                                .addOutput(UrlOutput.toUrl(outputFile))
                                .execute();

                        concatWriter.append(String.format("file '%s'\n", outputFile));
                    }
                }

                Platform.runLater(() -> {
                    dialog.setHeaderText("Generating Final Concatenated Video");
                    videoLabel.setText(concatenatedFileName);
                    progressBar.setProgress(0.0);
                });

                final double finalTotalFrameCount = totalFrameCount;

                    FFmpeg fFmpeg = FFmpeg.atPath();
                    fFmpeg.addInput(
                            UrlInput.fromUrl(concatFile.getAbsolutePath())
                                    .addArguments("-f", "concat")
                                    .addArguments("-safe", "0")
                    );
                    fFmpeg.setProgressListener(progress ->
                        Platform.runLater(() -> progressBar.setProgress((float) progress.getFrame() / finalTotalFrameCount)));
                    fFmpeg.addArguments("-c", "copy");
                    fFmpeg.setOverwriteOutput(true);
                    fFmpeg.addOutput(UrlOutput.toUrl(new File(dir, concatenatedFileName).getAbsolutePath()));
                    fFmpeg.execute();
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                    showAlert("Error Generating Clips",
                            String.format("Error encountered generating clips: %s", ex.getMessage()));
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            progressBar.setProgress(1.0);
            dialog.setHeaderText("Video Generation Complete");
            dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
        });
        new Thread(task).start();
    }

    private void saveProjectFile(File file) {
        ObjectMapper mapper = new ObjectMapper();

        try (Writer writer = new BufferedWriter(new FileWriter(file))) {
            writer.append(mapper.writeValueAsString(files.getItems()));
            writer.append('\n');
            writer.append(mapper.writeValueAsString(fileToClips.values().stream().flatMap(List::stream).collect(Collectors.toList())));
            writer.append('\n');
            projectDirty.setValue(false);
            projectFile.setValue(file);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            showAlert("Unexpected Error", "Could not save file: " + ex.getMessage());
        }
    }

    private void loadClip(Clip clip) {
        embeddedMediaPlayer.controls().stop();
        playFile(clip.file);
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

        this.clipDescription = editingClip.map(clip -> new ClipDescriptionData(clip.description, clip.rating));

        this.editingClip = editingClip;
    }

    // reset to the top left:
    private void resetViewport() {
        videoImageView.setViewport(new Rectangle2D(0, 0, videoWidth, videoHeight));
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

    public static class ClipDescriptionData {
        public final String description;
        public final Integer rating;

        public ClipDescriptionData(String description, Integer rating) {
            this.description = description;
            this.rating = rating;
        }

        public String getDescription() {
            return description;
        }

        public Integer getRating() {
            return rating;
        }
    }

    public static class Clip {
        public final File file;
        public final String description;
        public final Integer rating;
        public final double minValue;
        public final double lowValue;
        public final double highValue;
        public final double maxValue;
        public final Rectangle2D viewportRect;

        @JsonCreator
        public Clip(
            @JsonProperty("file") File file,
            @JsonProperty("description") String description,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("minValue") double minValue,
            @JsonProperty("lowValue") double lowValue,
            @JsonProperty("highValue") double highValue,
            @JsonProperty("maxValue") double maxValue,
            @JsonProperty("viewportRect") Rectangle2D viewportRect) {
            this.file = file;
            this.description = description;
            this.rating = rating;
            this.minValue = minValue;
            this.lowValue = lowValue;

            this.highValue = highValue;
            this.maxValue = maxValue;
            this.viewportRect = viewportRect;
        }

        public String label() {
            String unpadded = String.valueOf((long) (lowValue * 1000));
            return ("000000000000".substring(unpadded.length()) + unpadded).substring(0, 12);
        }

        public String itemRepresentation() {
            return file.getName().replaceFirst("[.][^.]+$", "") +
                    "_" + label();
        }
    }

    public static class FileFFProbeData {
        private final float startTime;
        private final double avgFrameRate;

        public FileFFProbeData(Stream stream) {
            this.startTime = stream.getStartTime();
            this.avgFrameRate = stream.getAvgFrameRate().doubleValue();
        }

        public float getStartTime() {
            return startTime;
        }

        public double getAvgFrameRate() {
            return avgFrameRate;
        }
    }

    public static class RectangleDeserializer extends StdDeserializer<Rectangle2D> {
        public RectangleDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Rectangle2D deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            return new Rectangle2D(
                    node.get("minX").doubleValue(),
                    node.get("minY").doubleValue(),
                    node.get("width").doubleValue(),
                    node.get("height").doubleValue()
            );
        }
    }

    public static class ClipGenerationData {
        public final List<Clip> clips;
        public final String baseFileName;
        public final String preset;

        public ClipGenerationData(List<Clip> clips, String baseFileName, String preset) {
            this.clips = clips;
            this.baseFileName = baseFileName;
            this.preset = preset;
        }
    }
}
