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
package tv.hd3g.mediaimporter.driveprobe;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import tv.hd3g.processlauncher.cmdline.Parameters;
import tv.hd3g.processlauncher.tool.ExecutableTool;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class WindowsDriveProbe implements DriveProbe {

	@Override
	public Map<File, String> getSNByMountedDrive(final ToolRunner runner) throws InterruptedException, ExecutionException {

		final Map<String, String> mapSNByDriveId = runner.execute(new Diskdrive()).get().checkExecutionGetText().getStdoutLines(false).skip(1).map(line -> {
			return Arrays.stream(line.split(" ")).filter(p -> p.equals("") == false).collect(Collectors.toUnmodifiableList());
		}).collect(Collectors.toMap(line -> {
			return line.get(0).split("\\\\")[2].toLowerCase();
		}, line -> {
			return line.get(1).replaceAll("\\.", "");
		}));
		// System.out.println(mapSNByDriveId);

		final List<String> partitions = runner.execute(new GetPartition()).get().checkExecutionGetText().getStdoutLines(false).collect(Collectors.toUnmodifiableList());
		final Map<File, String> result = new HashMap<>();

		String lastDiskPath = null;
		String lastDriveLetter = null;
		for (int pos = 0; pos < partitions.size(); pos++) {
			final String line = partitions.get(pos);
			if (line.startsWith("DiskPath")) {
				lastDiskPath = parseLine(line, true);
			} else if (line.startsWith("DriveLetter")) {
				lastDriveLetter = parseLine(line, true);
				if (lastDriveLetter.equals("") == false) {
					try {
						final String diskPathid = lastDiskPath.split("#")[2].toLowerCase();
						result.put(new File(lastDriveLetter + ":\\").getAbsoluteFile(), mapSNByDriveId.get(diskPathid));
					} catch (final IndexOutOfBoundsException e) {
					}
				}
			}
		}

		return Collections.unmodifiableMap(result);
	}

	/**
	 * @return "aa : bb" > "aa" if right == false ; else "bb" if right == true.
	 *         null if not ":"
	 */
	private static String parseLine(final String line, final boolean right) {
		final int colon = line.indexOf(":");
		if (colon == -1) {
			return null;
		}
		if (right) {
			return line.substring(colon + 1).trim();
		} else {
			return line.substring(0, colon).trim();
		}
	}

	class GetPartition implements ExecutableTool {

		@Override
		public Parameters getReadyToRunParameters() {
			final Parameters parameters = new Parameters();
			parameters.addParameters("Get-Partition | Select-Object -Property DiskPath, DriveLetter | Format-List");
			return parameters;
		}

		@Override
		public String getExecutableName() {
			return "powershell";
		}

	}

	class Diskdrive implements ExecutableTool {

		@Override
		public Parameters getReadyToRunParameters() {
			final Parameters parameters = new Parameters();
			parameters.addBulkParameters("diskdrive get SerialNumber,PNPDeviceID");
			return parameters;
		}

		@Override
		public String getExecutableName() {
			return "wmic";
		}

	}

}
