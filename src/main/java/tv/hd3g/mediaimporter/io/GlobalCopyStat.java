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
import tv.hd3g.mediaimporter.DestinationEntrySlot;
import tv.hd3g.mediaimporter.MainApp;

public class GlobalCopyStat {
	private final List<CopyStat> items;
	private final List<DestinationEntrySlot> slotList;

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

	synchronized long getSetStartDate() {
		if (startDate == -1) {
			return items.stream().filter(CopyStat::isStarted).mapToLong(CopyStat::getStartDateMsec).min().orElse(-1);
		}
		return startDate;
	}

	synchronized long getEndDate() {
		return items.stream().filter(CopyStat::isStarted).mapToLong(CopyStat::getEndDateMsec).max().orElse(-1);
	}

	void refresh() {
		final int filesCopied = (int) items.stream().filter(CopyStat::isDone).count();
		final long datasCopiedBytes = items.stream().mapToLong(CopyStat::getTotalReadedBytes).sum();
		final double progressRate = (double) datasCopiedBytes / (double) totalDatasBytes;
		final long timeElapsedMsec = items.stream().filter(CopyStat::isStarted).mapToLong(CopyStat::getLastUpdateDateMsec).max().orElse(-1l) - getSetStartDate();
		final double meanCopySpeedBytesPerSec = datasCopiedBytes / (double) timeElapsedMsec * 1000d;
		final Optional<CopyStat> currentCopyStat = items.stream().filter(CopyStat::isStarted).filter(Predicate.not(CopyStat::isDone)).findFirst();
		final long instantCopySpeedBytesPerSec = currentCopyStat.map(CopyStat::getInstantSpeedBytesPerSec).orElse(0l);
		final long etaMsec = Math.round((totalDatasBytes - datasCopiedBytes) / meanCopySpeedBytesPerSec * 1000d) + 1000;

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

			final long startTimeMsec = System.currentTimeMillis() - getSetStartDate();
			ui.updateProgress(progressRate, filesCopied, totalFiles, datasCopiedBytes, totalDatasBytes, startTimeMsec, etaMsec, Math.round(meanCopySpeedBytesPerSec), instantCopySpeedBytesPerSec);
		});
	}

	List<DestinationEntrySlot> getSlotList() {
		return slotList;
	}

}
