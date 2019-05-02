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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.mediaimporter.DestinationEntry.Slot;
import tv.hd3g.mediaimporter.FileEntry;

public class CopyOperation implements Runnable {
	private static Logger log = LogManager.getLogger();

	private static final Set<OpenOption> OPEN_OPTIONS_READ_ONLY = Set.of(StandardOpenOption.READ);
	private static final Set<OpenOption> OPEN_OPTIONS_READ_WRITE_NEW = Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

	private final FileEntry entryToCopy;
	private final List<Slot> destinationListToCopy;
	private final ByteBuffer buffer;
	private volatile boolean wantToStop;

	private final Path source;

	public CopyOperation(final FileEntry entryToCopy) throws IOException {
		this.entryToCopy = entryToCopy;
		wantToStop = false;
		source = entryToCopy.getFile().toPath();
		buffer = ByteBuffer.allocateDirect((int) Files.getFileStore(source).getBlockSize() * 32);
		destinationListToCopy = entryToCopy.getToCopyDestinationSlotList();
	}

	@Override
	public void run() {
		if (destinationListToCopy.isEmpty()) {
			return;
		}

		log.info("Start to copy " + entryToCopy + " (" + FileUtils.byteCountToDisplaySize(entryToCopy.getFile().length()) + ") to " + destinationListToCopy.size() + " destination(s)");

		final Map<Path, Slot> slotsToCopyByPath = destinationListToCopy.stream().collect(Collectors.toUnmodifiableMap(slot -> {
			final File fileDestination = slot.makePathFromRelativePath(entryToCopy.getDriveSNValue(), entryToCopy.getRelativePath());
			try {
				FileUtils.forceMkdirParent(fileDestination);
			} catch (final IOException e) {
				throw new RuntimeException("Can't prepare copy operation to " + fileDestination, e);
				// TODO verbose in UI
			}
			return fileDestination.toPath();
		}, slot -> slot));

		final Map<FileChannel, Path> destinationChannels = new LinkedHashMap<>();
		try (final FileChannel sourceChannel = FileChannel.open(source, OPEN_OPTIONS_READ_ONLY)) {

			for (final Path destinationPath : slotsToCopyByPath.keySet()) {
				destinationChannels.put(FileChannel.open(destinationPath, OPEN_OPTIONS_READ_WRITE_NEW), destinationPath);
			}

			while (sourceChannel.read(buffer) > 0) {
				if (wantToStop == true) {
					return;
				}
				buffer.flip();
				final int toRead = buffer.remaining();// TODO display progression in UI

				for (final FileChannel destinationChannel : destinationChannels.keySet()) {
					destinationChannel.write(buffer.asReadOnlyBuffer());
					if (wantToStop == true) {
						return;
					}
				}
				buffer.clear();
			}

			for (final FileChannel destinationChannel : destinationChannels.keySet()) {
				destinationChannel.force(true);
			}
		} catch (final IOException e) {
			log.error("Can't open source file " + source, e); // TODO display in UI
		} finally {
			for (final FileChannel destinationChannel : destinationChannels.keySet()) {
				try {
					destinationChannel.close();
				} catch (final IOException e) {
					log.warn("Can't close file " + destinationChannels.get(destinationChannel), e); // TODO display in UI
				}
			}
		}
		buffer.asReadOnlyBuffer();
	}

	public void switchStop() {
		wantToStop = true;
	}

}
