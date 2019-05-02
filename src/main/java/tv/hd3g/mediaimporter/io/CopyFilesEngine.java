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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.mediaimporter.FileEntry;

public class CopyFilesEngine {
	private static Logger log = LogManager.getLogger();

	private final List<CopyOperation> copyList;
	private final ThreadPoolExecutor mainExecutor;
	private CompletableFuture<?> allTasks;

	/**
	 * No reusable
	 */
	public CopyFilesEngine(final List<FileEntry> toCopy) {
		copyList = toCopy.stream().map(fileEntry -> {
			try {
				return new CopyOperation(fileEntry);
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
	}

	/**
	 * Non-blocking
	 */
	public CompletableFuture<?> asyncStart() {
		log.info("Put " + copyList.size() + " item(s) in queue for copy");

		final CompletableFuture<?>[] allOperations = new CompletableFuture<?>[copyList.size()];

		copyList.stream().map(copyOperation -> {
			return CompletableFuture.runAsync(copyOperation, mainExecutor);
		}).collect(Collectors.toUnmodifiableList()).toArray(allOperations);

		allTasks = CompletableFuture.allOf(allOperations);
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

}
