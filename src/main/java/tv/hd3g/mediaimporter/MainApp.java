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
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.image.Image;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import tv.hd3g.mediaimporter.io.CanBeStopped;
import tv.hd3g.mediaimporter.io.CopyFilesEngine;
import tv.hd3g.mediaimporter.io.IntegrityCheckEngine;
import tv.hd3g.mediaimporter.tools.ConfigurationStore;
import tv.hd3g.mediaimporter.tools.FileSanity;
import tv.hd3g.mediaimporter.ui.StatusMsgBox;
import tv.hd3g.mediaimporter.ui.UIMainPanelProvider;
import tv.hd3g.mediaimporter.ui.UIProgresser;

public class MainApp extends Application implements UIProgresser, UIMainPanelProvider {
	private static Logger log = LogManager.getLogger();
	public static final Logger log4javaFx = LogManager.getLogger("javafx");

	private static final DecimalFormat decimalFormat1digits = new DecimalFormat("###,###.#");
	private static final DecimalFormat decimalFormat2digits = new DecimalFormat("###,###.##");

	public MainApp() {
		super();
	}

	private SimpleObjectProperty<CanBeStopped> currentCopyEngine;
	private MainPanel mainPanel;
	private Stage stage;
	private Image appIcon;
	private ConfigurationStore store;

	@Override
	public MainPanel getMainPanel() {
		return mainPanel;
	}

	@Override
	public void start(final Stage primaryStage) {
		final var fileSanity = FileSanity.get();
		final var digestByFileCache = new ConcurrentHashMap<File, Long>();
		currentCopyEngine = new SimpleObjectProperty<>(null);

		stage = primaryStage;
		log.info("Start JavaFX GUI Interface");
		try {

			final FXMLLoader d = new FXMLLoader();
			final BorderPane root = (BorderPane) d.load(getClass().getResource(MainPanel.class.getSimpleName() + ".fxml").openStream());

			mainPanel = d.getController();

			final var backend = mainPanel.getBackend();
			final var sourcesList = backend.getSourcesList();
			final var destsList = backend.getDestsList();
			final var fileList = backend.getFileList();

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
			mainPanel.prepareTableSources(setColWidthFromConfig, stage, file -> new SourceEntry(file, fileSanity, digestByFileCache));
			mainPanel.prepareTableDestinations(setColWidthFromConfig, stage);
			mainPanel.prepareTableFiles(setColWidthFromConfig);

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
			sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
				while (change.next()) {
					change.getAddedSubList().stream().filter(BaseSourceDestEntry.isStoredOn(sourcesList, destsList)).forEach(entry -> {
						MainApp.log4javaFx.warn(Messages.getString("dontAllowDirs") + ": " + entry);
						sourcesList.remove(entry);
					});
				}
			});

			/**
			 * initDestZone
			 */
			destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
				while (change.next()) {
					change.getAddedSubList().stream().filter(BaseSourceDestEntry.isStoredOn(sourcesList, destsList)).forEach(entry -> {
						MainApp.log4javaFx.warn(Messages.getString("dontAllowDirs") + ": " + entry);
						destsList.remove(entry);
					});
				}
			});

			/**
			 * initFileZone
			 */
			final Supplier<Boolean> isBtnAddSourceToScanDisabled = () -> {
				return sourcesList.isEmpty() | destsList.isEmpty();
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

			mainPanel.getBtnAddSourceToScan().setOnAction(event -> {
				event.consume();
				if (sourcesList.isEmpty()) {
					return;
				}
				log.info("Start scan source dirs");
				mainPanel.getBtnClearScanlist().setDisable(true);

				fileList.removeIf(fileEntry -> {
					return fileEntry.updateState();
				});

				digestByFileCache.clear();

				sourcesList.forEach(entry -> {
					try {
						final List<FileEntry> newFilesEntries = entry.scanSource(fileList, destsList);

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
					final String label = String.format(Messages.getString("labelProgressReady"), stats.getCount(), MainApp.byteCountToDisplaySizeWithPrecision(stats.getSum()));
					mainPanel.getLblProgressionCounter().setText(label);
					mainPanel.getBtnStartCopy().setDisable(false);
				}

				mainPanel.getBtnClearScanlist().setDisable(fileList.isEmpty());

				new StatusMsgBox(fileList, appIcon).showAndWait();
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
								mainPanel.getProgressBar().setProgress(0);
								mainPanel.getLblEta().setText("");
								mainPanel.getLblSpeedCopy().setText(Messages.getString("labelProgressCheck"));
							});
							CompletableFuture.runAsync(() -> {
								Platform.runLater(() -> {
									new StatusMsgBox(fileList, appIcon).showAndWait();
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
		mainPanel.saveTableSourceSetup(getColWidthToConfig);
		mainPanel.saveTableDestinationsSetup(getColWidthToConfig);
		mainPanel.saveTableFilesSetup(getColWidthToConfig);

		store.setConfigValue("primaryStage.width", stage.getWidth());
		store.setConfigValue("primaryStage.height", stage.getHeight());
		store.setConfigValue("primaryStage.x", stage.getX());
		store.setConfigValue("primaryStage.y", stage.getY());
		store.setConfigValue("CBCheckAfterCopy", Boolean.toString(mainPanel.getCBCheckAfterCopy().isSelected()));

		log.info("JavaFX GUI Interface is stopped");
		System.exit(0);
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

		mainPanel.getBackend().getFileList().stream().forEach(f -> {
			f.updateState();
		});
		new StatusMsgBox(mainPanel.getBackend().getFileList(), appIcon).showAndWait();
	}

	public static void setFolderDragAndDrop(final Node node, final Supplier<Boolean> isEmpty, final Consumer<File> onDropDirectory) {
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
