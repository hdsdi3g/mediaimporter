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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import tv.hd3g.mediaimporter.tools.FileSanity;

public class SourceEntry extends BaseSourceDestEntry {

	private final FileSanity fileSanity;
	private final Map<File, Long> digestByFileCache;

	public SourceEntry(final File rootPath, final FileSanity fileSanity, final Map<File, Long> digestByFileCache) {
		super(rootPath);
		this.fileSanity = fileSanity;
		this.digestByFileCache = Objects.requireNonNull(digestByFileCache, "\"digestByFile\" can't to be null");

	}

	public static Callback<CellDataFeatures<SourceEntry, File>, ObservableValue<File>> getColPathFactory() {
		return param -> {
			return new ReadOnlyObjectWrapper<>(param.getValue().rootPath);
		};
	}

	/**
	 * @return new file entries.
	 */
	public List<FileEntry> scanSource(final ObservableList<FileEntry> fileList, final SimpleStringProperty driveSN, final List<DestinationEntry> destsList) throws IOException {
		final Set<FileEntry> actualFileEntrySet = fileList.stream().distinct().collect(Collectors.toSet());
		if (fileList.size() != actualFileEntrySet.size()) {
			/**
			 * Remove duplicate entries.
			 */
			fileList.clear();
			fileList.addAll(actualFileEntrySet);
		}

		final Set<File> actualFileSet = fileList.stream().map(FileEntry::getFile).distinct().collect(Collectors.toSet());

		return Files.walk(rootPath.toPath()).map(Path::toFile).filter(founded -> {
			if (founded.isDirectory()) {
				return false;
			} else if (actualFileSet.contains(founded)) {
				return false;
			} else if (founded.length() == 0) {
				return false;
			}
			return fileSanity.isFileIsValid(founded);
		}).peek(actualFileSet::add).map(founded -> {
			final FileEntry newFileEntry = new FileEntry(this, founded, driveSN, destsList, digestByFileCache);
			fileList.add(newFileEntry);
			return newFileEntry;
		}).collect(Collectors.toUnmodifiableList());
	}
}
