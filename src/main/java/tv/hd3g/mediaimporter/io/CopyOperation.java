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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import tv.hd3g.mediaimporter.DestinationEntrySlot;
import tv.hd3g.mediaimporter.FileEntry;
import tv.hd3g.mediaimporter.MainApp;

public class CopyOperation {
	private static Logger log = LogManager.getLogger();

	private static final Set<OpenOption> OPEN_OPTIONS_READ_ONLY = Set.of(StandardOpenOption.READ);
	private static final Set<OpenOption> OPEN_OPTIONS_WRITE_NEW = Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
	private static final String suffixCopyFileName = "-oncopy";

	private final Path source;
	private final FileEntry entryToCopy;
	private final List<DestinationEntrySlot> destinationListToCopy;
	private final List<ByteBuffer> bufferPool;
	private final Executor writeExecutor;
	private volatile boolean wantToStop;
	private final CopyStat copyStat;

	CopyOperation(final FileEntry entryToCopy, final ByteBuffer bufferA, final ByteBuffer bufferB, final Executor writeExecutor) throws IOException {
		this.entryToCopy = entryToCopy;
		bufferPool = new ArrayList<>(Arrays.asList(bufferA, bufferB));

		this.writeExecutor = writeExecutor;
		wantToStop = false;
		source = entryToCopy.getFile().toPath();
		destinationListToCopy = entryToCopy.getToCopyDestinationSlotList();
		copyStat = new CopyStat(this, entryToCopy.getFile().length());
	}

	List<DestinationEntrySlot> getDestinationListToCopy() {
		return destinationListToCopy;
	}

	public void run() {
		if (destinationListToCopy.isEmpty()) {
			return;
		}
		copyStat.onStart();
		log.info("Start to copy " + entryToCopy + " (" + MainApp.byteCountToDisplaySizeWithPrecision(entryToCopy.getFile().length()) + ") to " + destinationListToCopy.size() + " destination(s)");

		final Map<Path, DestinationEntrySlot> slotsToCopyByPath = destinationListToCopy.stream().collect(Collectors.toUnmodifiableMap(slot -> {
			final String relativePath = entryToCopy.getRelativePath();
			final File fileDestination = slot.makePathFromRelativePath(entryToCopy.getDriveSNValue(), relativePath);

			try {
				final String sourceFullPath = entryToCopy.getFile().getAbsolutePath();
				final String sourceBasePath = sourceFullPath.substring(0, sourceFullPath.length() - relativePath.length());
				log.trace("Prepare to create dir struct from {} to {}", sourceBasePath, relativePath);

				final Iterator<Path> relativePathIterator = Path.of(relativePath).iterator();
				File currentSourcePath = new File(sourceBasePath);
				File currentDestPath = slot.makePathFromRelativePath(entryToCopy.getDriveSNValue(), "");
				FileUtils.forceMkdir(currentDestPath);

				while (relativePathIterator.hasNext()) {
					final String pathPart = relativePathIterator.next().toFile().getPath();
					currentSourcePath = new File(currentSourcePath.getPath() + File.separator + pathPart);
					if (currentSourcePath.isDirectory() == false) {
						break;
					}
					currentDestPath = new File(currentDestPath.getPath() + File.separator + pathPart);
					if (currentDestPath.exists() && currentDestPath.isDirectory()) {
						break;
					}
					log.trace("Create dir and set source date from {} to {}", currentSourcePath, currentDestPath);
					FileUtils.forceMkdir(currentDestPath);
					currentDestPath.setLastModified(currentSourcePath.lastModified());
				}

				return fileDestination.toPath();
			} catch (final IOException e) {
				copyStat.setLastException(e);
				throw new RuntimeException("Can't prepare copy operation to " + fileDestination, e);
			}
		}, slot -> slot));

		final Map<FileChannel, DestinationEntrySlot> slotByFileChannel = new LinkedHashMap<>();
		final Map<FileChannel, Path> pathByFileChannel = new LinkedHashMap<>();

		try (final FileChannel sourceChannel = FileChannel.open(source, OPEN_OPTIONS_READ_ONLY)) {
			for (final Map.Entry<Path, DestinationEntrySlot> entry : slotsToCopyByPath.entrySet()) {
				entry.getValue().addLogHistoryOnStartsCopy(source.toFile(), entry.getKey().toFile());

				final File tempFile = new File(entry.getKey().toFile().getPath() + suffixCopyFileName);
				FileUtils.forceMkdir(tempFile.getParentFile());

				final FileChannel destination = FileChannel.open(tempFile.toPath(), OPEN_OPTIONS_WRITE_NEW);
				slotByFileChannel.put(destination, entry.getValue());
				pathByFileChannel.put(destination, entry.getKey());
			}

			final List<Entry<FileChannel, DestinationEntrySlot>> allFileChannelSlots = slotByFileChannel.entrySet().stream().collect(Collectors.toUnmodifiableList());

			long lastLoopDateNanoSec = System.nanoTime();

			ByteBuffer currentBuffer = bufferPool.get(0);
			currentBuffer.clear();

			List<CompletableFuture<?>> writers = Collections.emptyList();
			while (sourceChannel.read(currentBuffer) > 0) {
				if (wantToStop) {
					return;
				}
				currentBuffer.flip();
				copyStat.onReadWriteLoop(currentBuffer.remaining(), System.nanoTime() - lastLoopDateNanoSec);

				/**
				 * Wait to ends writes
				 */
				writers.forEach(cf -> {
					try {
						cf.get();
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
				});

				final ByteBuffer currentWriteBuffer = currentBuffer;
				/**
				 * Parallel writes
				 */
				writers = allFileChannelSlots.stream().map(entry -> {
					return CompletableFuture.runAsync(() -> {
						if (wantToStop) {
							return;
						}
						try {
							final long timeBeforeWrite = System.nanoTime();
							final int sizeWrited = entry.getKey().write(currentWriteBuffer.asReadOnlyBuffer());
							copyStat.onWrite(entry.getValue(), sizeWrited, System.nanoTime() - timeBeforeWrite);
						} catch (final IOException e) {
							throw new RuntimeException(e);
						}
					}, writeExecutor);
				}).collect(Collectors.toUnmodifiableList());

				Collections.reverse(bufferPool);
				currentBuffer = bufferPool.get(0);
				currentBuffer.clear();

				lastLoopDateNanoSec = System.nanoTime();
			}

			/*for (final FileChannel destinationChannel : destinationChannels.keySet()) {
				destinationChannel.force(true);
			}*/
		} catch (final IOException e) {
			log.error("Can't process copy with " + source, e);
			copyStat.setLastException(e);
		} catch (final RuntimeException e) {
			if (e.getCause() instanceof IOException) {
				log.error("Can't process copy with " + source, e.getCause());
				copyStat.setLastException((IOException) e.getCause());
			} else {
				log.warn("Generic error for " + source, e);
			}
		} catch (final Throwable e) {
			log.warn("Generic error for " + source, e);
		} finally {
			copyStat.onEnd();
			for (final Map.Entry<FileChannel, Path> entry : pathByFileChannel.entrySet()) {
				try {
					entry.getKey().close();

					if (wantToStop == false) {
						final File expectedFile = pathByFileChannel.get(entry.getKey()).toFile();
						final File realCopiedFile = new File(expectedFile.getPath() + suffixCopyFileName);
						FileUtils.moveFile(realCopiedFile, expectedFile);
					}
				} catch (final IOException e) {
					log.warn("Can't close file " + entry.getValue() + suffixCopyFileName, e);
					copyStat.setLastException(e);
				}
			}
		}

		for (final Map.Entry<FileChannel, DestinationEntrySlot> entry : slotByFileChannel.entrySet()) {
			entry.getValue().addLogHistoryOnEndCopy(pathByFileChannel.get(entry.getKey()).toFile());
		}

		Platform.runLater(() -> {
			entryToCopy.updateState();
		});

		final long lastModified = source.toFile().lastModified();
		slotsToCopyByPath.keySet().stream().forEach(path -> {
			path.toFile().setLastModified(lastModified);
		});
	}

	public long getSourceLength() {
		return source.toFile().length();
	}

	public Path getSourcePath() {
		return source;
	}

	public void switchStop() {
		wantToStop = true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (source == null ? 0 : source.hashCode());
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
		if (!(obj instanceof CopyOperation)) {
			return false;
		}
		final CopyOperation other = (CopyOperation) obj;
		if (source == null) {
			if (other.source != null) {
				return false;
			}
		} else if (!source.equals(other.source)) {
			return false;
		}
		return true;
	}

	public FileEntry getFileEntry() {
		return entryToCopy;
	}

	public CopyStat getCopyStat() {
		return copyStat;
	}
}
