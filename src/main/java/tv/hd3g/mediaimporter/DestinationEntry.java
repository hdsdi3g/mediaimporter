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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;

public class DestinationEntry extends BaseSourceDestEntry {
	private static Logger log = LogManager.getLogger();

	private final SimpleLongProperty availableSpace;
	private final SimpleLongProperty writeSpeed;
	private final SimpleIntegerProperty slotsCount;
	private final List<Slot> slots;

	public DestinationEntry(final File rootPath) {
		super(rootPath);
		availableSpace = new SimpleLongProperty(rootPath.getFreeSpace());
		writeSpeed = new SimpleLongProperty(0);
		slotsCount = new SimpleIntegerProperty(0);
		slots = new ArrayList<>();

		try {
			update(); // TODO async
		} catch (final Exception e) {
			MainApp.log4javaFx.error("Can't update destination " + rootPath, e);
		}
	}

	public void update() throws IOException { // TODO regulary update, async
		availableSpace.set(rootPath.getFreeSpace());

		final List<File> actualDirSlots = Arrays.asList(rootPath.listFiles(file -> file.isDirectory() & file.isHidden() == false & file.getName().startsWith(".") == false));

		slots.removeIf(slot -> {
			return actualDirSlots.contains(slot.getDir()) == false;
		});
		actualDirSlots.forEach(dir -> {
			if (slots.stream().map(Slot::getDir).noneMatch(slotDir -> slotDir.getAbsoluteFile().equals(dir.getAbsoluteFile()))) {
				slots.add(new Slot(dir));
			}
		});
		slotsCount.setValue(slots.size());
	}

	class Slot {
		private final File dir;

		private Slot(final File dir) {
			this.dir = Objects.requireNonNull(dir, "\"dir\" can't to be null");
		}

		public File getDir() {
			return dir;
		}
		// TODO continue impl Slot
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
			return param.getValue().slotsCount;
		};
	}

}
