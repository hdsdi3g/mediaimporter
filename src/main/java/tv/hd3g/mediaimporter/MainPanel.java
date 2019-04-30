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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class MainPanel {

	/**
	 * Source zone
	 */
	@FXML
	private Button btnAddSourceDir;
	@FXML
	private Button btnRemoveSourceDir;
	@FXML
	private TableView<SourceEntry> tableSources;
	@FXML
	private TableColumn<SourceEntry, File> tableSourcesColPath;

	/**
	 * Dest zone
	 */
	@FXML
	private Button btnAddDestinationDir;
	@FXML
	private Button btnRemoveDestinationDir;
	@FXML
	private TableView<DestinationEntry> tableDestinations;
	@FXML
	private TableColumn<DestinationEntry, File> tableDestinationsColPath;
	@FXML
	private TableColumn<DestinationEntry, Number> tableDestinationsColAvailable;
	@FXML
	private TableColumn<DestinationEntry, Number> tableDestinationsColSpeed;
	@FXML
	private TableColumn<DestinationEntry, Number> tableDestinationsColSlots;

	/**
	 * File zone
	 */
	@FXML
	private Button btnAddSourceToScan;
	@FXML
	private Button btnClearScanlist;
	@FXML
	private TableView<FileEntry> tableFiles;
	@FXML
	private TableColumn<FileEntry, String> tableFilesColSource;
	@FXML
	private TableColumn<FileEntry, String> tableFilesColPath;
	@FXML
	private TableColumn<FileEntry, Number> tableFilesColSize;
	@FXML
	private TableColumn<FileEntry, String> tableFilesColStatus;

	/**
	 * Status zone
	 */
	@FXML
	private ProgressBar progressBar;
	@FXML
	private Label lblProgressionCounter;
	@FXML
	private Label lblEta;
	@FXML
	private Label lblSpeedCopy;

	/**
	 * Action zone
	 */
	@FXML
	private Button btnStartCopy;
	@FXML
	private Button btnStopCopy;
	@FXML
	private Button btnQuit;

	public Button getBtnAddSourceDir() {
		return btnAddSourceDir;
	}

	public Button getBtnRemoveSourceDir() {
		return btnRemoveSourceDir;
	}

	public TableView<SourceEntry> getTableSources() {
		return tableSources;
	}

	public TableColumn<SourceEntry, File> getTableSourcesColPath() {
		return tableSourcesColPath;
	}

	public Button getBtnAddDestinationDir() {
		return btnAddDestinationDir;
	}

	public Button getBtnRemoveDestinationDir() {
		return btnRemoveDestinationDir;
	}

	public TableView<DestinationEntry> getTableDestinations() {
		return tableDestinations;
	}

	public TableColumn<DestinationEntry, File> getTableDestinationsColPath() {
		return tableDestinationsColPath;
	}

	public TableColumn<DestinationEntry, Number> getTableDestinationsColAvailable() {
		return tableDestinationsColAvailable;
	}

	public TableColumn<DestinationEntry, Number> getTableDestinationsColSpeed() {
		return tableDestinationsColSpeed;
	}

	public TableColumn<DestinationEntry, Number> getTableDestinationsColSlots() {
		return tableDestinationsColSlots;
	}

	public Button getBtnAddSourceToScan() {
		return btnAddSourceToScan;
	}

	public Button getBtnClearScanlist() {
		return btnClearScanlist;
	}

	public TableView<FileEntry> getTableFiles() {
		return tableFiles;
	}

	public TableColumn<FileEntry, String> getTableFilesColSource() {
		return tableFilesColSource;
	}

	public TableColumn<FileEntry, String> getTableFilesColPath() {
		return tableFilesColPath;
	}

	public TableColumn<FileEntry, Number> getTableFilesColSize() {
		return tableFilesColSize;
	}

	public TableColumn<FileEntry, String> getTableFilesColStatus() {
		return tableFilesColStatus;
	}

	public ProgressBar getProgressBar() {
		return progressBar;
	}

	public Label getLblProgressionCounter() {
		return lblProgressionCounter;
	}

	public Label getLblEta() {
		return lblEta;
	}

	public Label getLblSpeedCopy() {
		return lblSpeedCopy;
	}

	public Button getBtnStartCopy() {
		return btnStartCopy;
	}

	public Button getBtnStopCopy() {
		return btnStopCopy;
	}

	public Button getBtnQuit() {
		return btnQuit;
	}

}
