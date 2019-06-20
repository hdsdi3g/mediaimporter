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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DestinationEntrySlot {
	private static Logger log = LogManager.getLogger();
	private static final Set<OpenOption> OPEN_OPTIONS_WRITE = Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

	private final File slotRootDir;
	private final DestinationEntry referer;

	DestinationEntrySlot(final DestinationEntry referer, final File dir) {
		this.referer = referer;
		slotRootDir = Objects.requireNonNull(dir, "\"slotRootDir\" can't to be null");
	}

	File getDir() {
		return slotRootDir;
	}

	public File makePathFromRelativePath(final String driveSNValue, final String relativePath) {
		return new File(slotRootDir.getPath() + File.separator + driveSNValue + File.separator + relativePath);
	}

	public DestinationEntry getDestination() {
		return referer;
	}

	public void updateWriteSpeed() {
		if (referer.getCopiedDurationsNanoSec().get() > 0) {
			referer.getWriteSpeed().set(Math.round((double) referer.getCopiedDatasBytes().get() / (double) referer.getCopiedDurationsNanoSec().get() * 1_000_000_000d));
		} else {
			referer.getWriteSpeed().set(0);
		}
	}

	public AtomicLong getCopiedDatasBytes() {
		return referer.getCopiedDatasBytes();
	}

	public AtomicLong getCopiedDurationsNanoSec() {
		return referer.getCopiedDurationsNanoSec();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + referer.hashCode();
		result = prime * result + (slotRootDir == null ? 0 : slotRootDir.hashCode());
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
		if (!(obj instanceof DestinationEntrySlot)) {
			return false;
		}
		final DestinationEntrySlot other = (DestinationEntrySlot) obj;
		if (!referer.equals(other.referer)) {
			return false;
		}
		if (slotRootDir == null) {
			if (other.slotRootDir != null) {
				return false;
			}
		} else if (!slotRootDir.equals(other.slotRootDir)) {
			return false;
		}
		return true;
	}

	private static final SimpleDateFormat historyLogDisplay = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss,SSS");

	private void writeHistoryLog(final String text, final long date) {
		final File historyFile = new File(slotRootDir.getPath() + File.separator + "history.log");

		try (final FileChannel logFile = FileChannel.open(historyFile.toPath(), OPEN_OPTIONS_WRITE)) {
			if (logFile.size() > 0) {
				logFile.position(logFile.size());
			}
			logFile.write(ByteBuffer.wrap(historyLogDisplay.format(new Date(date)).concat("\t").getBytes(StandardCharsets.UTF_8)));
			logFile.write(ByteBuffer.wrap(text.concat(System.lineSeparator()).getBytes(StandardCharsets.UTF_8)));
		} catch (final IOException e) {
			log.error("Can't write log history file", e);
		}
	}

	public void addLogHistoryOnStartsCopy(final File source, final File dest) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Start copy \"");
		sb.append(source.getPath());
		sb.append("\" (");
		sb.append(source.length());
		sb.append(" bytes)");
		sb.append(System.lineSeparator());
		sb.append("\tto \"");
		sb.append(dest.getPath());
		sb.append("\"");

		writeHistoryLog(sb.toString(), System.currentTimeMillis());
	}

	public void addLogHistoryOnEndCopy(final File dest) {
		writeHistoryLog("Copy done" + System.lineSeparator(), System.currentTimeMillis());
	}

	public void addLogHistoryOnEndAllCopies(final long dataSize, final long duration) {
		if (dataSize < 1 || duration < 1) {
			return;
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("Total copy ");
		sb.append(dataSize);
		sb.append(" bytes (");
		sb.append(MainApp.byteCountToDisplaySizeWithPrecision(dataSize));
		sb.append(") in ");
		sb.append(DurationFormatUtils.formatDuration(duration, "HH:mm:ss"));
		sb.append(" (");
		sb.append(MainApp.byteCountToDisplaySizeWithPrecision(Math.round(dataSize / duration * 1000d)));
		sb.append("/sec)");

		writeHistoryLog(sb.toString(), System.currentTimeMillis());
	}

	public void addComputedDigestToListFile(final File dest, final Map<String, String> digestByAlgorithm) {
		final var destPath = dest.getAbsolutePath();
		final var slotDirPath = slotRootDir.getAbsolutePath();
		if (dest.isDirectory() | dest.exists() == false | dest.equals(slotRootDir) | destPath.startsWith(slotDirPath) == false) {
			throw new RuntimeException("Invalid file dest: " + dest);
		}

		final var relativeDestPath = destPath.substring(slotDirPath.length() + 1).replaceAll("\\\\", "/");

		digestByAlgorithm.forEach((algorithmName, digest) -> {
			final var digestName = algorithmName.toUpperCase().replaceAll("-", "");
			final var digestToListFile = Path.of(slotDirPath, digestName + "SUM");

			try (final FileChannel logFile = FileChannel.open(digestToListFile, OPEN_OPTIONS_WRITE)) {
				if (logFile.size() > 0) {
					logFile.position(logFile.size());
				}
				final var line = digest + "  " + relativeDestPath + "\n";
				logFile.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
			} catch (final IOException e) {
				log.error("Can't write digest list file", e);
			}
		});
	}

}
