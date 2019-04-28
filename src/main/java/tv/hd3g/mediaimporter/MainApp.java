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
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

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

public class MainApp extends Application {
	private static Logger log = LogManager.getLogger();

	private final ObservableList<SourceEntry> sourcesList;
	private final ObservableList<DestinationEntry> destsList;
	private final ObservableList<FileEntry> fileList;
	// private final ConfigurationStore configurationStore;

	public MainApp() {
		super();
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());

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
		initStatusZone();
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
				    // TODO warn: http://blog.pikodat.com/2015/10/11/frontend-logging-with-javafx/
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
				    // TODO warn !
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
		mainPanel.getTableFiles().setItems(fileList);
		mainPanel.getTableFilesColSource();
		mainPanel.getTableFilesColPath();
		mainPanel.getTableFilesColSize();
		mainPanel.getTableFilesColStatus();
		// TODO
		mainPanel.getBtnAddSourceToScan().setOnAction(event -> {
			event.consume();
			log.debug("");

		});
		mainPanel.getBtnClearScanlist().setOnAction(event -> {
			event.consume();
			log.debug("");

		});
	}

	private void initStatusZone() {
		// TODO
		mainPanel.getProgressBar();
		mainPanel.getLblProgressionCounter();
		mainPanel.getLblEta();
		mainPanel.getLblSpeedCopy();
	}

	private void initActionZone() {
		// TODO
		mainPanel.getBtnStartCopy().setOnAction(event -> {
			event.consume();
			log.debug("");

		});
		mainPanel.getBtnStopCopy().setOnAction(event -> {
			event.consume();
			log.debug("");

		});
	}

}
