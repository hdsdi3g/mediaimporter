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
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.mediaimporter.FileEntry;

public class CopyFilesEngine {
	private static Logger log = LogManager.getLogger();

	private final List<CopyOperation> copyList;
	private final ThreadPoolExecutor mainExecutor;
	private final ThreadPoolExecutor progressExecutor;
	private CompletableFuture<?> allTasks;

	private final long dataSizeToCopyBytes;
	private long startTimeMsec;
	private volatile GlobalCopyProgress lastGlobalCopyProgress;
	private final LinkedBlockingDeque<GlobalCopyProgress> lastCopyProgressSendedToDisplay;
	private final Consumer<GlobalCopyProgress> onGlobalProgress;

	/**
	 * No reusable
	 */
	public CopyFilesEngine(final List<FileEntry> toCopy, final Consumer<GlobalCopyProgress> onGlobalProgress) {
		this.onGlobalProgress = onGlobalProgress;

		progressExecutor = new ThreadPoolExecutor(1, 1, 1l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
			final Thread t = new Thread(r);
			t.setPriority(Thread.MIN_PRIORITY);
			t.setDaemon(true);
			t.setName("OnProgress");
			return t;
		});

		lastCopyProgressSendedToDisplay = new LinkedBlockingDeque<>();
		final BiConsumer<CopyOperation, Integer> onCopyProgress = (copyOperation, readSize) -> {
			final long dateMsec = System.currentTimeMillis();
			final long dateNanoSec = System.nanoTime();
			progressExecutor.execute(() -> {
				final GlobalCopyProgress copyProgress = new GlobalCopyProgress(readSize.longValue(), dateMsec, dateNanoSec, copyOperation);
				lastCopyProgressSendedToDisplay.addLast(copyProgress);
			});
		};

		copyList = toCopy.stream().map(fileEntry -> {
			try {
				return new CopyOperation(fileEntry, onCopyProgress);
			} catch (final IOException e) {
				throw new RuntimeException("Can't prepare copy operation with " + fileEntry, e);
			}
		}).collect(Collectors.toUnmodifiableList());

		final AtomicLong counter = new AtomicLong();
		mainExecutor = new ThreadPoolExecutor(1, 1, 1l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(toCopy.size()), r -> {
			final Thread t = new Thread(r);
			// t.setPriority(Thread.MIN_PRIORITY);
			t.setDaemon(true);
			t.setName("CopyOperation #" + counter.getAndIncrement());
			return t;
		});
		allTasks = CompletableFuture.failedFuture(new NullPointerException("Never started"));

		dataSizeToCopyBytes = copyList.stream().mapToLong(CopyOperation::getSourceLength).sum();

		copyList.stream().map(CopyOperation::getSourcePath).map(source -> {
			try {
				return Files.getFileStore(source);
			} catch (final IOException e) {
				throw new RuntimeException("Can't prepare copy operation with " + source, e);
			}
		}).distinct().forEach(fileStore -> {
			try {
				if (fileStore.getUsableSpace() < dataSizeToCopyBytes) {
					throw new RuntimeException("Store " + fileStore + " has potentially not enough space for handle all datas to copied");
				}
			} catch (final IOException e) {
				throw new RuntimeException("Can't prepare copy operation with " + fileStore, e);
			}
		});
	}

	/**
	 * Non-blocking
	 */
	public CompletableFuture<?> asyncStart() {
		log.info("Put " + copyList.size() + " item(s) in queue for copy");
		startTimeMsec = System.currentTimeMillis();

		final CompletableFuture<?>[] allOperations = new CompletableFuture<?>[copyList.size()];

		copyList.stream().map(copyOperation -> {
			return CompletableFuture.runAsync(copyOperation, mainExecutor);
		}).collect(Collectors.toUnmodifiableList()).toArray(allOperations);

		allTasks = CompletableFuture.allOf(allOperations);

		final ScheduledFuture<?> regularUIUpdaterFuture = Executors.newScheduledThreadPool(1, r -> {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("Display onProgress");
			return t;
		}).scheduleAtFixedRate(() -> {
			final Optional<GlobalCopyProgress> last = Optional.ofNullable(lastCopyProgressSendedToDisplay.pollLast());
			last.ifPresent(lastGlobalProgress -> {
				onGlobalProgress.accept(lastGlobalProgress);
				lastCopyProgressSendedToDisplay.clear();
			});
		}, 300, 300, TimeUnit.MILLISECONDS);

		allTasks.whenCompleteAsync((ok, error) -> regularUIUpdaterFuture.cancel(false));

		return allTasks;
	}

	/**
	 * Non-blocking
	 */
	public void asyncStop(final Runnable onDone) {
		if (mainExecutor.getActiveCount() == 0) {
			mainExecutor.shutdown();
			onDone.run();
			return;
		}
		log.info("Set to stop current copy queue: " + mainExecutor.getQueue().size() + " item(s)");
		allTasks.completeExceptionally(new Exception("Manual stop operation"));

		mainExecutor.getQueue().clear();
		copyList.forEach(copyOperation -> {
			copyOperation.switchStop();
		});
		CompletableFuture.runAsync(onDone, mainExecutor).thenAcceptAsync(v -> mainExecutor.shutdown());
	}

	public class GlobalCopyProgress {

		private final int positionInList;
		private final double instantSpeedBytesPerSec;
		private final double meanSpeedBytesPerSec;
		private final long positionBytes;
		// private final long dateMsec;
		private final long dateNanoSec;
		private final long timeElapsedMsec;
		private final long etaMsec;

		private GlobalCopyProgress(final long dataSizeReadForLastLoopBytes, final long dateMsec, final long dateNanoSec, final CopyOperation currentOperation) {
			positionInList = copyList.indexOf(currentOperation);

			// this.dateMsec = dateMsec;
			this.dateNanoSec = dateNanoSec;
			timeElapsedMsec = dateMsec - startTimeMsec;

			if (lastGlobalCopyProgress == null) {
				positionBytes = dataSizeReadForLastLoopBytes;
				instantSpeedBytesPerSec = (double) positionBytes / timeElapsedMsec * 1000d;
			} else {
				positionBytes = lastGlobalCopyProgress.positionBytes + dataSizeReadForLastLoopBytes;
				instantSpeedBytesPerSec = Optional.ofNullable(lastCopyProgressSendedToDisplay.peekFirst()).map(first -> {
					return (double) (positionBytes - first.positionBytes) / (double) (dateNanoSec - first.dateNanoSec) * 1_000_000_000d;
				}).orElseGet(() -> {
					return (double) (positionBytes - lastGlobalCopyProgress.positionBytes) / (double) (dateNanoSec - lastGlobalCopyProgress.dateNanoSec) * 1_000_000_000d;
				});
			}
			meanSpeedBytesPerSec = (double) positionBytes / (double) timeElapsedMsec * 1000d;
			lastGlobalCopyProgress = this;
			etaMsec = Math.round((dataSizeToCopyBytes - positionBytes) / meanSpeedBytesPerSec * 1000d);
		}

		public double getProgressRate() {
			return (double) positionBytes / (double) dataSizeToCopyBytes;
		}

		public String getProgressionCounterText(final String format) {
			return String.format(format, positionInList + 1, copyList.size(), FileUtils.byteCountToDisplaySize(positionBytes), FileUtils.byteCountToDisplaySize(dataSizeToCopyBytes));
		}

		public String getETA() {
			return "ETA: " + DurationFormatUtils.formatDuration(etaMsec, "HH:mm:ss");
		}

		public String getSpeedCopy(final String format) {
			return String.format(format, FileUtils.byteCountToDisplaySize(Math.round(meanSpeedBytesPerSec)), FileUtils.byteCountToDisplaySize(Math.round(instantSpeedBytesPerSec)));
		}

	}

}
