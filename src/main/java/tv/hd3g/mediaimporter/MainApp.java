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
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tv.hd3g.mediaimporter.driveprobe.DriveProbe;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class MainApp extends Application {
	private static Logger log = LogManager.getLogger();

	public static final Logger log4javaFx = LogManager.getLogger("javafx");

	private final ObservableList<SourceEntry> sourcesList;
	private final ObservableList<DestinationEntry> destsList;
	private final ObservableList<FileEntry> fileList;
	private final DriveProbe driveProbe;
	private final ToolRunner toolRunner;
	// private final ConfigurationStore configurationStore;

	public MainApp() {
		super();
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());
		driveProbe = DriveProbe.get();
		toolRunner = new ToolRunner(new ExecutableFinder(), 1);
		/*configurationStore =*/ new ConfigurationStore("mediaimporter", sourcesList, destsList);
	}

	private MainPanel mainPanel;
	private Stage stage;
	private ResourceBundle messages;

	// TODO java task example https://gist.github.com/jewelsea/2774481

	@Override
	public void start(final Stage primaryStage) throws Exception {
		log.info("Start JavaFX GUI Interface");

		final FXMLLoader d = new FXMLLoader();

		messages = ResourceBundle.getBundle(getClass().getPackage().getName() + ".messages");
		d.setResources(Objects.requireNonNull(messages, "\"messages\" can't to be null"));
		final BorderPane root = (BorderPane) d.load(getClass().getResource(MainPanel.class.getSimpleName() + ".fxml").openStream());

		mainPanel = d.getController();

		final Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource(MainPanel.class.getSimpleName() + ".css").toExternalForm());
		// stage.getIcons().add(new Image(getClass().getResourceAsStream("tasks.png")));
		// image_tasks = new Image(getClass().getResourceAsStream("tasks.png"), 10, 10, false, false);

		primaryStage.setScene(scene);
		primaryStage.show();

		primaryStage.setOnCloseRequest(event -> {
			event.consume();
			// TODO do "stop"
			primaryStage.close();
		});
		mainPanel.getBtnQuit().setOnAction(event -> {
			event.consume();
			log.debug("Want to close");
			// TODO do "stop"
			primaryStage.close();
		});
		stage = primaryStage;

		initSourceZone();
		initDestZone();
		initFileZone();
		initActionZone();
	}

	@Override
	public void stop() {
		log.info("JavaFX GUI Interface is stopped");
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
		} else {
			label.setText(FileUtils.byteCountToDisplaySize(value.longValue()));
		}
	}

	private void initDestZone() {
		mainPanel.getTableDestinations().setItems(destsList);
		mainPanel.getTableDestinationsColPath().setCellValueFactory(DestinationEntry.getColPathFactory());
		mainPanel.getTableDestinationsColAvailable().setCellValueFactory(DestinationEntry.getColAvailableFactory());
		mainPanel.getTableDestinationsColSpeed().setCellValueFactory(DestinationEntry.getColAvailableSpeed());
		mainPanel.getTableDestinationsColSlots().setCellValueFactory(DestinationEntry.getColAvailableSlots());

		mainPanel.getTableDestinationsColAvailable().setCellFactory(col -> new TableCell<>() {
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
					destsList.add(toAdd);
				}
			});
		});
		mainPanel.getBtnRemoveDestinationDir().setOnAction(event -> {
			event.consume();
			final DestinationEntry selected = mainPanel.getTableDestinations().getSelectionModel().getSelectedItem();
			if (selected != null) {
				log.debug("Remove dest dir: " + selected);
				destsList.remove(selected);
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
			log.info("Start scan source dirs");
			// TODO async
			mainPanel.getBtnClearScanlist().setDisable(true);
			try {
				final Map<File, String> lastProbeResult = driveProbe.getSNByMountedDrive(toolRunner);
				sourcesList.forEach(entry -> {
					try {
						entry.scanSource(fileList, lastProbeResult);
					} catch (final IOException e) {
						MainApp.log4javaFx.error("Can't scan " + entry, e);
					}
				});
				mainPanel.getBtnClearScanlist().setDisable(fileList.isEmpty());
			} catch (InterruptedException | ExecutionException e1) {
				MainApp.log4javaFx.error("Can't get S/N connected drives ", e1);
			}
		});
		mainPanel.getBtnClearScanlist().setOnAction(event -> {
			event.consume();
			log.info("Clear scan list");
			fileList.clear();
		});
	}

	private void initActionZone() {
		mainPanel.getBtnStartCopy().setOnAction(event -> {
			event.consume(); // TODO StartCopy
			log.debug("");

			mainPanel.getBtnAddSourceDir().setDisable(true);
			mainPanel.getBtnRemoveSourceDir().setDisable(true);
			mainPanel.getBtnAddDestinationDir().setDisable(true);
			mainPanel.getBtnRemoveDestinationDir().setDisable(true);
			mainPanel.getBtnAddSourceToScan().setDisable(true);
			mainPanel.getBtnStopCopy().setDisable(false);
			mainPanel.getBtnQuit().setDisable(true);
		});
		mainPanel.getBtnStopCopy().setOnAction(event -> {
			event.consume(); // TODO StopCopy
			log.debug("");

		});
	}

}
