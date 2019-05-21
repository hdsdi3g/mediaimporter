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
import java.util.List;
import java.util.Objects;

public interface TargetedFileEntries {

	List<Entry> getTargetedFileEntries();

	class Entry {
		private final String label;
		private final File file;
		private final boolean invalid;

		Entry(final String label, final File file, final boolean invalid) {
			this.label = Objects.requireNonNull(label, "\"label\" can't to be null");
			this.file = Objects.requireNonNull(file, "\"file\" can't to be null");
			this.invalid = invalid;
		}

		Entry(final String label, final File file) {
			this(label, file, false);
		}

		public String getLabel() {
			return label;
		}

		public File getFile() {
			return file;
		}

		public boolean isInvalid() {
			return invalid;
		}
	}

}
