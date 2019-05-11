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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum FileEntryStatus {

	ALL_COPIES_DONE, NOT_STARTED, PARTIAL_DONE, ERROR_OR_INCOMPLETE;

	public static Map<FileEntryStatus, Integer> countByStatuses(final List<FileEntry> allFiles) {
		return Arrays.stream(FileEntryStatus.values()).collect(Collectors.toUnmodifiableMap(status -> {
			return status;
		}, status -> {
			return (int) allFiles.stream().filter(s -> s.getCurrentResumeStatus().equals(status)).count();
		}));
	}

	public static Map<FileEntryStatus, Long> sizeByStatuses(final List<FileEntry> allFiles) {
		return Arrays.stream(FileEntryStatus.values()).collect(Collectors.toUnmodifiableMap(status -> {
			return status;
		}, status -> {
			return allFiles.stream().filter(s -> s.getCurrentResumeStatus().equals(status)).mapToLong(f -> f.getFile().length()).sum();
		}));
	}

}
