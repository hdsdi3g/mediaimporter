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
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Application;
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
	// private final ConfigurationStore configurationStore;

	public MainApp() {
		super();
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());
		driveProbe = DriveProbe.get();
		toolRunner = new ToolRunner(new ExecutableFinder(), 1);
		/*configurationStore =*/ new ConfigurationStore("mediaimporter", sourcesList, destsList);
		currentCopyEngine = new SimpleObjectProperty<>();
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

		primaryStage.setScene(scene);
		primaryStage.show();

		primaryStage.setOnCloseRequest(event -> {
			event.consume();
			if (currentCopyEngine.isBound()) {
				currentCopyEngine.get().asyncStop(() -> {
				});
			}
			primaryStage.close();
		});
		mainPanel.getBtnQuit().setOnAction(event -> {
			event.consume();
			log.debug("Want to close");
			if (currentCopyEngine.isBound()) {
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
		destsList.forEach(DestinationEntry::updateSlotsContent);

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

	/*private class UpdateFileEntryStatusTask extends Task<Void> {
		private final FileEntry fileEntry;
		private final DestinationEntry destination;
	
		UpdateFileEntryStatusTask(final FileEntry fileEntry, final DestinationEntry destination) {
			super();
			this.fileEntry = fileEntry;
			this.destination = destination;
		}
	
		@Override
		protected Void call() throws Exception {
			// Auto-generated method stub
			// destination.
			return null;
		}*/
	/*final Task task = new Task<ObservableList<String>>() {
	@Override
	protected ObservableList<String> call() throws InterruptedException {
		updateMessage("Finding friends . . .");
		for (int i = 0; i < 10; i++) {
			Thread.sleep(200);
			updateProgress(i + 1, 10);
		}
		updateMessage("Finished.");
		return FXCollections.observableArrayList("John", "Jim", "Geoff", "Jill", "Suki");
	}
	// @Override protected void done() {
	// super.done();
	// System.out.println("This is bad, do not do this, this thread " + Thread.currentThread() + " is not the FXApplication thread.");
	// runButton.setText("Voila!");
	// }
	};
	statusLabel.textProperty().bind(task.messageProperty());
	runButton.disableProperty().bind(task.runningProperty());
	peopleView.itemsProperty().bind(task.valueProperty());
	progressBar.progressProperty().bind(task.progressProperty());
	task.stateProperty().addListener(new ChangeListener<Worker.State>() {
	  @Override public void changed(ObservableValue<? extends Worker.State> observableValue, Worker.State oldState, Worker.State newState) {
	    if (newState == Worker.State.SUCCEEDED) {
	      System.out.println("This is ok, this thread " + Thread.currentThread() + " is the JavaFX Application thread.");
	      runButton.setText("Voila!");
	    }
	  }
	});
	
	new Thread(task).start();}	*/

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
				final String label = String.format(messages.getString("labelProgressReady"), stats.getCount(), FileUtils.byteCountToDisplaySize(stats.getSum()));
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
			event.consume(); // TODO StartCopy
			if (currentCopyEngine.isBound()) {
				log4javaFx.error("Can't create a new copy operation");
				return;
			}

			log.info("Prepare and start copy operation"); // TODO with more informations

			currentCopyEngine.set(new CopyFilesEngine(fileList));
			final CompletableFuture<?> allRunTasks = currentCopyEngine.get().asyncStart();

			// TODO watch allRunTasks

			mainPanel.getBtnAddSourceDir().setDisable(true);
			mainPanel.getBtnRemoveSourceDir().setDisable(true);
			mainPanel.getBtnAddDestinationDir().setDisable(true);
			mainPanel.getBtnRemoveDestinationDir().setDisable(true);
			mainPanel.getBtnAddSourceToScan().setDisable(true);
			mainPanel.getBtnStartCopy().setDisable(true);
			mainPanel.getBtnStopCopy().setDisable(false);
		});
		mainPanel.getBtnStopCopy().setOnAction(event -> {
			event.consume(); // TODO StopCopy
			log.debug("");

		});
	}

}
