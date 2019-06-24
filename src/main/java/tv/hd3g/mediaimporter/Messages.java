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

import java.util.Objects;
import java.util.ResourceBundle;

public class Messages {

	private final static ResourceBundle messages;

	static {
		messages = ResourceBundle.getBundle(Messages.class.getPackage().getName() + ".messages");
		Objects.requireNonNull(messages, "\"messages\" can't to be null");
	}

	public static String getString(final String key) {
		return messages.getString(key);
	}
}
