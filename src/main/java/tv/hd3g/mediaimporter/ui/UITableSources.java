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
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import tv.hd3g.mediaimporter.BaseSourceDestEntry;
import tv.hd3g.mediaimporter.MainApp;
import tv.hd3g.mediaimporter.Messages;
import tv.hd3g.mediaimporter.SourceEntry;
import tv.hd3g.mediaimporter.TargetedFileEntries;

public interface UITableSources extends PanelBackendProvider {

	Logger log = LogManager.getLogger();

	TableView<SourceEntry> getTableSources();

	TableColumn<SourceEntry, File> getTableSourcesColPath();

	TableColumn<SourceEntry, String> getTableSourcesColDrive();

	TableColumn<SourceEntry, String> getTableSourcesColType();

	Button getBtnAddSourceDir();

	Button getBtnRemoveSourceDir();

	default void prepareTableSources(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> setColWidthFromConfig, final Window stage, final Function<File, SourceEntry> sourceEntryBuilder) {
		final var tableSources = getTableSources();
		final var sourcesList = getBackend().getSourcesList();

		tableSources.getColumns().forEach(setColWidthFromConfig);

		final Consumer<File> onAddNewSourceDir = file -> {
			final SourceEntry toAdd = sourceEntryBuilder.apply(file);
			if (sourcesList.contains(toAdd) == false) {
				log.info("Add new source directory: " + file);
				sourcesList.add(toAdd);
			}
		};

		tableSources.setItems(sourcesList);
		tableSources.setPlaceholder(new Placeholder(Messages.getString("tableSourcePlaceholder")));
		MainApp.setFolderDragAndDrop(tableSources, tableSources.getItems()::isEmpty, onAddNewSourceDir);
		getTableSourcesColPath().setCellValueFactory(SourceEntry.getColPathFactory());
		getTableSourcesColDrive().setCellValueFactory(BaseSourceDestEntry.getColDriveFactory());
		getTableSourcesColType().setCellValueFactory(BaseSourceDestEntry.getColTypeFactory());

		getBtnAddSourceDir().setOnAction(event -> {
			event.consume();
			new OptionalDirectoryChooser("addSourceDirectory", stage).select().ifPresent(onAddNewSourceDir);
		});

		getBtnRemoveSourceDir().setOnAction(event -> {
			event.consume();
			final SourceEntry selected = tableSources.getSelectionModel().getSelectedItem();
			if (selected != null) {
				log.debug("Remove source dir: " + selected);
				sourcesList.remove(selected);
			}
		});

		tableSources.getSelectionModel().selectedItemProperty().addListener((observable_value, old_value, new_value) -> {
			getBtnRemoveSourceDir().setDisable(new_value == null);
		});

		final var backend = getBackend();
		tableSources.setRowFactory(tv -> {
			final TableRow<SourceEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					backend.getNavigateTo().navigateTo(row.getItem().getRootPath(), backend.getToolRunner());
				}
			});
			return row;
		});

		new TableContextMenu(tableSources, backend.getNavigateTo(), backend.getToolRunner());
	}

	default void saveTableSourceSetup(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> getColWidthToConfig) {
		getTableSources().getColumns().forEach(getColWidthToConfig);
	}
}
