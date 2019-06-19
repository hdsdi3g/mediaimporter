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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import tv.hd3g.mediaimporter.io.CanBeStopped;
import tv.hd3g.mediaimporter.io.CopyFilesEngine;
import tv.hd3g.mediaimporter.io.IntegrityCheckEngine;
import tv.hd3g.mediaimporter.tools.ConfigurationStore;
import tv.hd3g.mediaimporter.tools.DriveProbe;
import tv.hd3g.mediaimporter.tools.FileSanity;
import tv.hd3g.mediaimporter.tools.NavigateTo;
import tv.hd3g.mediaimporter.ui.TableCellFileSize;
import tv.hd3g.mediaimporter.ui.TableContextMenu;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class MainApp extends Application {
	private static Logger log = LogManager.getLogger();
	public static final Logger log4javaFx = LogManager.getLogger("javafx");

	public final static ResourceBundle messages;

	static {
		messages = ResourceBundle.getBundle(MainApp.class.getPackage().getName() + ".messages");
	}
	private static final DecimalFormat decimalFormat1digits = new DecimalFormat("###,###.#");
	private static final DecimalFormat decimalFormat2digits = new DecimalFormat("###,###.##");
	private static final BiFunction<Map<File, String>, SourceEntry, String> driveSNFromProbeResult = (probeResult, entry) -> probeResult.getOrDefault(entry.rootPath.toPath().getRoot().toFile(), messages.getString("driveSNDefault"));

	private final ObservableList<SourceEntry> sourcesList;
	private final ObservableList<DestinationEntry> destsList;
	private final ObservableList<FileEntry> fileList;
	private final ToolRunner toolRunner;
	private final NavigateTo navigateTo;
	private final SimpleObjectProperty<CanBeStopped> currentCopyEngine;
	private final FileSanity fileSanity;
	private final ConcurrentHashMap<File, Long> digestByFileCache;

	private final SimpleObjectProperty<Map<File, String>> lastSNDrivesProbeResult;
	private final ScheduledExecutorService driveSNUpdaterRegularExecutor;
	private ScheduledFuture<?> driveSNUpdaterRegularFuture;

	public MainApp() {
		super();
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());
		fileSanity = FileSanity.get();
		navigateTo = NavigateTo.get();
		digestByFileCache = new ConcurrentHashMap<>();
		toolRunner = new ToolRunner(new ExecutableFinder(), 2);
		currentCopyEngine = new SimpleObjectProperty<>(null);
		lastSNDrivesProbeResult = new SimpleObjectProperty<>();
		driveSNUpdaterRegularExecutor = Executors.newScheduledThreadPool(1);
		driveSNUpdaterRegularFuture = null;
		updateSNDrives();
	}

	private void updateSNDrives() {
		if (driveSNUpdaterRegularFuture != null) {
			if (driveSNUpdaterRegularFuture.isCancelled() == false && driveSNUpdaterRegularFuture.isDone() == false) {
				return;
			}
		}

		driveSNUpdaterRegularFuture = driveSNUpdaterRegularExecutor.scheduleAtFixedRate(() -> {
			try {
				final Map<File, String> probeResult = DriveProbe.get().getSNByMountedDrive(toolRunner).get(DriveProbe.timeLimitSec + 1, TimeUnit.SECONDS);
				log.info("Found S/N for drives: {}", probeResult.toString());
				driveSNUpdaterRegularFuture.cancel(false);
				Platform.runLater(() -> {
					lastSNDrivesProbeResult.set(probeResult);
				});
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				log.warn("Can't get drives S/N, but it will be a retry", e);
			}
		}, 0, DriveProbe.timeLimitSec / 2, TimeUnit.SECONDS);
	}

	private MainPanel mainPanel;
	private Stage stage;
	private Image appIcon;
	private ConfigurationStore store;

	@Override
	public void start(final Stage primaryStage) {
		stage = primaryStage;
		log.info("Start JavaFX GUI Interface");
		try {

			final FXMLLoader d = new FXMLLoader();

			d.setResources(Objects.requireNonNull(messages, "\"messages\" can't to be null"));
			final BorderPane root = (BorderPane) d.load(getClass().getResource(MainPanel.class.getSimpleName() + ".fxml").openStream());

			mainPanel = d.getController();

			final Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource(MainPanel.class.getSimpleName() + ".css").toExternalForm());
			appIcon = new Image(getClass().getResourceAsStream("memory-card.png"));
			stage.getIcons().add(appIcon);
			// image_tasks = new Image(getClass().getResourceAsStream("tasks.png"), 10, 10, false, false);

			store = new ConfigurationStore("mediaimporter", sourcesList, destsList, mainPanel.getInputPrefixDirName(), fileSanity, digestByFileCache);

			/**
			 * Get last preferencies and set to controls
			 * @see close() for save values
			 */
			final Consumer<TableColumn<? extends TargetedFileEntries, ?>> setColWidthFromConfig = c -> {
				if (c.getId() == null) {
					return;
				}
				store.getConfigDoubleValue(c.getId() + ".width").ifPresent(c::setPrefWidth);
			};
			mainPanel.getTableSources().getColumns().forEach(setColWidthFromConfig);
			mainPanel.getTableDestinations().getColumns().forEach(setColWidthFromConfig);
			mainPanel.getTableFiles().getColumns().forEach(setColWidthFromConfig);

			store.getConfigDoubleValue("primaryStage.width").ifPresent(stage::setWidth);
			store.getConfigDoubleValue("primaryStage.height").ifPresent(stage::setHeight);
			store.getConfigDoubleValue("primaryStage.x").ifPresent(stage::setX);
			store.getConfigDoubleValue("primaryStage.y").ifPresent(stage::setY);
			store.getConfigValue("CBCheckAfterCopy").map(Boolean::parseBoolean).or(() -> Optional.of(true)).ifPresent(mainPanel.getCBCheckAfterCopy()::setSelected);

			stage.setScene(scene);
			stage.setTitle(System.getProperty("javappackager.appname", "Media importer"));
			stage.show();

			stage.setOnCloseRequest(event -> {
				event.consume();
				if (currentCopyEngine.isNotNull().get()) {
					currentCopyEngine.get().asyncStop(() -> {
					});
				}
				stage.close();
			});
			mainPanel.getBtnQuit().setOnAction(event -> {
				event.consume();
				log.debug("Want to close");
				if (currentCopyEngine.isNotNull().get()) {
					currentCopyEngine.get().asyncStop(() -> {
					});
				}
				stage.close();
			});

			/**
			 * initSourceZone
			 */
			final Consumer<File> onAddNewSourceDir = file -> {
				final SourceEntry toAdd = new SourceEntry(file, fileSanity, digestByFileCache);
				if (sourcesList.contains(toAdd) == false) {
					log.info("Add new source directory: " + file);
					sourcesList.add(toAdd);
				}
			};

			mainPanel.getTableSources().setItems(sourcesList);
			mainPanel.getTableSources().setPlaceholder(createPlaceholder(messages.getString("tableSourcePlaceholder")));
			setFolderDragAndDrop(mainPanel.getTableSources(), mainPanel.getTableSources().getItems()::isEmpty, onAddNewSourceDir);
			mainPanel.getTableSourcesColPath().setCellValueFactory(SourceEntry.getColPathFactory());
			mainPanel.getTableSourcesColDrive().setCellValueFactory(BaseSourceDestEntry.getColDriveFactory());
			mainPanel.getTableSourcesColType().setCellValueFactory(BaseSourceDestEntry.getColTypeFactory());

			mainPanel.getBtnAddSourceDir().setOnAction(event -> {
				event.consume();
				selectDirectory("addSourceDirectory").ifPresent(onAddNewSourceDir);
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

			/**
			 * initDestZone
			 */
			final Consumer<File> onAddNewDestDir = file -> {
				final DestinationEntry toAdd = new DestinationEntry(file);
				if (destsList.contains(toAdd) == false) {
					log.info("Add new dest directory: " + file);
					toAdd.updateSlotsContent();
					destsList.add(toAdd);
					fileList.forEach(fileEntry -> {
						fileEntry.addDestination(toAdd);
					});
				}
			};

			mainPanel.getTableDestinations().setItems(destsList);
			mainPanel.getTableDestinations().setPlaceholder(createPlaceholder(messages.getString("tableDestPlaceholder")));
			setFolderDragAndDrop(mainPanel.getTableDestinations(), mainPanel.getTableDestinations().getItems()::isEmpty, onAddNewDestDir);
			mainPanel.getTableDestinationsColPath().setCellValueFactory(DestinationEntry.getColPathFactory());
			mainPanel.getTableDestinationsColAvailable().setCellValueFactory(DestinationEntry.getColAvailableFactory());
			mainPanel.getTableDestinationsColSpeed().setCellValueFactory(DestinationEntry.getColAvailableSpeed());
			mainPanel.getTableDestinationsColSlots().setCellValueFactory(DestinationEntry.getColAvailableSlots());
			mainPanel.getTableDestinationsColDrive().setCellValueFactory(BaseSourceDestEntry.getColDriveFactory());
			mainPanel.getTableDestinationsColType().setCellValueFactory(BaseSourceDestEntry.getColTypeFactory());

			destsList.forEach(DestinationEntry::updateSlotsContent);

			mainPanel.getTableDestinationsColAvailable().setCellFactory(col -> new TableCellFileSize<>());
			mainPanel.getTableDestinationsColSpeed().setCellFactory(col -> new TableCellFileSize<>("/sec"));

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
				selectDirectory("addDestDirectory").ifPresent(onAddNewDestDir);
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

			/**
			 * initFileZone
			 */
			final Supplier<Boolean> isBtnAddSourceToScanDisabled = () -> {
				return sourcesList.isEmpty() | destsList.isEmpty() | lastSNDrivesProbeResult.isNull().get();
			};

			mainPanel.getBtnAddSourceToScan().setDisable(isBtnAddSourceToScanDisabled.get());

			sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
				while (change.next()) {
					if (change.wasRemoved() | change.wasAdded()) {
						mainPanel.getBtnAddSourceToScan().setDisable(isBtnAddSourceToScanDisabled.get());
					}
				}
			});
			destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
				while (change.next()) {
					if (change.wasRemoved() | change.wasAdded()) {
						mainPanel.getBtnAddSourceToScan().setDisable(isBtnAddSourceToScanDisabled.get());
					}
				}
			});
			lastSNDrivesProbeResult.addListener((observable, oldValue, newValue) -> {
				mainPanel.getBtnAddSourceToScan().setDisable(isBtnAddSourceToScanDisabled.get());
			});

			mainPanel.getTableFiles().setItems(fileList);
			mainPanel.getTableFiles().setPlaceholder(createPlaceholder(messages.getString("tableFilePlaceholder")));
			mainPanel.getTableFilesColSource().setCellValueFactory(FileEntry.getColSourceFactory());
			mainPanel.getTableFilesColDriveSN().setCellValueFactory(FileEntry.getColDriveSNFactory());
			mainPanel.getTableFilesColPath().setCellValueFactory(FileEntry.getColPathFactory());
			mainPanel.getTableFilesColSize().setCellValueFactory(FileEntry.getColSizeFactory());
			mainPanel.getTableFilesColStatus().setCellValueFactory(FileEntry.getColStatusFactory());
			mainPanel.getTableFilesColSize().setCellFactory(col -> new TableCellFileSize<>());

			mainPanel.getBtnAddSourceToScan().setOnAction(event -> {
				event.consume();
				if (sourcesList.isEmpty()) {
					return;
				}
				log.info("Start scan source dirs");
				mainPanel.getBtnClearScanlist().setDisable(true);

				updateSNDrives();

				fileList.removeIf(fileEntry -> {
					return fileEntry.updateState();
				});

				digestByFileCache.clear();

				sourcesList.forEach(entry -> {
					final SimpleStringProperty driveSN = new SimpleStringProperty();
					if (lastSNDrivesProbeResult.isNotNull().get()) {
						driveSN.set(driveSNFromProbeResult.apply(lastSNDrivesProbeResult.get(), entry));
					} else {
						driveSN.set(driveSNFromProbeResult.apply(Collections.emptyMap(), entry));
					}
					lastSNDrivesProbeResult.addListener((observable, oldValue, newValue) -> {
						driveSN.set(driveSNFromProbeResult.apply(newValue, entry));
					});

					try {
						final List<FileEntry> newFilesEntries = entry.scanSource(fileList, driveSN, destsList);

						if (newFilesEntries.isEmpty() == false) {
							log.info("Found " + newFilesEntries.size() + " new file(s), start update copies references");
							destsList.forEach(destination -> {
								newFilesEntries.forEach(newFileEntry -> {
									newFileEntry.addDestination(destination);
								});
							});
						}

						fileList.sort((l, r) -> {
							return l.getFile().compareTo(r.getFile());
						});
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

				displayStatusMsgBox();
			});

			mainPanel.getBtnClearScanlist().setOnAction(event -> {
				event.consume();
				log.info("Clear scan list");
				fileList.clear();
				mainPanel.getBtnStartCopy().setDisable(true);
				mainPanel.getBtnStopCopy().setDisable(true);
				mainPanel.getLblProgressionCounter().setText("");
			});

			/**
			 * initActionZone
			 */
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

					copyFilesEngine.asyncStart().thenCompose(copiedList -> {
						final boolean checkAfterCopy = mainPanel.getCBCheckAfterCopy().isSelected();

						if (checkAfterCopy) {
							Platform.runLater(() -> {
								afterCopyBeforeCheckOperation();
							});
							CompletableFuture.runAsync(() -> {
								Platform.runLater(() -> {
									displayStatusMsgBox();
								});
							});

							fileList.stream().forEach(f -> {
								f.updateState();
							});

							final var onlyValidCopiedList = copiedList.stream().filter(c -> {
								return FileEntryStatus.ALL_COPIES_DONE.equals(c.getSourceEntry().getCurrentResumeStatus());
							}).collect(Collectors.toUnmodifiableList());

							final var ice = new IntegrityCheckEngine(onlyValidCopiedList);
							currentCopyEngine.set(ice);

							return ice.start(Runnable::run);
						} else {
							log.info("Skip file integrity check");
							return CompletableFuture.completedStage(null);
						}
					}).thenAccept(v -> {
						Platform.runLater(() -> {
							afterAllOperations();
						});
					});

					// TODO add hash listing
					// TODO add multiple hashs
				} catch (final Exception e) {
					log4javaFx.error("Can't process copy operation", e);
					afterAllOperations();
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
						afterAllOperations();
					});
				});
			});

			/**
			 * initTablesRowFactory
			 */
			mainPanel.getTableSources().setRowFactory(tv -> {
				final TableRow<SourceEntry> row = new TableRow<>();
				row.setOnMouseClicked(mouseEvent -> {
					if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
						navigateTo.navigateTo(row.getItem().rootPath, toolRunner);
					}
				});
				return row;
			});

			mainPanel.getTableDestinations().setRowFactory(tv -> {
				final TableRow<DestinationEntry> row = new TableRow<>();
				row.setOnMouseClicked(mouseEvent -> {
					if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
						navigateTo.navigateTo(row.getItem().rootPath, toolRunner);
					}
				});
				return row;
			});

			mainPanel.getTableFiles().setRowFactory(tv -> {
				final TableRow<FileEntry> row = new TableRow<>();
				row.setOnMouseClicked(mouseEvent -> {
					if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
						navigateTo.navigateTo(row.getItem().getFile(), toolRunner);
					}
				});
				return row;
			});

			/**
			 * initTablesContextMenu
			 */
			new TableContextMenu(mainPanel.getTableSources(), navigateTo, toolRunner);
			new TableContextMenu(mainPanel.getTableDestinations(), navigateTo, toolRunner);
			new TableContextMenu(mainPanel.getTableFiles(), navigateTo, toolRunner);

			/**
			 * Display "about" labels
			 */
			final StringBuilder aboutText = new StringBuilder();
			aboutText.append(System.getProperty("javappackager.appversion", ""));
			aboutText.append(" ");
			aboutText.append(System.getProperty("javappackager.gitversion", ""));
			if (aboutText.toString().trim().equals("") == false) {
				mainPanel.getLblAppAbout().setText("Version: " + aboutText.toString().trim());
			}
			final String appUrl = System.getProperty("javappackager.url");
			if (appUrl != null) {
				mainPanel.getLblAppLink().setText(appUrl);
				mainPanel.getLblAppLink().setOnAction(event -> {
					getHostServices().showDocument(appUrl);
					event.consume();
				});
			}

		} catch (final Exception e) {
			log.error("Error during loading app", e);
			System.exit(1);
		}
	}

	private VBox createPlaceholder(final String text) {
		final VBox placeholder = new VBox(50);
		placeholder.setAlignment(Pos.CENTER);
		final Text welcomeLabel = new Text(text);
		welcomeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");
		welcomeLabel.setStrokeType(StrokeType.INSIDE);
		welcomeLabel.setTextAlignment(TextAlignment.CENTER);
		welcomeLabel.setFill(Color.web("#bbb"));
		placeholder.getChildren().add(welcomeLabel);
		return placeholder;
	}

	@Override
	public void stop() {
		/**
		 * Save controls preferencies
		 */
		final Consumer<TableColumn<? extends TargetedFileEntries, ?>> getColWidthToConfig = c -> {
			if (c.getId() == null) {
				return;
			}
			store.setConfigValue(c.getId() + ".width", c.getWidth());
		};
		mainPanel.getTableSources().getColumns().forEach(getColWidthToConfig);
		mainPanel.getTableDestinations().getColumns().forEach(getColWidthToConfig);
		mainPanel.getTableFiles().getColumns().forEach(getColWidthToConfig);

		store.setConfigValue("primaryStage.width", stage.getWidth());
		store.setConfigValue("primaryStage.height", stage.getHeight());
		store.setConfigValue("primaryStage.x", stage.getX());
		store.setConfigValue("primaryStage.y", stage.getY());
		store.setConfigValue("CBCheckAfterCopy", Boolean.toString(mainPanel.getCBCheckAfterCopy().isSelected()));

		log.info("JavaFX GUI Interface is stopped");
		System.exit(0);
	}

	private Optional<File> selectDirectory(final String titleTextKey) {
		final DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle(messages.getString(titleTextKey));
		return Optional.ofNullable(directoryChooser.showDialog(stage));
	}

	public static String byteCountToDisplaySizeWithPrecision(final long bytes) {
		if (bytes == 0) {
			return "0 Bytes";
		}

		final long k = 1000;
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

	public void updateProgress(final double progressRate, final int filesCopied, final int totalFiles, final long datasCopiedBytes, final long totalDatasBytes, final long startTimeMsec, final long etaMsec, final long meanCopySpeedBytesPerSec, final long instantCopySpeedBytesPerSec) {
		mainPanel.getProgressBar().setProgress(progressRate);

		final String counter = String.format(messages.getString("labelProgressProcess"), filesCopied + 1, totalFiles, MainApp.byteCountToDisplaySizeWithPrecision(datasCopiedBytes), MainApp.byteCountToDisplaySizeWithPrecision(totalDatasBytes));
		mainPanel.getLblProgressionCounter().setText(counter);

		if (etaMsec < 1) {
			mainPanel.getLblEta().setText("ETA: 00:00:00");
		} else {
			mainPanel.getLblEta().setText("ETA: " + DurationFormatUtils.formatDuration(etaMsec, "HH:mm:ss"));
		}

		final String since;
		if (startTimeMsec < 1) {
			since = "00:00:00";
		} else {
			since = DurationFormatUtils.formatDuration(startTimeMsec, "HH:mm:ss");
		}

		final String speedCopy = String.format(messages.getString("labelProgressSpeed"), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(meanCopySpeedBytesPerSec)), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(instantCopySpeedBytesPerSec)), since);
		mainPanel.getLblSpeedCopy().setText(speedCopy);
	}

	private void afterAllOperations() {
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

		fileList.stream().forEach(f -> {
			f.updateState();
		});
		displayStatusMsgBox();
	}

	private void afterCopyBeforeCheckOperation() {
		mainPanel.getProgressBar().setProgress(0);
		mainPanel.getLblEta().setText("");
		mainPanel.getLblSpeedCopy().setText(messages.getString("labelProgressCheck"));
	}

	private void displayStatusMsgBox() {
		final Map<FileEntryStatus, Integer> countByStatuses = FileEntryStatus.countByStatuses(fileList);
		final Map<FileEntryStatus, Long> sizeByStatuses = FileEntryStatus.sizeByStatuses(fileList);

		final AlertType alertType;
		if (countByStatuses.get(FileEntryStatus.ERROR_OR_INCOMPLETE).intValue() > 0) {
			alertType = AlertType.ERROR;
		} else if (countByStatuses.get(FileEntryStatus.PARTIAL_DONE).intValue() > 0) {
			alertType = AlertType.WARNING;
		} else {
			alertType = AlertType.INFORMATION;
		}

		final String header;
		if (countByStatuses.get(FileEntryStatus.ERROR_OR_INCOMPLETE).intValue() > 0) {
			header = messages.getString("statusMsgboxHeader_ERROR_OR_INCOMPLETE");
		} else if (countByStatuses.get(FileEntryStatus.PARTIAL_DONE).intValue() > 0) {
			header = messages.getString("statusMsgboxHeader_PARTIAL_DONE");
		} else if (countByStatuses.get(FileEntryStatus.NOT_STARTED).intValue() > 0) {
			header = messages.getString("statusMsgboxHeader_NOT_STARTED");
		} else if (countByStatuses.get(FileEntryStatus.INTEGRITY_INVALID).intValue() > 0) {
			header = messages.getString("statusMsgboxHeader_INTEGRITY_INVALID");
		} else if (countByStatuses.get(FileEntryStatus.INTEGRITY_VALID).intValue() > 0) {
			header = messages.getString("statusMsgboxHeader_INTEGRITY_VALID");
		} else {
			header = messages.getString("statusMsgboxHeader_ALL_COPIES_DONE");
		}

		final Alert alert = new Alert(alertType);
		alert.setTitle(messages.getString("statusMsgboxTitle"));
		alert.setHeaderText(header);

		alert.setContentText(Arrays.stream(FileEntryStatus.values()).filter(status -> {
			return countByStatuses.get(status).intValue() > 0;
		}).map(status -> {
			final int count = countByStatuses.get(status).intValue();
			final String size = byteCountToDisplaySizeWithPrecision(sizeByStatuses.get(status).longValue());
			return String.format(messages.getString("statusMsgboxContent_" + status.name()), count, size);
		}).collect(Collectors.joining(System.lineSeparator())));

		((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(appIcon);
		alert.showAndWait();
	}

	private void setFolderDragAndDrop(final Node node, final Supplier<Boolean> isEmpty, final Consumer<File> onDropDirectory) {
		final String defaultStyle = node.getStyle();
		node.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
			e.consume();
		});
		node.setOnDragEntered(e -> {
			if (isEmpty.get()) {
				node.setStyle("-fx-background-color: #ddfbff");
			}
			e.consume();
		});
		node.setOnDragExited(e -> {
			node.setStyle(defaultStyle);
			e.consume();
		});
		node.setOnDragDropped(e -> {
			e.getDragboard().getFiles().stream().filter(File::isDirectory).forEach(onDropDirectory);
			e.consume();
		});
	}

}
