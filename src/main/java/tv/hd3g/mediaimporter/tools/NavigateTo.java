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

import org.apache.commons.lang3.SystemUtils;

import tv.hd3g.processlauncher.tool.ToolRunner;

public interface NavigateTo {

	void navigateTo(File selectedTarget, ToolRunner runner);

	static NavigateTo get() {
		if (SystemUtils.IS_OS_MAC_OSX) {
			throw new UnsupportedOperationException("macOS is not managed");
		} else if (SystemUtils.IS_OS_LINUX) {
			throw new UnsupportedOperationException("Linux is not managed");
		}

		return new WindowsNavigateTo();
	}
}
