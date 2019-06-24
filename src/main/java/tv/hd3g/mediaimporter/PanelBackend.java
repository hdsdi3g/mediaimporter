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

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tv.hd3g.mediaimporter.tools.NavigateTo;
import tv.hd3g.mediaimporter.tools.SNDriveUpdater;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class PanelBackend {
	private static Logger log = LogManager.getLogger();

	private final ToolRunner toolRunner;
	private final NavigateTo navigateTo;
	private final SNDriveUpdater sNDriveUpdater;
	private final ObservableList<SourceEntry> sourcesList;
	private final ObservableList<DestinationEntry> destsList;
	private final ObservableList<FileEntry> fileList;

	public PanelBackend() {
		toolRunner = new ToolRunner(new ExecutableFinder(), 2);
		navigateTo = NavigateTo.get();
		sNDriveUpdater = new SNDriveUpdater(toolRunner);
		sourcesList = FXCollections.observableList(new ArrayList<SourceEntry>());
		destsList = FXCollections.observableList(new ArrayList<DestinationEntry>());
		fileList = FXCollections.observableList(new ArrayList<FileEntry>());
	}

	public ToolRunner getToolRunner() {
		return toolRunner;
	}

	public NavigateTo getNavigateTo() {
		return navigateTo;
	}

	public SNDriveUpdater getsNDriveUpdater() {
		return sNDriveUpdater;
	}

	public ObservableList<DestinationEntry> getDestsList() {
		return destsList;
	}

	public ObservableList<FileEntry> getFileList() {
		return fileList;
	}

	public ObservableList<SourceEntry> getSourcesList() {
		return sourcesList;
	}
}
