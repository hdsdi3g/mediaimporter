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
package tv.hd3g.mediaimporter.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WindowsFileSanity implements FileSanity {

	private static Logger log = LogManager.getLogger();

	@Override
	public boolean isFileIsValid(final File regularFile) {
		try {
			final DosFileAttributes dosAttr = Files.getFileAttributeView(regularFile.toPath(), DosFileAttributeView.class).readAttributes();
			if (dosAttr.isOther() || dosAttr.isSymbolicLink() || dosAttr.isSystem()) {
				return false;
			}
			final File parent = regularFile.getParentFile();
			if (parent != null) {
				final DosFileAttributes parentDosAttr = Files.getFileAttributeView(parent.toPath(), DosFileAttributeView.class).readAttributes();
				if (parentDosAttr.isOther() || parentDosAttr.isSymbolicLink() || parentDosAttr.isSystem()) {
					return false;
				}
			}
			return true;
		} catch (final IOException e) {
			log.error("Can't check file {}", regularFile, e);
		}

		return false;
	}
}
