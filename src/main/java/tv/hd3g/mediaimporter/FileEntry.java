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
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;

public class FileEntry {
	private static Logger log = LogManager.getLogger();

	private final SourceEntry source;
	private final File file;
	private final SimpleStringProperty status;
	private final String driveSN;

	public FileEntry(final SourceEntry source, final File file, final String driveSN) {
		this.source = Objects.requireNonNull(source, "\"source\" can't to be null");
		this.file = Objects.requireNonNull(file, "\"file\" can't to be null");
		this.driveSN = Objects.requireNonNull(driveSN, "\"driveSN\" can't to be null");
		status = new SimpleStringProperty();
		// TODO update status on create
	}

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColSourceFactory() {
		return param -> {
			return new ReadOnlyObjectWrapper<>(param.getValue().source.rootPath.getPath() + " (" + param.getValue().driveSN + ")");
		};
	}

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColPathFactory() {
		return param -> {
			final int len = param.getValue().source.rootPath.getAbsolutePath().length();
			return new ReadOnlyObjectWrapper<>(param.getValue().file.getAbsolutePath().substring(len + 1));
		};
	}

	public static Callback<CellDataFeatures<FileEntry, Number>, ObservableValue<Number>> getColSizeFactory() {
		return param -> {
			return new ReadOnlyLongWrapper(param.getValue().getFile().length());
		};
	}

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColStatusFactory() {
		return param -> {
			return param.getValue().status;
		};
	}

	@Override
	public String toString() {
		return file.getPath();
	}

	public File getFile() {
		return file;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (file == null ? 0 : file.hashCode());
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
		if (!(obj instanceof FileEntry)) {
			return false;
		}
		final FileEntry other = (FileEntry) obj;
		if (file == null) {
			if (other.file != null) {
				return false;
			}
		} else if (!file.equals(other.file)) {
			return false;
		}
		return true;
	}
}
