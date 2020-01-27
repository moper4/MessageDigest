package dzy.javafx.app.hash.fxml;

import dzy.security.ChecksumProvider;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;

public class RootPane extends VBox implements Initializable, AutoCloseable {
    private static final String CONFIG_PATH;

    static {
        Security.addProvider(new ChecksumProvider());
        Security.addProvider(new BouncyCastleProvider());
        CONFIG_PATH = System.getProperty("java.io.tmpdir") + File.separator + "config.ser";
    }

    private final FileChooser chooser = new FileChooser();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private ExtensionFilter txtFilter;
    private ExtensionFilter anyFilter;
    private Task<?> currentTask;

    public MenuBar menuBar;
    public Menu algorithmMenu;
    public MenuItem openMenu;
    public MenuItem saveAsMenu;
    public MenuItem exitMenu;
    public TextArea textArea;
    public Button openBtn;
    public Button clearBtn;
    public Button copyBtn;
    public Button saveBtn;
    public Button stopBtn;
    public ProgressBar fileTaskBar;
    public ProgressBar totalTaskBar;

    public RootPane() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("RootPane.fxml"));
        loader.setResources(ResourceBundle.getBundle("dzy/javafx/app/hash/fxml/RootPane"));
        loader.setRoot(this);
        loader.setController(this);
        loader.load();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadAlgorithmMenus();

        txtFilter = new ExtensionFilter(resources.getString("text"), "*.txt");
        anyFilter = new ExtensionFilter(resources.getString("anyFile"), "*.*");
        chooser.getExtensionFilters().addAll(txtFilter, anyFilter);

        openMenu.setOnAction(e -> open());
        exitMenu.setOnAction(e -> Platform.exit());
        saveAsMenu.setOnAction(e -> save());
        openBtn.setOnAction(e -> open());
        clearBtn.setOnAction(e -> textArea.clear());
        copyBtn.setOnAction(e -> textArea.copy());
        saveBtn.setOnAction(e -> save());
        stopBtn.setOnAction(e -> Optional.ofNullable(currentTask).ifPresent(Task::cancel));

        textArea.setOnDragOver(e -> {
            Dragboard dragboard = e.getDragboard();
            if (dragboard.hasFiles()) e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            e.consume();
        });

        textArea.setOnDragDropped(e -> {
            Dragboard dragboard = e.getDragboard();
            if (dragboard.hasFiles()) doTask(dragboard.getFiles());
            e.consume();
        });

        textArea.sceneProperty().addListener((s, os, ns) -> {
            if (ns != null) ns.windowProperty().addListener((w, ow, nw) -> {
                if (nw != null) ((Stage) nw).setTitle(resources.getString("title"));
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void loadAlgorithmMenus() {
        List<String> selectedAlgorithms;
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(Path.of(CONFIG_PATH)))) {
            selectedAlgorithms = (List<String>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            selectedAlgorithms = List.of("CRC32", "MD5", "SHA");
        }

        Map<String, TreeSet<String>> listMap = Security.getAlgorithms("MessageDigest").stream()
                .filter(Pattern.compile("^(OID\\.)?[\\d|.]+").asPredicate().negate())
                .filter(alg -> !alg.equals("SHA-1"))//去除重复
                .collect(
                        groupingBy(
                                it -> it.split("(?<=\\p{Alpha}{1,10}+)")[0],
                                TreeMap::new,
                                toCollection(TreeSet::new)
                        )
                );

        List<String> lst = selectedAlgorithms;
        ObservableList<MenuItem> items = algorithmMenu.getItems();
        for (Map.Entry<String, TreeSet<String>> entry : listMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                Menu subMenu = new Menu(entry.getKey());
                for (String alg : entry.getValue()) {
                    CheckMenuItem item = new CheckMenuItem(alg);
                    item.setSelected(lst.contains(item.getText()));
                    subMenu.getItems().add(item);
                }
                items.add(subMenu);
            } else {
                CheckMenuItem item = new CheckMenuItem(entry.getValue().first());
                item.setSelected(lst.contains(item.getText()));
                items.add(item);
            }
        }
    }

    private List<String> getSelectAlgorithms() {
        return algorithmMenu.getItems().stream()
                .flatMap(menuItem -> menuItem instanceof Menu ? ((Menu) menuItem).getItems().stream() : Stream.of(menuItem))
                .map(CheckMenuItem.class::cast)
                .filter(CheckMenuItem::isSelected)
                .map(CheckMenuItem::getText)
                .collect(Collectors.toList());
    }

    private void open() {
        chooser.setSelectedExtensionFilter(anyFilter);
        List<File> files = chooser.showOpenMultipleDialog(menuBar.getScene().getWindow());
        if (files == null) return;
        chooser.setInitialDirectory(files.get(0).getParentFile());
        doTask(files);
    }

    private void save() {
        chooser.setSelectedExtensionFilter(txtFilter);
        File file = chooser.showSaveDialog(menuBar.getScene().getWindow());
        if (file == null) return;
        chooser.setInitialDirectory(file.getParentFile());

        try {
            Files.writeString(file.toPath(), textArea.getText(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void doTask(List<File> files) {
        try {
            TotalTask task = new TotalTask(files, getSelectAlgorithms());
            this.currentTask = task;
            pool.submit(task);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void close() throws Exception {
        pool.shutdown();
        Optional.ofNullable(currentTask).ifPresent(Task::cancel);
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Path.of(CONFIG_PATH)))) {
            out.writeObject(getSelectAlgorithms());
        }
    }

    private class TotalTask extends Task<Void> {
        private final List<FileTask> tasks = new ArrayList<>();

        TotalTask(List<File> files, List<String> algorithms) throws IOException, NoSuchAlgorithmException {
            for (File file : files) tasks.add(new FileTask(file, algorithms));

            openMenu.disableProperty().bind(stateProperty().isEqualTo(State.RUNNING));
            openBtn.disableProperty().bind(stateProperty().isEqualTo(State.RUNNING));
            textArea.disableProperty().bind(stateProperty().isEqualTo(State.RUNNING));
            totalTaskBar.progressProperty().bind(progressProperty());

            setOnCancelled(e -> {
                tasks.forEach(Task::cancel);
                totalTaskBar.progressProperty().unbind();
                totalTaskBar.setProgress(0);
            });
        }

        @Override
        protected Void call() {
            Platform.runLater(() -> stopBtn.setDisable(false));
            updateProgress(0, tasks.size());
            EventHandler<WorkerStateEvent> cancelAction = e -> {
                fileTaskBar.progressProperty().unbind();
                fileTaskBar.setProgress(0);
            };

            for (int i = 0, n = tasks.size(); i < n && !isCancelled(); i++) {
                FileTask task = tasks.get(i);

                Platform.runLater(() -> {
                    fileTaskBar.progressProperty().bind(task.progressProperty());
                    task.setOnSucceeded(e -> textArea.appendText(task.getValue()));
                    task.setOnFailed(cancelAction);
                    task.setOnCancelled(cancelAction);
                });

                task.run();
                updateProgress(i + 1, n);
            }

            Platform.runLater(() -> stopBtn.setDisable(true));
            return null;
        }
    }
}
