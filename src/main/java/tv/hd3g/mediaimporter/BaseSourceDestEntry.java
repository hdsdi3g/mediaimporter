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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import javafx.collections.ObservableList;

public class BaseSourceDestEntry {
	protected final File rootPath;

	public BaseSourceDestEntry(final File rootPath) {
		this.rootPath = Objects.requireNonNull(rootPath, "\"rootPath\" can't to be null");
	}

	private static Predicate<BaseSourceDestEntry> isStoredOn(final Stream<BaseSourceDestEntry> toCompare) {
		return entry -> {
			return toCompare.map(s -> s.rootPath).anyMatch(file -> {
				try {
					return FileUtils.directoryContains(file, entry.rootPath);
				} catch (final IOException e) {
					MainApp.log4javaFx.error("Can't scan directory " + file, e);
					return true;
				}
			});
		};
	}

	public static Predicate<BaseSourceDestEntry> isStoredOn(final ObservableList<SourceEntry> toCompareSources, final ObservableList<DestinationEntry> toCompareDests) {
		return BaseSourceDestEntry.isStoredOn(toCompareSources.stream().map(item -> {
			return (BaseSourceDestEntry) item;
		})).or(BaseSourceDestEntry.isStoredOn(toCompareDests.stream().map(item -> {
			return (BaseSourceDestEntry) item;
		}))).or(item -> {
			return toCompareSources.stream().map(s -> s.rootPath).anyMatch(s -> {
				return item.rootPath.equals(s);
			}) & toCompareDests.stream().map(s -> s.rootPath).anyMatch(s -> {
				return item.rootPath.equals(s);
			});
		});
	}

	@Override
	public String toString() {
		return rootPath.getPath();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (rootPath == null ? 0 : rootPath.hashCode());
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
		if (!(obj instanceof BaseSourceDestEntry)) {
			return false;
		}
		final BaseSourceDestEntry other = (BaseSourceDestEntry) obj;
		if (rootPath == null) {
			if (other.rootPath != null) {
				return false;
			}
		} else if (!rootPath.equals(other.rootPath)) {
			return false;
		}
		return true;
	}

}
