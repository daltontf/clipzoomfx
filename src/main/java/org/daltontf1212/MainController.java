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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
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
import uk.co.caprica.vlcj.player.base.LibVlcConst;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurfaceFactory.videoSurfaceForImageView;

public class MainController {
    private double videoWidth = 1920.0;
    private double videoHeight = 1080.0;

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

    @FXML private MenuItem addVideo;
    @FXML private MenuItem loadProject;
    @FXML private MenuItem closeProject;
    @FXML private MenuItem saveProject;
    @FXML private MenuItem saveProjectAs;
    @FXML private MenuItem generateVideo;
    //@FXML private MenuItem renderScript;
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

    private final SimpleBooleanProperty projectDirty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty clipDirty = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<File> projectFile = new SimpleObjectProperty<>(null);

    private Optional<File> loadedMedia = Optional.empty();
    private Optional<Clip> editingClip = Optional.empty();
    private Optional<ClipDescriptionData> clipDescription = Optional.empty();

    public MainController() {
        this.mediaPlayerFactory = new MediaPlayerFactory();
        this.embeddedMediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
    }

    public boolean load(File file) {
        bottomBarPlay.setVisible(true);
        bottomBarClip.setVisible(false);
        embeddedMediaPlayer.controls().stop();
        embeddedMediaPlayer.media().play(file.getAbsolutePath());
        loadedMedia = Optional.of(file);
        // TODO figure out bad file, gripe and return false;
        return true;
    }

    @FXML
    public void skipBackward(ActionEvent event) {
        long time = embeddedMediaPlayer.status().time();
        embeddedMediaPlayer.controls().setTime(Math.max(time - 10000, 0));
    }

    @FXML
    public void playPause(ActionEvent event) {
        if (playPause.getGraphic().equals(PAUSE_ICON)) {
            embeddedMediaPlayer.controls().pause();
        } else {
            embeddedMediaPlayer.controls().play();
            embeddedMediaPlayer.audio().setVolume(LibVlcConst.MAX_VOLUME / 2);
        }
    }

    @FXML
    public void skipForward(ActionEvent event) {
        long time = embeddedMediaPlayer.status().time();
        embeddedMediaPlayer.controls().setTime(Math.min(time + 10000, embeddedMediaPlayer.status().length()));
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
    public void describeClip(ActionEvent event) {
        Dialog<ClipDescriptionData> dialog = new Dialog<>();
        dialog.setTitle("Describe Clip");
        dialog.setHeaderText("Enter description for this clip:");

        VBox dialogRoot = new VBox();
        TextArea descriptionText = new TextArea(clipDescription.map(ClipDescriptionData::getDescription).orElse(""));
        dialogRoot.getChildren().add(descriptionText);
        ComboBox<Integer> ratingCombo = new ComboBox<>();
        ratingCombo.getItems().add(1);
        ratingCombo.getItems().add(2);
        ratingCombo.getItems().add(3);
        ratingCombo.getItems().add(4);
        ratingCombo.getItems().add(5);
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
    public void cancelClip(ActionEvent event) {
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

    private ButtonType saveOrDiscardClip(Event event, String contentText) {
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Discard Clip Changes?");
        dialog.setContentText(contentText);
        dialog.getDialogPane().getButtonTypes().addAll(
                Arrays.asList(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
        );
        var choice = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.YES) {
            saveClip(event);
        }
        return choice;
    }

    @FXML
    public void saveClip(Event event) {
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
    public void clipZoomOut(ActionEvent event) {
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
    public void clipZoomIn(ActionEvent event) {
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

        files.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
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
                if (event.getClickCount() > 1) {
                    if (!load(listCell.getItem())) {
                        files.getItems().remove(listCell.getItem());
                    }
                }
            });
            return listCell;
        });

        clips.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<Clip,String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().itemRepresentation()));
        nameCol.setResizable(true);
        nameCol.setSortable(false);
        TableColumn<Clip,Integer> ratingCol = new TableColumn<>("\u2605");
        ratingCol.setCellValueFactory(row -> new SimpleObjectProperty<>(row.getValue().rating));
        ratingCol.setMaxWidth(30);
        ratingCol.setMinWidth(30);
        ratingCol.setSortable(false);
        ratingCol.setStyle( "-fx-alignment: center;");
        clips.getColumns().addAll(nameCol, ratingCol);
        clips.setRowFactory(listView -> {
            var tableRow = new TableRow<Clip>() {
                private final Tooltip tooltip = new Tooltip();

                @Override
                protected void updateItem(Clip clip, boolean b) {
                    super.updateItem(clip, b);
                    setTooltip(tooltip);
                    tooltip.setText(clip != null ? clip.itemRepresentation() : null);
                }
            };

            tableRow.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    Clip clickedItem = tableRow.getItem();
                    if (!isPlayerMode() && clipDirty.get() && editingClip
                            .map(item -> clickedItem != null && clickedItem != item).orElse(false)) {
                        if (saveOrDiscardClip(event,"Do wish save the clip before switching to another?" ) != ButtonType.CANCEL) {
                            loadClip(clickedItem);
                        }
                    } else {
                        loadClip(clickedItem);
                    }
                }
            });
            return tableRow;
        });
        nameCol.prefWidthProperty().bind(clips.widthProperty().subtract(50));

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

        addVideo.setOnAction(event -> {
            List<File> chosen = lazyFileChooser.get().showOpenMultipleDialog(stage);
            if (chosen != null) {
                files.getItems().addAll(chosen);
                lazyFileChooser.get().setInitialDirectory(chosen.get(chosen.size() - 1).getParentFile());
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
                    showAlert("Unexpected Error", "Error loading project: " + ex.getMessage());
                }
                projectDirty.setValue(false);
                projectFile.setValue(file);
            }
        });

        closeProject.setOnAction(event -> {
            embeddedMediaPlayer.controls().stop();

            files.getItems().clear();
            clips.getItems().clear();

            bottomBarPlay.setVisible(true);
            bottomBarClip.setVisible(false);

            projectDirty.setValue(false);
            projectFile.setValue(null);
        });

        saveProject.setOnAction(event -> {
            saveProject();
        });

        projectDirty.addListener(
            (observableValue, wasDirty, nowDirty) -> {
                saveProject.setDisable(!nowDirty);
                saveProjectAs.setDisable(!nowDirty);
                closeProject.setDisable(projectFile.get() == null && !nowDirty);
            });
        saveProject.setDisable(true);
        closeProject.setDisable(true);

        saveProjectAs.setOnAction(event -> {
            saveProjectAs(stage);
        });
        saveProjectAs.setDisable(true);

        generateVideo.setOnAction(event -> generateVideo(stage));
        //renderScript.setOnAction(event -> renderScript(stage));

        generateVideo.setDisable(true);
        //renderScript.setDisable(true);

        clips.getItems().addListener((ListChangeListener<Clip>) change -> {
            boolean clipCountIsZero = clips.getItems().size() == 0;
            generateVideo.setDisable(clipCountIsZero);
            //renderScript.setDisable(clipCountIsZero);
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

        ChangeListener<Boolean> dirtyClip = (observableValue, oldValue, newValue) -> clipDirty.setValue(true);

        rangeSlider.lowValueChangingProperty().addListener(dirtyClip);
        rangeSlider.highValueChangingProperty().addListener(dirtyClip);
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

    private Dialog<ButtonType> createGenerateDialog(Label videoLabel, ProgressBar progressBar) {
        Dialog<ButtonType> dialog = new Dialog<>();
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

    private void generateVideo(Stage stage) {
        if (clips.getSelectionModel().getSelectedItems().isEmpty()) {
            showAlert("Error", "No Clips Selected");
            return;
        }

        String extension = getFileExtension(clips.getSelectionModel().getSelectedItems().get(0).file);

        if (extension == null) {
            showAlert("Error", "File extension can not be determined");
            return;
        }
        String concatenatedFileName = "concatenated" + extension;

        File dir = new DirectoryChooser().showDialog(stage);
        File concatFile = new File(dir, "concat_files.txt");

        Label videoLabel = new Label();
        ProgressBar progressBar = new ProgressBar();
        videoLabel.setMinWidth(325.0);
        progressBar.setMinWidth(125.0);
        Dialog<ButtonType> dialog = createGenerateDialog(videoLabel, progressBar);
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
                    int total = clips.getSelectionModel().getSelectedItems().size();
                    for (Clip clip : clips.getSelectionModel().getSelectedItems()) {
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
                                .setProgressListener(progress -> {
                                    fileMeta.ifPresent(fm -> {
                                        Platform.runLater(() -> progressBar.setProgress((float) progress.getFrame() / estimatedFrameCount));
                                    });
                                })
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
                                .addArguments("-preset", "slow")
                                .addOutput(
                                        UrlOutput.toUrl(outputFile)
                                )
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

                FFmpeg.atPath()
                        .addInput(
                                UrlInput.fromUrl(concatFile.getAbsolutePath())
                                        .addArguments("-f", "concat")
                                        .addArguments("-safe", "0")
                        )
                        .setProgressListener(progress -> {
                            Platform.runLater(() -> progressBar.setProgress((float) progress.getFrame() / finalTotalFrameCount));
                        })
                        .addArguments("-c", "copy")
                        .setOverwriteOutput(true)
                        .addOutput(UrlOutput.toUrl(new File(dir, concatenatedFileName).getAbsolutePath()))
                        .execute();
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

//    private void renderScript(Stage stage) {
//        File dir = new DirectoryChooser().showDialog(stage);
//        try (Writer runWriter = new BufferedWriter(new FileWriter(new File(dir, "run.sh")));
//             Writer concatWriter = new BufferedWriter(new FileWriter(new File(dir, "concat_files.txt")))
//        ) {
//            Map<File, Optional<FileFFProbeData>> fileFFProbeDataMap = new HashMap<>();
//            for (Clip clip : clips.getSelectionModel().getSelectedItems()) {
//                Optional<FileFFProbeData> fileMeta = fileFFProbeDataMap.computeIfAbsent(clip.file, MainController.this::startClipFFProbeMetaFile);
//                float startTime = fileMeta.map(FileFFProbeData::getStartTime).orElse(0.0f);
//                Rectangle2D viewPort = clip.viewportRect;
//                runWriter.append(String.format("ffmpeg -n -ss %.2f -i \"%s\" -vf \"crop=%d:%d:%d:%d, scale=%d:%d\" -t %.2f %s.mp4\n",
//                        Math.max(0.0f, (clip.lowValue / 1000) - startTime),
//                        clip.file,
//                        (int) viewPort.getWidth(),
//                        (int) viewPort.getHeight(),
//                        (int) viewPort.getMinX(),
//                        (int) viewPort.getMinY(),
//                        (int) videoWidth,
//                        (int) videoHeight,
//                        (clip.highValue - clip.lowValue) / 1000,
//                        clip.itemRepresentation()
//                ));
//
//                concatWriter.append(String.format("file %s.mp4\n", clip.itemRepresentation()));
//            }
//
//            runWriter.append("ffmpeg -f concat -i concat_files.txt -c copy concatenated.mp4\n");
//        } catch (IOException ex) {
//            ex.printStackTrace(System.err);
//        }
//    }

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
            showAlert("Unexpected Error", "Could not save file: " + ex.getMessage());
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
            if (!file.getName().contains(".")) {
                file = new File(file.getParent(), file.getName() + ".json");
            }

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

        public String itemRepresentation() {
            String unpadded = String.valueOf((long) (lowValue * 1000));
            return file.getName().replaceFirst("[.][^.]+$", "") +
                    "_" + ("000000000000".substring(unpadded.length()) + unpadded).substring(0, 12);
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
}
