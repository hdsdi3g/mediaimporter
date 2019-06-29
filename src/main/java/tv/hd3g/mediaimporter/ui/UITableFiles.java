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

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import tv.hd3g.mediaimporter.FileEntry;
import tv.hd3g.mediaimporter.Messages;
import tv.hd3g.mediaimporter.TargetedFileEntries;

public interface UITableFiles extends PanelBackendProvider {

	Logger log = LogManager.getLogger();

	TableView<FileEntry> getTableFiles();

	TableColumn<FileEntry, String> getTableFilesColSource();

	TableColumn<FileEntry, String> getTableFilesColPath();

	TableColumn<FileEntry, Number> getTableFilesColSize();

	TableColumn<FileEntry, String> getTableFilesColStatus();

	default void prepareTableFiles(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> setColWidthFromConfig) {
		final var tableFiles = getTableFiles();
		final var fileList = getBackend().getFileList();

		tableFiles.getColumns().forEach(setColWidthFromConfig);
		tableFiles.setItems(fileList);
		tableFiles.setPlaceholder(new Placeholder(Messages.getString("tableFilePlaceholder")));
		getTableFilesColSource().setCellValueFactory(FileEntry.getColSourceFactory());
		getTableFilesColPath().setCellValueFactory(FileEntry.getColPathFactory());
		getTableFilesColSize().setCellValueFactory(FileEntry.getColSizeFactory());
		getTableFilesColStatus().setCellValueFactory(FileEntry.getColStatusFactory());
		getTableFilesColSize().setCellFactory(col -> new TableCellFileSize<>());

		final var backend = getBackend();
		tableFiles.setRowFactory(tv -> {
			final TableRow<FileEntry> row = new TableRow<>();
			row.setOnMouseClicked(mouseEvent -> {
				if (row.isEmpty() == false && mouseEvent.getButton().equals(MouseButton.PRIMARY) && mouseEvent.getClickCount() == 2) {
					backend.getNavigateTo().navigateTo(row.getItem().getFile(), backend.getToolRunner());
				}
			});
			return row;
		});

		new TableContextMenu(tableFiles, backend.getNavigateTo(), backend.getToolRunner());
	}

	default void saveTableFilesSetup(final Consumer<TableColumn<? extends TargetedFileEntries, ?>> getColWidthToConfig) {
		getTableFiles().getColumns().forEach(getColWidthToConfig);
	}

}
