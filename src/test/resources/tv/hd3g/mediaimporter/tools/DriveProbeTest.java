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

import java.util.concurrent.ExecutionException;

import junit.framework.Assert;
import junit.framework.TestCase;
import tv.hd3g.processlauncher.cmdline.ExecutableFinder;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class DriveProbeTest extends TestCase {

	public void testGetSNByMountedDrive() throws InterruptedException, ExecutionException {
		final DriveProbe probe = DriveProbe.get();
		Assert.assertNotNull(probe);

		final ToolRunner runner = new ToolRunner(new ExecutableFinder(), 1);
		System.out.println(probe.getSNByMountedDrive(runner).get());
	}
}
