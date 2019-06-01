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
package tv.hd3g.mediaimporter.ui;

import javafx.scene.control.TableCell;
import tv.hd3g.mediaimporter.MainApp;

public class TableCellFileSize<T> extends TableCell<T, Number> {

	private final String unit;

	public TableCellFileSize(final String unit) {
		super();
		this.unit = unit;
	}

	public TableCellFileSize() {
		this(null);
	}

	@Override
	protected void updateItem(final Number item, final boolean empty) {
		super.updateItem(item, empty);
		if (empty) {
			setText(null);
		} else if (item.longValue() == 0l) {
			setText(null);
		} else {
			if (unit == null) {
				setText(MainApp.byteCountToDisplaySizeWithPrecision(item.longValue()));
			} else {
				setText(MainApp.byteCountToDisplaySizeWithPrecision(item.longValue()) + " " + unit);
			}
		}
	}
}
