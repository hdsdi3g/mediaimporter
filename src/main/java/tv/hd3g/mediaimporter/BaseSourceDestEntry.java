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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;

public abstract class BaseSourceDestEntry implements TargetedFileEntries {

	private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	protected final File rootPath;
	protected final SimpleStringProperty systemDriveName;
	protected final SimpleStringProperty systemDriveType;

	public BaseSourceDestEntry(final File rootPath) {
		this.rootPath = Objects.requireNonNull(rootPath, "\"rootPath\" can't to be null");
		systemDriveName = new SimpleStringProperty();
		systemDriveType = new SimpleStringProperty();
	}

	private static Predicate<BaseSourceDestEntry> isStoredOn(final Stream<BaseSourceDestEntry> toCompare) {
		return entry -> {
			return toCompare.map(s -> s.rootPath).anyMatch(file -> {
				try {
					return FileUtils.directoryContains(file, entry.rootPath);
				} catch (final IOException e) {
					MainApp.log4javaFx.error("Can't scan directory " + file, e);
					return true;
				}
			});
		};
	}

	public static Predicate<BaseSourceDestEntry> isStoredOn(final ObservableList<SourceEntry> toCompareSources, final ObservableList<DestinationEntry> toCompareDests) {
		return BaseSourceDestEntry.isStoredOn(toCompareSources.stream().map(item -> {
			return (BaseSourceDestEntry) item;
		})).or(BaseSourceDestEntry.isStoredOn(toCompareDests.stream().map(item -> {
			return (BaseSourceDestEntry) item;
		}))).or(item -> {
			return toCompareSources.stream().map(s -> s.rootPath).anyMatch(s -> {
				return item.rootPath.equals(s);
			}) & toCompareDests.stream().map(s -> s.rootPath).anyMatch(s -> {
				return item.rootPath.equals(s);
			});
		});
	}

	@Override
	public List<Entry> getTargetedFileEntries() {
		return Arrays.asList(new Entry(Messages.getString("tableContextMenuDir"), rootPath));
	}

	@Override
	public String toString() {
		return rootPath.getPath();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (rootPath == null ? 0 : rootPath.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof BaseSourceDestEntry)) {
			return false;
		}
		final BaseSourceDestEntry other = (BaseSourceDestEntry) obj;
		if (rootPath == null) {
			if (other.rootPath != null) {
				return false;
			}
		} else if (!rootPath.equals(other.rootPath)) {
			return false;
		}
		return true;
	}

	public File getRootPath() {
		return rootPath;
	}

	/**
	 * @return like "CD-ROM (M:)" or "MyDir E"
	 */
	public String getSystemDisplayName() {
		final var systemDisplayName = fileSystemView.getSystemDisplayName(rootPath);
		if (rootPath.getName().equals(systemDisplayName)) {
			final var abs = rootPath.getAbsolutePath();
			if (abs.contains(":")) {
				return rootPath.getName() + " " + abs.substring(0, abs.indexOf(":"));
			} else {
				return abs;
			}
		}
		return systemDisplayName;
	}

	public void updateColsDriveType() {
		systemDriveName.set(fileSystemView.getSystemDisplayName(rootPath));
		systemDriveType.set(fileSystemView.getSystemTypeDescription(rootPath));
	}

	public static <T extends BaseSourceDestEntry> Callback<CellDataFeatures<T, String>, ObservableValue<String>> getColDriveFactory() {
		return param -> {
			return param.getValue().systemDriveName;
		};
	}

	public static <T extends BaseSourceDestEntry> Callback<CellDataFeatures<T, String>, ObservableValue<String>> getColTypeFactory() {
		return param -> {
			return param.getValue().systemDriveType;
		};
	}

}
