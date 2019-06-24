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

import java.io.File;
import java.util.Optional;

import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import tv.hd3g.mediaimporter.Messages;

public class OptionalDirectoryChooser {

	private final DirectoryChooser directoryChooser;
	private final Window stage;

	public OptionalDirectoryChooser(final String titleTextKey, final Window stage) {
		directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle(Messages.getString(titleTextKey));
		this.stage = stage;
	}

	public Optional<File> select() {
		return Optional.ofNullable(directoryChooser.showDialog(stage));
	}

}
