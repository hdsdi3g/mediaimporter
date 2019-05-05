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
package tv.hd3g.mediaimporter.io;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.application.Platform;
import tv.hd3g.mediaimporter.DestinationEntry.Slot;
import tv.hd3g.mediaimporter.MainApp;

public class GlobalCopyStat {
	private final List<CopyStat> items;
	private final List<Slot> slotList;

	private final MainApp ui;
	private final int totalFiles;
	private final long totalDatasBytes;
	private final long startDate;

	GlobalCopyStat(final List<CopyStat> items, final MainApp ui) {
		this.items = items;
		this.ui = ui;
		totalFiles = items.size();
		totalDatasBytes = items.stream().mapToLong(CopyStat::getSourceFileSizeBytes).sum();
		startDate = -1;

		slotList = items.stream().map(CopyStat::getReferer).map(CopyOperation::getDestinationListToCopy).flatMap(slotList -> slotList.stream()).distinct().collect(Collectors.toUnmodifiableList());
	}

	private synchronized long getSetStartDate() {
		if (startDate == -1) {
			return items.stream().filter(CopyStat::isStarted).mapToLong(CopyStat::getStartDateMsec).min().orElse(-1);
		}
		return startDate;
	}

	void refresh() {
		final int filesCopied = (int) items.stream().filter(CopyStat::isDone).count();
		final long datasCopiedBytes = items.stream().mapToLong(CopyStat::getTotalReadedBytes).sum();
		final double progressRate = (double) datasCopiedBytes / (double) totalDatasBytes;
		final long timeElapsedMsec = items.stream().filter(CopyStat::isStarted).mapToLong(CopyStat::getLastUpdateDateMsec).max().orElse(-1l) - getSetStartDate();
		final double meanCopySpeedBytesPerSec = datasCopiedBytes / (double) timeElapsedMsec * 1000d;
		final Optional<CopyStat> currentCopyStat = items.stream().filter(CopyStat::isStarted).filter(Predicate.not(CopyStat::isDone)).findFirst();
		final long instantCopySpeedBytesPerSec = currentCopyStat.map(CopyStat::getInstantSpeedBytesPerSec).orElse(0l);
		final long etaMsec = Math.round((totalDatasBytes - datasCopiedBytes) / meanCopySpeedBytesPerSec * 1000d);

		Platform.runLater(() -> {
			currentCopyStat.ifPresent(copyStat -> {
				final long currentEtaMsec = copyStat.getETAMsec();
				final long meanSpeed = copyStat.getMeanSpeedBytesPerSec();
				final long readedBytes = copyStat.getTotalReadedBytes();
				final Optional<IOException> lastError = copyStat.getLastException();
				copyStat.getFileEntry().updateCopyProgression(currentEtaMsec, meanSpeed, readedBytes, lastError);
			});
			slotList.forEach(dest -> {
				dest.updateWriteSpeed();
			});
			ui.updateProgress(progressRate, filesCopied, totalFiles, datasCopiedBytes, totalDatasBytes, etaMsec, Math.round(meanCopySpeedBytesPerSec), instantCopySpeedBytesPerSec);
		});

	}
	/*TODO clean
	, onProgress -> {
						Platform.runLater(() -> {
							updateStateOnGlobalProgress(onProgress.globalCopyProgress);
							onProgress.fileCopyProgress.updateFileEntryStatus();

							for (final Map.Entry<DestinationEntry, Long> entry : onProgress.writeSpeedByDestination.entrySet()) {
								entry.getKey().getWriteSpeed().set(entry.getValue());
							}
						});
					} * */

	/*lastCopyProgressSendedToDisplay = new LinkedBlockingDeque<>();
	lastFileCopyProgressSendedToDisplay = new LinkedBlockingDeque<>();

	final BiConsumer<CopyOperation, Integer> onCopyProgress = (copyOperation, readSize) -> {
		final long dateMsec = System.currentTimeMillis();
		final long dateNanoSec = System.nanoTime();
		progressExecutor.execute(() -> {
			lastCopyProgressSendedToDisplay.addLast(new GlobalCopyProgress(readSize.longValue(), dateMsec, dateNanoSec, copyOperation));
			lastFileCopyProgressSendedToDisplay.addLast(new FileCopyProgress(copyOperation.getFileEntry(), readSize.longValue(), copyOperation.getStartDate(), dateMsec));
		});
	};*/

	/*copiedDatasByDestination = Collections.unmodifiableMap(allDestinations.stream().collect(Collectors.toMap(dest -> dest, dest -> new AtomicLong(0))));
	copiedDurationsByDestination = Collections.unmodifiableMap(allDestinations.stream().collect(Collectors.toMap(dest -> dest, dest -> new AtomicLong(0))));
	final Consumer<OnWrite> onWrite = lastWrite -> {
		progressExecutor.execute(() -> {
			final DestinationEntry dest = lastWrite.slot.getDestination();
			copiedDatasByDestination.get(dest).addAndGet(lastWrite.writedBytes);
			copiedDurationsByDestination.get(dest).addAndGet(lastWrite.durationNanoSec);
		});
	};*/
	/*final GlobalCopyProgress lastGlobalProgress = lastCopyProgressSendedToDisplay.pollLast();
	if (lastGlobalProgress == null) {
		return;
	}
	final List<FileCopyProgress> lastFileCopyProgressEvents = new ArrayList<>();
	lastFileCopyProgressSendedToDisplay.drainTo(lastFileCopyProgressEvents);

	FileCopyProgress lastProgress = new FileCopyProgress(fileEntry, lastReadedPositionBytes, startDateMsec, dateMsec);
	lastFileCopyProgressEvents.stream().mapToLong(fc -> fc.);

	onProgressUpdate.accept(new ProgressUpdate(lastGlobalProgress, lastProgress));
	lastCopyProgressSendedToDisplay.clear();
	lastFileCopyProgressSendedToDisplay.clear();*/
	/*public class GlobalCopyProgress {

		private final int positionInList;
		private final long positionBytes;
		// private final long dateMsec;
		private final long dateNanoSec;
		private final long timeElapsedMsec;

		private GlobalCopyProgress(final long dataSizeReadForLastLoopBytes, final long dateMsec, final long dateNanoSec, final CopyOperation currentOperation) {
			positionInList = copyList.indexOf(currentOperation);

			// this.dateMsec = dateMsec;
			this.dateNanoSec = dateNanoSec;
			timeElapsedMsec = dateMsec - startTimeMsec;

			if (lastGlobalCopyProgress == null) {
				positionBytes = dataSizeReadForLastLoopBytes;
			}
			lastGlobalCopyProgress = this;
		}
	}*/

	/*public class FileCopyProgress {
		private final FileEntry fileEntry;
		private final long lastReadedPositionBytes;
		private final long startDateMsec;
		private final long dateMsec;

		private FileCopyProgress(final FileEntry fileEntry, final long lastReadedPositionBytes, final long startDateMsec, final long dateMsec) {
			this.fileEntry = fileEntry;
			this.lastReadedPositionBytes = lastReadedPositionBytes;
			this.startDateMsec = startDateMsec;
			this.dateMsec = dateMsec;
		}

		public void updateFileEntryStatus() {
			fileEntry.updateCopyProgression(lastReadedPositionBytes, dateMsec - startDateMsec);
		}
	}

	public class ProgressUpdate {
		public final GlobalCopyProgress globalCopyProgress;
		public final FileCopyProgress fileCopyProgress;
		**
		 * Bytes/sec
		 *
		public final Map<DestinationEntry, Long> writeSpeedByDestination;

		private ProgressUpdate(final GlobalCopyProgress globalCopyProgress, final FileCopyProgress fileCopyProgress) {
			this.globalCopyProgress = globalCopyProgress;
			this.fileCopyProgress = fileCopyProgress;

			writeSpeedByDestination = Collections.unmodifiableMap(allDestinations.stream().collect(Collectors.toMap(destination -> {
				return destination;
			}, destination -> {
				final long datasBytes = copiedDatasByDestination.get(destination).get();
				final long durationNanoSec = copiedDurationsByDestination.get(destination).get();
				return Math.round((double) datasBytes / (double) durationNanoSec * 1_000_000_000d);
			})));
		}
	}*/

}
