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
	private final SimpleIntegerProperty slots;

	public DestinationEntry(final File rootPath) {
		super(rootPath);
		availableSpace = new SimpleLongProperty(rootPath.getFreeSpace());
		writeSpeed = new SimpleLongProperty(0);
		slots = new SimpleIntegerProperty(0);
	}

	// TODO regulary update
	// public void update() {
	// availableSpace.set(rootPath.getFreeSpace());
	// }

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
			return param.getValue().slots;
		};
	}

}
