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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.mediaimporter.DestinationEntry;
import tv.hd3g.mediaimporter.FileEntry;
import tv.hd3g.mediaimporter.MainApp;

public class CopyFilesEngine {
	private static Logger log = LogManager.getLogger();

	private final List<CopyOperation> copyList;
	final List<DestinationEntry> allDestinations;
	private final ThreadPoolExecutor mainExecutor;
	private final ThreadPoolExecutor writeExecutor;
	private CompletableFuture<?> allTasks;

	private final long dataSizeToCopyBytes;
	private final GlobalCopyStat globalCopyStat;

	private volatile boolean wantToStop;

	/**
	 * Not reusable
	 */
	public CopyFilesEngine(final List<FileEntry> toCopy, final List<DestinationEntry> allDestinations, final MainApp ui) {
		this.allDestinations = allDestinations;

		mainExecutor = new ThreadPoolExecutor(1, 1, 10l, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("CopyOperation");
			return t;
		});

		final AtomicLong counter = new AtomicLong();
		final int size = Math.min(allDestinations.size(), Runtime.getRuntime().availableProcessors());
		writeExecutor = new ThreadPoolExecutor(size, size, 1l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("Write #" + counter.getAndIncrement());
			return t;
		});

		// final int baseBufferSize = (int) Files.getFileStore(source).getBlockSize();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(256 * 256 * 256 * 4);// 67 108 864 bytes

		copyList = toCopy.stream().map(fileEntry -> {
			try {
				return new CopyOperation(fileEntry, buffer, writeExecutor);
			} catch (final IOException e) {
				throw new RuntimeException("Can't prepare copy operation with " + fileEntry, e);
			}
		}).collect(Collectors.toUnmodifiableList());

		globalCopyStat = new GlobalCopyStat(copyList.stream().map(CopyOperation::getCopyStat).collect(Collectors.toUnmodifiableList()), ui);

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

		allTasks = CompletableFuture.runAsync(() -> {
			copyList.stream().filter(cL -> wantToStop == false).forEach(CopyOperation::run);
		}, mainExecutor);

		final ScheduledFuture<?> regularUIUpdaterFuture = Executors.newScheduledThreadPool(1, r -> {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("Display onProgress");
			return t;
		}).scheduleAtFixedRate(() -> {
			globalCopyStat.refresh();
		}, 300, 300, TimeUnit.MILLISECONDS);

		allTasks.whenCompleteAsync((ok, error) -> {
			regularUIUpdaterFuture.cancel(false);
			if (error == null) {
				final long duration = globalCopyStat.getEndDate() - globalCopyStat.getSetStartDate();
				globalCopyStat.getSlotList().forEach(slot -> {
					slot.addLogHistoryOnEndAllCopies(dataSizeToCopyBytes, duration);
				});
			}
		});
		return allTasks;
	}

	/**
	 * Non-blocking
	 */
	public void asyncStop(final Runnable onDone) {
		wantToStop = true;

		if (mainExecutor.getActiveCount() == 0) {
			mainExecutor.shutdown();
			writeExecutor.shutdown();
			onDone.run();
			return;
		}
		log.info("Set to stop current copy queue: " + mainExecutor.getQueue().size() + " item(s)");
		allTasks.completeExceptionally(new Exception("Manual stop operation"));

		mainExecutor.getQueue().clear();
		writeExecutor.getQueue().clear();

		copyList.forEach(copyOperation -> {
			copyOperation.switchStop();
		});
		CompletableFuture.runAsync(onDone, mainExecutor).thenAcceptAsync(v -> {
			mainExecutor.shutdown();
			writeExecutor.shutdown();
		});
	}

}
