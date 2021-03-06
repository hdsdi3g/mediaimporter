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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import tv.hd3g.mediaimporter.io.IntegrityAllState;

public class FileEntry implements TargetedFileEntries {

	private static final long maxFileSizeDigestCompute = 30_000;

	private final SourceEntry source;
	private final File file;
	private final SimpleStringProperty status;
	private final Map<DestinationEntry, CopiedFileReference> copiesByDestination;
	private final String relativePath;
	private final List<DestinationEntry> destsList;
	private final Map<File, Long> digestByFileCache;

	private IOException lastCopyError;
	private volatile FileEntryStatus currentResumeStatus;
	private String computedDigest;
	private IntegrityAllState integrityAllStates;

	public FileEntry(final SourceEntry source, final File file, final List<DestinationEntry> destsList, final Map<File, Long> digestByFileCache) {
		this.source = Objects.requireNonNull(source, "\"source\" can't to be null");
		this.file = Objects.requireNonNull(file, "\"file\" can't to be null");
		this.digestByFileCache = Objects.requireNonNull(digestByFileCache, "\"digestByFileCache\" can't to be null");

		status = new SimpleStringProperty();
		copiesByDestination = new HashMap<>();

		if (source.rootPath.getParentFile() == null) {
			/**
			 * File from root dir
			 */
			relativePath = file.getAbsolutePath().substring(source.rootPath.getAbsolutePath().length());
		} else {
			/**
			 * File from sub dir
			 */
			relativePath = file.getAbsolutePath().substring(source.rootPath.getAbsolutePath().length() + 1);
		}
		this.destsList = destsList;
		currentResumeStatus = FileEntryStatus.NOT_STARTED;
		computedDigest = "";
		integrityAllStates = IntegrityAllState.NOT_CHECKED;
	}

	public String getDriveReference() {
		return source.getSystemDisplayName().replaceAll("\\(", "").replaceAll("\\)", "").replaceAll(":", "").replaceAll("//", "").replaceAll("\\\\", "").replaceAll(" ", "_");
	}

	public void addDestination(final DestinationEntry destination) {
		final List<File> foundedPotential = destination.searchCopyPresence(relativePath, getDriveReference());

		foundedPotential.stream().filter(potentialFile -> file.length() == potentialFile.length()).filter(potentialFile -> {
			if (potentialFile.length() < maxFileSizeDigestCompute) {
				final long potentialFileDigest = destination.getdigestByFileCache().computeIfAbsent(potentialFile, f -> {
					try {
						return FileUtils.checksumCRC32(f);
					} catch (final IOException e) {
						throw new RuntimeException("Can't read " + f.getPath(), e);
					}
				});
				final long sourceFileDigest = digestByFileCache.computeIfAbsent(file, f -> {
					try {
						return FileUtils.checksumCRC32(f);
					} catch (final IOException e) {
						throw new RuntimeException("Can't read " + f.getPath(), e);
					}
				});
				return potentialFileDigest == sourceFileDigest;
			}
			return true;
		}).findFirst().ifPresentOrElse(copy -> {
			if (copiesByDestination.containsKey(destination)) {
				if (copiesByDestination.get(destination).equalsNotChanged(file)) {
					return;
				}
			}
			copiesByDestination.put(destination, new CopiedFileReference(copy));
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

	private class CopiedFileReference {
		private final File copy;
		private final boolean sameSize;

		private CopiedFileReference(final File copy) {
			this.copy = Objects.requireNonNull(copy, "\"copy\" can't to be null");
			sameSize = file.length() == copy.length();
		}

		boolean equalsNotChanged(final File candidate) {
			if (candidate.equals(copy) == false) {
				return false;
			} else if (candidate.length() != copy.length()) {
				return false;
			}
			return true;
		}

	}

	private static final Predicate<CopiedFileReference> isSameSize = c -> c.sameSize;

	private void updateStatus() {
		if (lastCopyError != null) {
			status.set("Error: " + lastCopyError.getMessage());
			currentResumeStatus = FileEntryStatus.ERROR_OR_INCOMPLETE;
		} else if (copiesByDestination.isEmpty()) {
			status.setValue(Messages.getString("fileEntryStatusNew"));
			currentResumeStatus = FileEntryStatus.NOT_STARTED;
		} else if (integrityAllStates != IntegrityAllState.NOT_CHECKED) {
			if (integrityAllStates == IntegrityAllState.ALL_VALID) {
				status.setValue(String.format(Messages.getString("fileEntryStatusDoneCheck"), copiesByDestination.size()));
				currentResumeStatus = FileEntryStatus.INTEGRITY_VALID;
			} else {
				status.setValue(String.format(Messages.getString("fileEntryStatusDoneCorrupted"), copiesByDestination.size()));
				currentResumeStatus = FileEntryStatus.INTEGRITY_INVALID;
			}
		} else {
			final boolean isNotOnError = copiesByDestination.values().stream().allMatch(isSameSize);
			if (copiesByDestination.size() == destsList.size()) {
				if (isNotOnError) {
					status.setValue(String.format(Messages.getString("fileEntryStatusDone"), copiesByDestination.size()));
					currentResumeStatus = FileEntryStatus.ALL_COPIES_DONE;
				} else {
					final long inError = copiesByDestination.values().stream().filter(isSameSize.negate()).count();
					status.setValue(String.format(Messages.getString("fileEntryStatusWithError"), inError, copiesByDestination.size()));
					currentResumeStatus = FileEntryStatus.ERROR_OR_INCOMPLETE;
				}
			} else if (isNotOnError) {
				status.setValue(String.format(Messages.getString("fileEntryStatusPartial"), copiesByDestination.size(), destsList.size()));
				currentResumeStatus = FileEntryStatus.PARTIAL_DONE;
			} else {
				final long inError = copiesByDestination.values().stream().filter(isSameSize.negate()).count();
				status.setValue(String.format(Messages.getString("fileEntryStatusPartialWithError"), inError, copiesByDestination.size(), destsList.size()));
				currentResumeStatus = FileEntryStatus.ERROR_OR_INCOMPLETE;
			}
		}
	}

	public FileEntryStatus getCurrentResumeStatus() {
		return currentResumeStatus;
	}

	public void updateCopyProgression(final long currentEtaMsec, final long meanSpeed, final long readedBytes, final Optional<IOException> lastError) {
		lastError.ifPresentOrElse(e -> {
			synchronized (this) {
				lastCopyError = e;
			}
			status.set("Error: " + e.getMessage());
			currentResumeStatus = FileEntryStatus.ERROR_OR_INCOMPLETE;
		}, () -> {
			final String readed = MainApp.byteCountToDisplaySizeWithPrecision(readedBytes);
			final String speed = MainApp.byteCountToDisplaySizeWithPrecision(meanSpeed);

			final String eta;
			if (currentEtaMsec < 1) {
				eta = "00:00:00";
			} else {
				eta = DurationFormatUtils.formatDuration(currentEtaMsec, "HH:mm:ss");
			}

			status.set(String.format(Messages.getString("fileEntryStatusProgress"), readed, speed, eta));
		});
	}

	public static final Predicate<FileEntry> needsToBeCopied = fileEntry -> {
		if (fileEntry.copiesByDestination.isEmpty()) {
			return true;
		} else {
			return fileEntry.copiesByDestination.size() != fileEntry.destsList.size();
		}
	};

	public List<DestinationEntrySlot> getToCopyDestinationSlotList() {
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

	@Override
	public List<Entry> getTargetedFileEntries() {
		final String messageDest = Messages.getString("tableContextDestFile");

		final Stream<Entry> sourceEntry = Stream.of(new Entry(Messages.getString("tableContextSourceFile"), file));

		final Stream<Entry> destEntries = destsList.stream().filter(copiesByDestination::containsKey).map(dest -> {
			final CopiedFileReference ref = copiesByDestination.get(dest);
			return new Entry(String.format(messageDest, dest.rootPath.getPath()), ref.copy, ref.sameSize == false);
		});

		return Stream.concat(sourceEntry, destEntries).collect(Collectors.toUnmodifiableList());
	}

	public void setDigest(final String computedDigest) {
		this.computedDigest = computedDigest;
	}

	public String getDigest() {
		return computedDigest;
	}

	public synchronized void setAllCopiesIntegrity(final IntegrityAllState integrityAllStates) {
		if (this.integrityAllStates != IntegrityAllState.NOT_CHECKED) {
			if (this.integrityAllStates != integrityAllStates) {
				throw new RuntimeException("Can't update twice integrity status for " + file + " (" + this.integrityAllStates + " is not " + integrityAllStates + ")");
			}
			return;
		}
		this.integrityAllStates = integrityAllStates;
		updateStatus();
	}

}
