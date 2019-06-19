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
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import tv.hd3g.mediaimporter.DestinationEntrySlot;
import tv.hd3g.mediaimporter.FileEntry;
import tv.hd3g.mediaimporter.MainClass;

public class IntegrityCheckEngine implements CanBeStopped {
	private static Logger log = LogManager.getLogger();
	private static final Set<OpenOption> OPEN_OPTIONS_READ_ONLY = Set.of(StandardOpenOption.READ);

	private final Map<DestinationEntrySlot, List<ToCheck>> toCheckBySlots;
	private final ThreadPoolExecutor executor;
	private final Map<FileEntry, List<ToCheck>> toCheckListBySources;

	private volatile boolean wantToStop;
	private CompletableFuture<?> currentTask;

	/**
	 * Not reusable
	 */
	public IntegrityCheckEngine(final List<CopyOperationResult> copiedList) {
		Objects.requireNonNull(copiedList, "\"copiedList\" can't to be null");

		log.debug("Get copiedList source {}", () -> {
			return copiedList.stream().map(cl -> cl.getSourceEntry().getRelativePath()).collect(Collectors.toUnmodifiableList());
		});

		final List<DestinationEntrySlot> slots = copiedList.stream().flatMap(CopyOperationResult::getSlots).distinct().collect(Collectors.toUnmodifiableList());
		final Map<DestinationEntrySlot, ByteBuffer> buffersBySlots = slots.stream().collect(Collectors.toUnmodifiableMap(slot -> slot, slot -> ByteBuffer.allocateDirect(4096)));

		toCheckBySlots = slots.stream().collect(Collectors.toUnmodifiableMap(slot -> {
			return slot;
		}, slot -> {
			return copiedList.stream().map(copyOperationResult -> {
				final Path pathForSlot = copyOperationResult.getResultCopies().get(slot);
				return new ToCheck(copyOperationResult.getSourceEntry(), pathForSlot, buffersBySlots.get(slot));
			}).collect(Collectors.toUnmodifiableList());
		}));

		final AtomicLong counter = new AtomicLong();
		final int size = Math.min(toCheckBySlots.size(), Runtime.getRuntime().availableProcessors());
		executor = new ThreadPoolExecutor(size, size, 1l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), r -> {
			final Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("Check operation #" + counter.getAndIncrement());
			return t;
		});

		toCheckListBySources = toCheckBySlots.entrySet().stream().flatMap(entry -> {
			return entry.getValue().stream();
		}).map(toCheck -> toCheck.sourceEntry).distinct().collect(Collectors.toUnmodifiableMap(sourceEntry -> {
			return sourceEntry;
		}, sourceEntry -> {
			return toCheckBySlots.entrySet().stream().flatMap(entry -> {
				return entry.getValue().stream();
			}).filter(toCheck -> {
				return toCheck.sourceEntry.equals(sourceEntry);
			}).collect(Collectors.toUnmodifiableList());
		}));

		toCheckBySlots.keySet().forEach(slot -> {
			log.debug("Prepare to compute integrity checks for {}", () -> toCheckBySlots.get(slot));
		});
	}

	private class ToCheck {
		private final FileEntry sourceEntry;
		private final Path copied;
		private final ByteBuffer buffer;
		private final MessageDigest destMessageDigest;
		private volatile IntegrityState state;

		private ToCheck(final FileEntry sourceEntry, final Path copied, final ByteBuffer buffer) {
			this.sourceEntry = Objects.requireNonNull(sourceEntry, "\"sourceEntry\" can't to be null");
			this.copied = Objects.requireNonNull(copied, "\"copied\" can't to be null");
			this.buffer = Objects.requireNonNull(buffer, "\"buffer\" can't to be null");
			state = IntegrityState.NOT_CHECKED;

			try {
				destMessageDigest = MessageDigest.getInstance(MainClass.DIGEST_NAME);
			} catch (final NoSuchAlgorithmException e) {
				throw new RuntimeException("Can't init " + MainClass.DIGEST_NAME + " Digest", e);
			}
		}

		private void readFile() throws IOException {
			buffer.clear();
			try (final FileChannel channel = FileChannel.open(copied, OPEN_OPTIONS_READ_ONLY)) {
				while (channel.read(buffer) > 0) {
					if (wantToStop) {
						break;
					}
					buffer.flip();
					destMessageDigest.update(buffer);
					buffer.clear();
				}
			}
		}

		private String getDigest() {
			return CopyOperation.byteToString(destMessageDigest.digest());
		}

		@Override
		public String toString() {
			return copied.toString();
		}
	}

	/**
	 * Async
	 */
	public CompletableFuture<?> start(final Executor waitForEndExecutor) {
		final var cfList = toCheckBySlots.keySet().stream().map(slot -> {
			return CompletableFuture.supplyAsync(() -> {
				toCheckBySlots.get(slot).stream().filter(cL -> wantToStop == false).forEach(check -> {
					try {
						log.info("Start to check integrity for {}", check.copied);
						check.readFile();

						if (wantToStop) {
							return;
						}

						final String copiedDigest = check.getDigest();
						final String sourceDigest = check.sourceEntry.getDigest();

						if (copiedDigest.equalsIgnoreCase(sourceDigest) == false) {
							check.state = IntegrityState.INVALID;
							log.error("Failed copy integrity between \"{}\" ({}) and \"{}\" ({})", check.sourceEntry.getFile(), sourceDigest, check.copied, copiedDigest);
						} else {
							check.state = IntegrityState.VALID;
						}
					} catch (final IOException e) {
						throw new RuntimeException("Can't read " + check.copied, e);
					}

					refreshDisplay(check.sourceEntry);
				});

				return slot;
			}, executor);
		}).collect(Collectors.toUnmodifiableList());

		currentTask = CompletableFuture.runAsync(() -> {
			cfList.forEach(action -> {
				if (wantToStop) {
					return;
				}
				try {
					final var slot = action.get();
					log.debug("All integrity checks are done for {}", () -> toCheckBySlots.get(slot));
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException("Can't wait all checks operations", e);
				}
			});
			executor.shutdown();
		}, waitForEndExecutor);

		return currentTask;
	}

	private void refreshDisplay(final FileEntry sourceEntry) {
		final var toCheckList = toCheckListBySources.get(sourceEntry);
		if (toCheckList.stream().anyMatch(toCheck -> {
			return toCheck.state == IntegrityState.NOT_CHECKED;
		})) {
			return;
		}

		final var allValid = toCheckList.stream().allMatch(toCheck -> toCheck.state == IntegrityState.VALID);
		final var allInvalid = toCheckList.stream().allMatch(toCheck -> toCheck.state == IntegrityState.INVALID);

		Platform.runLater(() -> {
			sourceEntry.setAllCopiesIntegrity(IntegrityAllState.get(allValid, allInvalid));
		});
	}

	@Override
	public void asyncStop(final Runnable onDone) {
		wantToStop = true;

		if (executor.getActiveCount() == 0) {
			executor.shutdown();
			onDone.run();
			return;
		}
		log.info("Set to stop current check queue: " + executor.getQueue().size() + " item(s)");

		if (currentTask != null) {
			currentTask.completeExceptionally(new Exception("Manual stop operation"));
		}

		executor.getQueue().clear();

		CompletableFuture.runAsync(onDone, executor).thenAcceptAsync(v -> {
			executor.shutdown();
		});
	}

}
