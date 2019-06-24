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
package tv.hd3g.mediaimporter.ui;

import java.io.File;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import tv.hd3g.mediaimporter.BaseSourceDestEntry;
import tv.hd3g.mediaimporter.DestinationEntry;
import tv.hd3g.mediaimporter.MainApp;
import tv.hd3g.mediaimporter.Messages;
import tv.hd3g.mediaimporter.TargetedFileEntries;

public interface UITableDestinations extends PanelBackendProvider {

	Logger log = LogManager.getLogger();

	Button getBtnAddDestinationDir();

	Button getBtnRemoveDestinationDir();

	TableView<DestinationEntry> getTableDestinations();

	TableColumn<DestinationEntry, File> getTableDestinationsColPath();

	TableColumn<DestinationEntry, Number> getTableDestinationsColAvailable();

	TableColumn<DestinationEntry, Number> getTableDestinationsColSpeed();

	TableColumn<DestinationEntry, Number> getTableDestinationsColSlots();

	TableColumn<DestinationEntry, String> getTableDestinationsColDrive();

	TableColumn<DestinationEntry, String> getTableDestinationsColType();

	default void prepareTableDestinations(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> setColWidthFromConfig, final Window stage) {
		final var tableDestinations = getTableDestinations();
		final var destsList = getBackend().getDestsList();
		final var fileList = getBackend().getFileList();

		tableDestinations.getColumns().forEach(setColWidthFromConfig);

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

		tableDestinations.setItems(destsList);
		tableDestinations.setPlaceholder(new Placeholder(Messages.getString("tableDestPlaceholder")));
		MainApp.setFolderDragAndDrop(tableDestinations, tableDestinations.getItems()::isEmpty, onAddNewDestDir);
		getTableDestinationsColPath().setCellValueFactory(DestinationEntry.getColPathFactory());
		getTableDestinationsColAvailable().setCellValueFactory(DestinationEntry.getColAvailableFactory());
		getTableDestinationsColSpeed().setCellValueFactory(DestinationEntry.getColAvailableSpeed());
		getTableDestinationsColSlots().setCellValueFactory(DestinationEntry.getColAvailableSlots());
		getTableDestinationsColDrive().setCellValueFactory(BaseSourceDestEntry.getColDriveFactory());
		getTableDestinationsColType().setCellValueFactory(BaseSourceDestEntry.getColTypeFactory());

		destsList.forEach(DestinationEntry::updateSlotsContent);

		getTableDestinationsColAvailable().setCellFactory(col -> new TableCellFileSize<>());
		getTableDestinationsColSpeed().setCellFactory(col -> new TableCellFileSize<>("/sec"));

		getBtnAddDestinationDir().setOnAction(event -> {
			event.consume();
			new OptionalDirectoryChooser("addDestDirectory", stage).select().ifPresent(onAddNewDestDir);
		});

		getBtnRemoveDestinationDir().setOnAction(event -> {
			event.consume();
			final DestinationEntry selected = tableDestinations.getSelectionModel().getSelectedItem();
			if (selected != null) {
				log.debug("Remove dest dir: " + selected);
				destsList.remove(selected);

				log.debug("Update all actual references: " + selected);
				fileList.forEach(fileEntry -> {
					fileEntry.removeDestination(selected);
				});
			}
		});
		tableDestinations.getSelectionModel().selectedItemProperty().addListener((observable_value, old_value, new_value) -> {
			getBtnRemoveDestinationDir().setDisable(new_value == null);
		});

		final var backend = getBackend();
		tableDestinations.setRowFactory(tv -> {
			final TableRow<DestinationEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					backend.getNavigateTo().navigateTo(row.getItem().getRootPath(), backend.getToolRunner());
				}
			});
			return row;
		});

		new TableContextMenu(tableDestinations, backend.getNavigateTo(), backend.getToolRunner());
	}

	default void saveTableDestinationsSetup(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> getColWidthToConfig) {
		getTableDestinations().getColumns().forEach(getColWidthToConfig);
	}

}
