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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import tv.hd3g.mediaimporter.DestinationEntry.Slot;

public class FileEntry {
	private static Logger log = LogManager.getLogger();

	private final SourceEntry source;
	private final File file;
	private final SimpleStringProperty status;
	private final SimpleStringProperty driveSN;
	private final Map<DestinationEntry, CopyFileReference> copiesByDestination;
	private final String relativePath;
	private final List<DestinationEntry> destsList;

	public FileEntry(final SourceEntry source, final File file, final SimpleStringProperty driveSN, final List<DestinationEntry> destsList) {
		this.source = Objects.requireNonNull(source, "\"source\" can't to be null");
		this.file = Objects.requireNonNull(file, "\"file\" can't to be null");
		this.driveSN = Objects.requireNonNull(driveSN, "\"driveSN\" can't to be null");
		status = new SimpleStringProperty();
		copiesByDestination = new HashMap<>();
		relativePath = file.getAbsolutePath().substring(source.rootPath.getAbsolutePath().length() + 1);
		this.destsList = destsList;
	}

	public void addDestination(final DestinationEntry destination) {
		destination.searchCopyPresence(relativePath).ifPresentOrElse(copy -> {
			if (copiesByDestination.containsKey(destination)) {
				if (copiesByDestination.get(destination).equalsNotChanged(file)) {
					return;
				}
			}
			copiesByDestination.put(destination, new CopyFileReference(copy));
			updateStatus();
		}, () -> {
			if (copiesByDestination.containsKey(destination)) {
				copiesByDestination.remove(destination);
			}
			updateStatus();
		});
	}

	/**
	 * @return true for remove
	 */
	public boolean updateState() {
		if (file.exists() == false) {
			return true;
		}
		copiesByDestination.clear();
		destsList.forEach(destination -> {
			addDestination(destination);
		});
		return false;
	}

	public String getDriveSNValue() {
		if (driveSN.isEmpty().get()) {
			return MainApp.messages.getString("driveSNDefault");
		}
		return driveSN.getValue();
	}

	public void removeDestination(final DestinationEntry oldDestination) {
		if (copiesByDestination.remove(oldDestination) != null) {
			updateStatus();
		}
	}

	public SourceEntry getSource() {
		return source;
	}

	public String getRelativePath() {
		return relativePath;
	}

	private class CopyFileReference {
		private final File copy;
		private final boolean sameSize;

		CopyFileReference(final File copy) {
			this.copy = Objects.requireNonNull(copy, "\"copy\" can't to be null");
			sameSize = file.length() == copy.length();
		}

		boolean equalsNotChanged(final File candidate) {
			return candidate.equals(copy) & candidate.length() == copy.length();
		}

	}

	private static final Predicate<CopyFileReference> isSameSize = c -> c.sameSize;

	private void updateStatus() {
		if (copiesByDestination.isEmpty()) {
			status.setValue(MainApp.messages.getString("fileEntryStatusNew"));
		} else {
			final boolean isNotOnError = copiesByDestination.values().stream().allMatch(isSameSize);
			if (copiesByDestination.size() == destsList.size()) {
				if (isNotOnError) {
					status.setValue(String.format(MainApp.messages.getString("fileEntryStatusDone"), copiesByDestination.size()));
				} else {
					final long inError = copiesByDestination.values().stream().filter(isSameSize.negate()).count();
					status.setValue(String.format(MainApp.messages.getString("fileEntryStatusWithError"), inError, copiesByDestination.size()));
				}
			} else if (isNotOnError) {
				status.setValue(String.format(MainApp.messages.getString("fileEntryStatusPartial"), copiesByDestination.size(), destsList.size()));
			} else {
				final long inError = copiesByDestination.values().stream().filter(isSameSize.negate()).count();
				status.setValue(String.format(MainApp.messages.getString("fileEntryStatusPartialWithError"), inError, copiesByDestination.size(), destsList.size()));
			}
		}
	}

	public static final Predicate<FileEntry> needsToBeCopied = fileEntry -> {
		if (fileEntry.copiesByDestination.isEmpty()) {
			return true;
		} else {
			return fileEntry.copiesByDestination.size() != fileEntry.destsList.size();
		}
	};

	public List<Slot> getToCopyDestinationSlotList() {
		return destsList.stream().filter(destination -> {
			return copiesByDestination.containsKey(destination) == false;
		}).map(destination -> {
			return destination.getCurrentSessionSlot();
		}).collect(Collectors.toUnmodifiableList());
	}

	public SimpleStringProperty getStatus() {
		return status;
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

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColSourceFactory() {
		return param -> {
			return new ReadOnlyObjectWrapper<>(param.getValue().source.rootPath.getPath());
		};
	}

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColDriveSNFactory() {
		return param -> {
			return param.getValue().driveSN;
		};
	}

	public static Callback<CellDataFeatures<FileEntry, String>, ObservableValue<String>> getColPathFactory() {
		return param -> {
			return new ReadOnlyObjectWrapper<>(param.getValue().relativePath);
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

}
