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
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;

public class DestinationEntry extends BaseSourceDestEntry {

	private static final SimpleDateFormat slotBaseNameDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	private final SimpleLongProperty availableSpace;
	private final SimpleLongProperty writeSpeed;
	private final AtomicLong copiedDatasBytes;
	private final AtomicLong copiedDurationsNanoSec;
	private final ConcurrentHashMap<File, Long> digestByFile;

	private final ObservableList<DestinationEntrySlot> slots;
	private DestinationEntrySlot currentSessionSlot;

	public DestinationEntry(final File rootPath) {
		super(rootPath);
		availableSpace = new SimpleLongProperty(rootPath.getFreeSpace());
		writeSpeed = new SimpleLongProperty(0);
		slots = FXCollections.observableList(new ArrayList<>());
		copiedDatasBytes = new AtomicLong(0);
		copiedDurationsNanoSec = new AtomicLong(0);
		digestByFile = new ConcurrentHashMap<>();
	}

	static final FilenameFilter validDirNonHidden = (dir, name) -> {
		final File file = new File(dir.getPath() + File.separator + name);
		return file.isDirectory() & file.isHidden() == false & file.getName().startsWith(".") == false;
	};

	Map<File, Long> getDigestByFile() {
		return digestByFile;
	}

	public AtomicLong getCopiedDatasBytes() {
		return copiedDatasBytes;
	}

	public AtomicLong getCopiedDurationsNanoSec() {
		return copiedDurationsNanoSec;
	}

	public SimpleLongProperty getWriteSpeed() {
		return writeSpeed;
	}

	public void updateSlotsContent() {
		availableSpace.set(rootPath.getFreeSpace());

		final List<File> actualDirSlots = Arrays.asList(rootPath.listFiles(validDirNonHidden));

		slots.removeIf(slot -> {
			return actualDirSlots.contains(slot.getDir()) == false;
		});
		actualDirSlots.forEach(dir -> {
			if (slots.stream().map(DestinationEntrySlot::getDir).noneMatch(slotDir -> slotDir.getAbsoluteFile().equals(dir.getAbsoluteFile()))) {
				slots.add(new DestinationEntrySlot(this, dir));
			}
		});

		slots.sort((l, r) -> Long.compare(l.getDir().lastModified(), r.getDir().lastModified()));
	}

	public List<File> searchCopyPresence(final String relativePath, final String driveSN) {
		return slots.stream().map(slot -> {
			return slot.makePathFromRelativePath(driveSN, relativePath);
		}).filter(File::exists).collect(Collectors.toUnmodifiableList());
	}

	public DestinationEntry prepareNewSessionSlot(final String prefixDirName) {
		final StringBuilder path = new StringBuilder();
		path.append(rootPath.getPath());
		path.append(File.separator);

		if (prefixDirName != null) {
			if (prefixDirName.trim().isEmpty() == false) {
				path.append(prefixDirName.trim());
				path.append("_");
			}
		}
		path.append(slotBaseNameDate.format(System.currentTimeMillis()));

		currentSessionSlot = new DestinationEntrySlot(this, new File(path.toString()));
		slots.add(currentSessionSlot);
		return this;
	}

	public DestinationEntrySlot getCurrentSessionSlot() {
		Objects.requireNonNull(currentSessionSlot, "\"currentSessionSlot\" can't to be null");
		return currentSessionSlot;
	}

	public static Callback<CellDataFeatures<DestinationEntry, File>, ObservableValue<File>> getColPathFactory() {
		return param -> {
			return new ReadOnlyObjectWrapper<>(param.getValue().rootPath);
		};
	}

	public static Callback<CellDataFeatures<DestinationEntry, Number>, ObservableValue<Number>> getColAvailableFactory() {
		return param -> {
			return param.getValue().availableSpace;
		};
	}

	public static Callback<CellDataFeatures<DestinationEntry, Number>, ObservableValue<Number>> getColAvailableSpeed() {
		return param -> {
			return param.getValue().writeSpeed;
		};
	}

	public static Callback<CellDataFeatures<DestinationEntry, Number>, ObservableValue<Number>> getColAvailableSlots() {
		return param -> {
			final SimpleIntegerProperty size = new SimpleIntegerProperty(param.getValue().slots.size());
			param.getValue().slots.addListener((ListChangeListener<DestinationEntrySlot>) change -> {
				while (change.next()) {
					size.set(change.getList().size());
				}
			});
			return size;
		};
	}

}
