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

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tv.hd3g.mediaimporter.DestinationEntrySlot;
import tv.hd3g.mediaimporter.FileEntry;

public class CopyOperationResult {

	private final FileEntry entryToCopy;
	private final Map<DestinationEntrySlot, Path> resultCopies;

	CopyOperationResult(final FileEntry entryToCopy, final Map<Path, DestinationEntrySlot> resultCopies) {
		this.entryToCopy = entryToCopy;

		/**
		 * Revert map key <-> value
		 */
		this.resultCopies = resultCopies.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> {
			return entry.getValue();
		}, entry -> {
			return entry.getKey();
		}));
	}

	public FileEntry getSourceEntry() {
		return entryToCopy;
	}

	public Map<DestinationEntrySlot, Path> getResultCopies() {
		return resultCopies;
	}

	public Stream<DestinationEntrySlot> getSlots() {
		return getResultCopies().keySet().stream();
	}

}
