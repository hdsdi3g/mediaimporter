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

import junit.framework.TestCase;

public class MainAppTest extends TestCase {

	public void testByteCountToDisplaySizeWithPrecision() {
		System.out.println(MainApp.byteCountToDisplaySizeWithPrecision(1_000_000));
		System.out.println(MainApp.byteCountToDisplaySizeWithPrecision(100_000));
		System.out.println(MainApp.byteCountToDisplaySizeWithPrecision(10_000));
		System.out.println(MainApp.byteCountToDisplaySizeWithPrecision(1_000));
	}
}
