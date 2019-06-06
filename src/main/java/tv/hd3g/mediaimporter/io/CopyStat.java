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
import java.util.Optional;

import tv.hd3g.mediaimporter.DestinationEntrySlot;
import tv.hd3g.mediaimporter.FileEntry;

public class CopyStat {
	private final CopyOperation referer;

	private final long sourceFileSizeBytes;

	private long startDateMsec;
	private long lastLoopDateMsec;
	private long totalReadedBytes;
	private long endDateMsec;

	private long lastReadWriteLoopReadedBytes;
	private long lastReadWriteLoopTimeNanoSec;

	private IOException lastException;

	CopyStat(final CopyOperation referer, final long sourceFileSizeBytes) {
		this.referer = referer;
		this.sourceFileSizeBytes = sourceFileSizeBytes;
		startDateMsec = -1;
		endDateMsec = -1;
	}

	FileEntry getFileEntry() {
		return referer.getFileEntry();
	}

	CopyOperation getReferer() {
		return referer;
	}

	synchronized void onReadWriteLoop(final long loopReadedBytes, final long loopTimeNanoSec) {
		lastLoopDateMsec = System.currentTimeMillis();
		totalReadedBytes += loopReadedBytes;
		lastReadWriteLoopReadedBytes = loopReadedBytes;
		lastReadWriteLoopTimeNanoSec = loopTimeNanoSec;
	}

	synchronized void onStart() {
		startDateMsec = System.currentTimeMillis();
	}

	synchronized void onEnd() {
		endDateMsec = System.currentTimeMillis();
	}

	void onWrite(final DestinationEntrySlot currentSlot, final long datasBytes, final long durationNanoSec) {
		currentSlot.getCopiedDatasBytes().addAndGet(datasBytes);
		currentSlot.getCopiedDurationsNanoSec().addAndGet(durationNanoSec);
	}

	synchronized CopyStat setLastException(final IOException lastException) {
		this.lastException = lastException;
		return this;
	}

	public synchronized long getTotalReadedBytes() {
		return totalReadedBytes;
	}

	public long getSourceFileSizeBytes() {
		return sourceFileSizeBytes;
	}

	public synchronized boolean isDone() {
		return totalReadedBytes == sourceFileSizeBytes || lastException != null;
	}

	public synchronized boolean isStarted() {
		return startDateMsec > 0;
	}

	public synchronized boolean isWaiting() {
		return startDateMsec == -1;
	}

	public synchronized long getStartDateMsec() {
		return startDateMsec;
	}

	public synchronized long getEndDateMsec() {
		return endDateMsec;
	}

	public synchronized long getLastUpdateDateMsec() {
		if (isStarted() == false) {
			throw new RuntimeException("Can't get result before copy starts");
		}
		return lastLoopDateMsec;
	}

	public synchronized long getInstantSpeedBytesPerSec() {
		if (isStarted() == false) {
			throw new RuntimeException("Can't get result before copy starts");
		}
		return Math.round((double) lastReadWriteLoopReadedBytes / (double) lastReadWriteLoopTimeNanoSec * 1_000_000_000d);
	}

	public synchronized long getMeanSpeedBytesPerSec() {
		if (isStarted() == false) {
			throw new RuntimeException("Can't get result before copy starts");
		}
		return Math.round(totalReadedBytes / (double) (lastLoopDateMsec - startDateMsec) * 1000d);
	}

	public synchronized long getETAMsec() {
		if (isStarted() == false) {
			throw new RuntimeException("Can't get result before copy starts");
		}
		final long meanSpeed = getMeanSpeedBytesPerSec();
		if (meanSpeed == 0) {
			return 0;
		}
		return Math.round((sourceFileSizeBytes - totalReadedBytes) / meanSpeed * 1000d) + 1000;
	}

	public synchronized Optional<IOException> getLastException() {
		return Optional.ofNullable(lastException);
	}
}
