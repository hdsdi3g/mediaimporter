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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private static Logger log = LogManager.getLogger();

	private final SimpleLongProperty availableSpace;
	private final SimpleLongProperty writeSpeed;
	private final ObservableList<Slot> slots;

	public DestinationEntry(final File rootPath) {
		super(rootPath);
		availableSpace = new SimpleLongProperty(rootPath.getFreeSpace());
		writeSpeed = new SimpleLongProperty(0);
		slots = FXCollections.observableList(new ArrayList<>());
	}

	private static final FilenameFilter validDirNonHidden = (dir, name) -> {
		final File file = new File(dir.getPath() + File.separator + name);
		return file.isDirectory() & file.isHidden() == false & file.getName().startsWith(".") == false;
	};

	public void updateSlotsContent() {
		availableSpace.set(rootPath.getFreeSpace());

		final List<File> actualDirSlots = Arrays.asList(rootPath.listFiles(validDirNonHidden));

		slots.removeIf(slot -> {
			return actualDirSlots.contains(slot.getDir()) == false;
		});
		actualDirSlots.forEach(dir -> {
			if (slots.stream().map(Slot::getDir).noneMatch(slotDir -> slotDir.getAbsoluteFile().equals(dir.getAbsoluteFile()))) {
				slots.add(new Slot(dir));
			}
		});

		slots.sort((l, r) -> Long.compare(l.slotRootDir.lastModified(), r.slotRootDir.lastModified()));
	}

	public Optional<File> searchCopyPresence(final String relativePath) {
		return slots.stream().map(slot -> slot.getCopyPresenceInSlotCopiedDirs(relativePath)).filter(Optional::isPresent).map(Optional::get).findFirst();
	}

	public Slot createSessionSlot() throws IOException {
		final Slot slot = new Slot(new File(rootPath.getPath() + File.separator + System.currentTimeMillis()));
		FileUtils.forceMkdir(slot.slotRootDir);
		return slot;
	}

	public class Slot {
		private final File slotRootDir;

		private Slot(final File dir) {
			slotRootDir = Objects.requireNonNull(dir, "\"slotRootDir\" can't to be null");
		}

		File getDir() {
			return slotRootDir;
		}

		List<File> getCopiedListRootDirs() {
			return Arrays.asList(slotRootDir.listFiles(validDirNonHidden));
		}

		Optional<File> getCopyPresenceInSlotCopiedDirs(final String relativePath) {
			return getCopiedListRootDirs().stream().map(copiedRootDir -> {
				return new File(copiedRootDir.getPath() + File.separator + relativePath);
			}).filter(File::exists).findFirst();
		}

		public File makePathFromRelativePath(final String driveSNValue, final String relativePath) {
			return new File(slotRootDir.getPath() + File.separator + driveSNValue + File.separator + relativePath);
		}
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
			param.getValue().slots.addListener((ListChangeListener<Slot>) change -> {
				while (change.next()) {
					size.set(change.getList().size());
				}
			});
			return size;
		};
	}

}
