/*
 * This file is part of mediaimporter.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * Copyright (C) hdsdi3g for hd3g.tv 2019
 *
*/
package tv.hd3g.mediaimporter;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tv.hd3g.mediaimporter.io.CopyFilesEngine;
import tv.hd3g.mediaimporter.tools.DriveProbe;
import tv.hd3g.mediaimporter.tools.NavigateTo;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class MainApp extends Application {
	private static Logger log = LogManager.getLogger();

	public static final Logger log4javaFx = LogManager.getLogger("javafx");

	final static ResourceBundle messages;

	static {
		messages = ResourceBundle.getBundle(MainApp.class.getPackage().getName() + ".messages");
	}

	private final ObservableList<SourceEntry> sourcesList;
	private final ObservableList<DestinationEntry> destsList;
	private final ObservableList<FileEntry> fileList;
	private final DriveProbe driveProbe;
	private final ToolRunner toolRunner;
	private final SimpleObjectProperty<CopyFilesEngine> currentCopyEngine;

	public MainApp() {
		super();
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());
		driveProbe = DriveProbe.get();
		toolRunner = new ToolRunner(new ExecutableFinder(), 1);
		currentCopyEngine = new SimpleObjectProperty<>(null);
		driveProbe.getSNByMountedDrive(toolRunner);
	}

	private MainPanel mainPanel;
	private Stage stage;

	@Override
	public void start(final Stage primaryStage) throws Exception {
		log.info("Start JavaFX GUI Interface");

		final FXMLLoader d = new FXMLLoader();

		d.setResources(Objects.requireNonNull(messages, "\"messages\" can't to be null"));
		final BorderPane root = (BorderPane) d.load(getClass().getResource(MainPanel.class.getSimpleName() + ".fxml").openStream());

		mainPanel = d.getController();

		final Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource(MainPanel.class.getSimpleName() + ".css").toExternalForm());
		// stage.getIcons().add(new Image(getClass().getResourceAsStream("tasks.png")));
		// image_tasks = new Image(getClass().getResourceAsStream("tasks.png"), 10, 10, false, false);

		new ConfigurationStore("mediaimporter", sourcesList, destsList, mainPanel.getInputPrefixDirName());

		primaryStage.setScene(scene);
		primaryStage.setTitle("Media importer");
		primaryStage.show();

		primaryStage.setOnCloseRequest(event -> {
			event.consume();
			if (currentCopyEngine.isNotNull().get()) {
				currentCopyEngine.get().asyncStop(() -> {
				});
			}
			primaryStage.close();
		});
		mainPanel.getBtnQuit().setOnAction(event -> {
			event.consume();
			log.debug("Want to close");
			if (currentCopyEngine.isNotNull().get()) {
				currentCopyEngine.get().asyncStop(() -> {
				});
			}
			primaryStage.close();
		});
		stage = primaryStage;

		initSourceZone();
		initDestZone();
		initFileZone();
		initActionZone();
		initTablesRowFactory();
	}

	private void initTablesRowFactory() {
		mainPanel.getTableSources().setRowFactory(tv -> {
			final TableRow<SourceEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					NavigateTo.get().navigateTo(row.getItem().rootPath, toolRunner);
				}
			});
			return row;
		});

		mainPanel.getTableDestinations().setRowFactory(tv -> {
			final TableRow<DestinationEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					NavigateTo.get().navigateTo(row.getItem().rootPath, toolRunner);
				}
			});
			return row;
		});

		mainPanel.getTableFiles().setRowFactory(tv -> {
			final TableRow<FileEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					NavigateTo.get().navigateTo(row.getItem().getFile(), toolRunner);
				}
			});
			return row;
		});
	}

	@Override
	public void stop() {
		log.info("JavaFX GUI Interface is stopped");
		System.exit(0);
	}

	private Optional<File> selectDirectory(final String titleTextKey) {
		final DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle(messages.getString(titleTextKey));
		return Optional.ofNullable(directoryChooser.showDialog(stage));
	}

	private void initSourceZone() {
		mainPanel.getTableSources().setItems(sourcesList);
		mainPanel.getTableSourcesColPath().setCellValueFactory(SourceEntry.getColPathFactory());

		mainPanel.getBtnAddSourceDir().setOnAction(event -> {
			event.consume();
			selectDirectory("addSourceDirectory").ifPresent(file -> {
				final SourceEntry toAdd = new SourceEntry(file);
				if (sourcesList.contains(toAdd) == false) {
					log.info("Add new source directory: " + file);
					sourcesList.add(toAdd);
				}
			});
		});
		sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().stream().filter(BaseSourceDestEntry.isStoredOn(sourcesList, destsList)).forEach(entry -> {
					MainApp.log4javaFx.warn(messages.getString("dontAllowDirs") + ": " + entry);
					sourcesList.remove(entry);
				});
			}
		});
		mainPanel.getBtnRemoveSourceDir().setOnAction(event -> {
			event.consume();
			final SourceEntry selected = mainPanel.getTableSources().getSelectionModel().getSelectedItem();
			if (selected != null) {
				log.debug("Remove source dir: " + selected);
				sourcesList.remove(selected);
			}
		});
		mainPanel.getTableSources().getSelectionModel().selectedItemProperty().addListener((observable_value, old_value, new_value) -> {
			mainPanel.getBtnRemoveSourceDir().setDisable(new_value == null);
		});
	}

	private static void roundSizeValues(final Number value, final boolean empty, final Labeled label) {
		if (empty) {
			label.setText(null);
		} else if (value.longValue() == 0l) {
			label.setText(null);
		} else {
			label.setText(byteCountToDisplaySizeWithPrecision(value.longValue()));
		}
	}

	private static final DecimalFormat decimalFormat1digits = new DecimalFormat("###,###.#");
	private static final DecimalFormat decimalFormat2digits = new DecimalFormat("###,###.##");

	public static String byteCountToDisplaySizeWithPrecision(final long bytes) {
		if (bytes == 0) {
			return "0 Bytes";
		}

		final long k = 1024;
		final String[] sizes = new String[] { "Bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB" };

		final long i = Math.round(Math.floor(Math.log(bytes) / Math.log(k)));
		final double value = bytes / Math.pow(k, i);

		if (value > 100d) {
			return String.valueOf(Math.round(value)) + " " + sizes[(int) i];
		} else if (value > 10d) {
			return decimalFormat1digits.format(value) + " " + sizes[(int) i];
		} else {
			return decimalFormat2digits.format(value) + " " + sizes[(int) i];
		}
	}

	private void initDestZone() {
		mainPanel.getTableDestinations().setItems(destsList);
		mainPanel.getTableDestinationsColPath().setCellValueFactory(DestinationEntry.getColPathFactory());
		mainPanel.getTableDestinationsColAvailable().setCellValueFactory(DestinationEntry.getColAvailableFactory());
		mainPanel.getTableDestinationsColSpeed().setCellValueFactory(DestinationEntry.getColAvailableSpeed());
		mainPanel.getTableDestinationsColSlots().setCellValueFactory(DestinationEntry.getColAvailableSlots());
		destsList.forEach(DestinationEntry::updateSlotsContent);

		mainPanel.getTableDestinationsColAvailable().setCellFactory(col -> new TableCell<>() {
			public void updateItem(final Number value, final boolean empty) {
				super.updateItem(value, empty);
				roundSizeValues(value, empty, this);
			}
		});
		mainPanel.getTableDestinationsColSpeed().setCellFactory(col -> new TableCell<>() {
			public void updateItem(final Number value, final boolean empty) {
				super.updateItem(value, empty);
				roundSizeValues(value, empty, this);
			}
		});
		destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().stream().filter(BaseSourceDestEntry.isStoredOn(sourcesList, destsList)).forEach(entry -> {
					MainApp.log4javaFx.warn(messages.getString("dontAllowDirs") + ": " + entry);
					destsList.remove(entry);
				});
			}
		});
		mainPanel.getBtnAddDestinationDir().setOnAction(event -> {
			event.consume();
			selectDirectory("addDestDirectory").ifPresent(file -> {
				final DestinationEntry toAdd = new DestinationEntry(file);
				if (destsList.contains(toAdd) == false) {
					log.info("Add new dest directory: " + file);
					toAdd.updateSlotsContent();
					destsList.add(toAdd);
					fileList.forEach(fileEntry -> {
						fileEntry.addDestination(toAdd);
					});
				}
			});
		});
		mainPanel.getBtnRemoveDestinationDir().setOnAction(event -> {
			event.consume();
			final DestinationEntry selected = mainPanel.getTableDestinations().getSelectionModel().getSelectedItem();
			if (selected != null) {
				log.debug("Remove dest dir: " + selected);
				destsList.remove(selected);

				log.debug("Update all actual references: " + selected);
				fileList.forEach(fileEntry -> {
					fileEntry.removeDestination(selected);
				});
			}
		});
		mainPanel.getTableDestinations().getSelectionModel().selectedItemProperty().addListener((observable_value, old_value, new_value) -> {
			mainPanel.getBtnRemoveDestinationDir().setDisable(new_value == null);
		});
	}

	private void initFileZone() {
		mainPanel.getBtnAddSourceToScan().setDisable(sourcesList.isEmpty() | destsList.isEmpty());

		sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
			while (change.next()) {
				if (change.wasRemoved() | change.wasAdded()) {
					mainPanel.getBtnAddSourceToScan().setDisable(sourcesList.isEmpty() | destsList.isEmpty());
				}
			}
		});
		destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
			while (change.next()) {
				if (change.wasRemoved() | change.wasAdded()) {
					mainPanel.getBtnAddSourceToScan().setDisable(sourcesList.isEmpty() | destsList.isEmpty());
				}
			}
		});

		mainPanel.getTableFiles().setItems(fileList);
		mainPanel.getTableFilesColSource().setCellValueFactory(FileEntry.getColSourceFactory());
		mainPanel.getTableFilesColDriveSN().setCellValueFactory(FileEntry.getColDriveSNFactory());
		mainPanel.getTableFilesColPath().setCellValueFactory(FileEntry.getColPathFactory());
		mainPanel.getTableFilesColSize().setCellValueFactory(FileEntry.getColSizeFactory());
		mainPanel.getTableFilesColStatus().setCellValueFactory(FileEntry.getColStatusFactory());

		mainPanel.getTableFilesColSize().setCellFactory(col -> new TableCell<>() {
			public void updateItem(final Number value, final boolean empty) {
				super.updateItem(value, empty);
				roundSizeValues(value, empty, this);
			}
		});

		mainPanel.getBtnAddSourceToScan().setOnAction(event -> {
			event.consume();
			if (sourcesList.isEmpty()) {
				return;
			}
			log.info("Start scan source dirs");
			mainPanel.getBtnClearScanlist().setDisable(true);

			final CompletableFuture<Map<File, String>> cfLastProbeResult = driveProbe.getSNByMountedDrive(toolRunner);

			fileList.removeIf(fileEntry -> {
				return fileEntry.updateState();
			});

			// TODO display error box if some files are in error ! + after operation. info box if ok
			// TODO icon

			// TODO Better async pshell on start, with retry
			// TODO Test media change

			sourcesList.forEach(entry -> {
				try {
					final SimpleStringProperty driveSN = new SimpleStringProperty();

					cfLastProbeResult.thenAccept(lastProbeResult -> {
						driveSN.set(lastProbeResult.getOrDefault(entry.rootPath.toPath().getRoot().toFile(), messages.getString("driveSNDefault")));
					});
					final List<FileEntry> newFilesEntries = entry.scanSource(fileList, driveSN, destsList);

					if (newFilesEntries.isEmpty() == false) {
						log.info("Found " + newFilesEntries.size() + " new file(s), start update copies references");
						destsList.forEach(destination -> {
							newFilesEntries.forEach(newFileEntry -> {
								newFileEntry.addDestination(destination);
							});
						});
					}
				} catch (final IOException e) {
					MainApp.log4javaFx.error("Can't scan " + entry, e);
				}
			});

			final LongSummaryStatistics stats = fileList.stream().filter(FileEntry.needsToBeCopied).mapToLong(fileEntry -> fileEntry.getFile().length()).summaryStatistics();
			if (stats.getCount() > 0) {
				final String label = String.format(messages.getString("labelProgressReady"), stats.getCount(), MainApp.byteCountToDisplaySizeWithPrecision(stats.getSum()));
				mainPanel.getLblProgressionCounter().setText(label);
				mainPanel.getBtnStartCopy().setDisable(false);
			}

			mainPanel.getBtnClearScanlist().setDisable(fileList.isEmpty());
		});
		mainPanel.getBtnClearScanlist().setOnAction(event -> {
			event.consume();
			log.info("Clear scan list");
			fileList.clear();
			mainPanel.getBtnStartCopy().setDisable(true);
			mainPanel.getBtnStopCopy().setDisable(true);
			mainPanel.getLblProgressionCounter().setText("");
		});
	}

	private void initActionZone() {
		mainPanel.getBtnStartCopy().setOnAction(event -> {
			event.consume();
			if (currentCopyEngine.isNotNull().get()) {
				log4javaFx.error("Can't create a new copy operation");
				return;
			}

			destsList.forEach(dest -> {
				dest.prepareNewSessionSlot(mainPanel.getInputPrefixDirName().getText());
			});

			log.info("Prepare and start copy operation");

			mainPanel.getBtnAddSourceDir().setDisable(true);
			mainPanel.getBtnRemoveSourceDir().setDisable(true);
			mainPanel.getBtnAddDestinationDir().setDisable(true);
			mainPanel.getBtnRemoveDestinationDir().setDisable(true);
			mainPanel.getBtnAddSourceToScan().setDisable(true);
			mainPanel.getInputPrefixDirName().setDisable(true);
			mainPanel.getBtnStartCopy().setDisable(true);
			mainPanel.getBtnStopCopy().setDisable(false);
			mainPanel.getProgressBar().setProgress(-1);

			try {
				final CopyFilesEngine copyFilesEngine = new CopyFilesEngine(fileList, destsList, this);
				currentCopyEngine.set(copyFilesEngine);
				copyFilesEngine.asyncStart().thenRunAsync(() -> {
					Platform.runLater(() -> {
						resetStatesAfterCopyOperation();
					});
				});
			} catch (final Exception e) {
				log4javaFx.error("Can't process copy operation", e);
				resetStatesAfterCopyOperation();
			}
		});
		mainPanel.getBtnStopCopy().setOnAction(event -> {
			event.consume();
			if (currentCopyEngine.isNull().get()) {
				return;
			}
			log.info("Manual stop copy action");

			currentCopyEngine.get().asyncStop(() -> {
				Platform.runLater(() -> {
					resetStatesAfterCopyOperation();
				});
			});
		});
	}

	public void updateProgress(final double progressRate, final int filesCopied, final int totalFiles, final long datasCopiedBytes, final long totalDatasBytes, final long etaMsec, final long meanCopySpeedBytesPerSec, final long instantCopySpeedBytesPerSec) {
		mainPanel.getProgressBar().setProgress(progressRate);

		final String counter = String.format(messages.getString("labelProgressProcess"), filesCopied + 1, totalFiles, MainApp.byteCountToDisplaySizeWithPrecision(datasCopiedBytes), MainApp.byteCountToDisplaySizeWithPrecision(totalDatasBytes));
		mainPanel.getLblProgressionCounter().setText(counter);

		mainPanel.getLblEta().setText("ETA: " + DurationFormatUtils.formatDuration(etaMsec, "HH:mm:ss"));

		final String speedCopy = String.format(messages.getString("labelProgressSpeed"), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(meanCopySpeedBytesPerSec)), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(instantCopySpeedBytesPerSec)));
		mainPanel.getLblSpeedCopy().setText(speedCopy);
	}

	private void resetStatesAfterCopyOperation() {
		currentCopyEngine.setValue(null);
		mainPanel.getBtnAddSourceDir().setDisable(false);
		mainPanel.getBtnRemoveSourceDir().setDisable(false);
		mainPanel.getBtnAddDestinationDir().setDisable(false);
		mainPanel.getBtnRemoveDestinationDir().setDisable(false);
		mainPanel.getBtnAddSourceToScan().setDisable(false);
		mainPanel.getInputPrefixDirName().setDisable(false);
		mainPanel.getBtnStartCopy().setDisable(false);
		mainPanel.getBtnStopCopy().setDisable(true);

		mainPanel.getProgressBar().setProgress(0);
		mainPanel.getLblEta().setText("");
		mainPanel.getLblSpeedCopy().setText("");
	}

}
