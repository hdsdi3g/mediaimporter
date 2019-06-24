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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class SNDriveUpdater {
	private static Logger log = LogManager.getLogger();

	private final ToolRunner toolRunner;
	private final ScheduledExecutorService driveSNUpdaterRegularExecutor;
	private final SimpleObjectProperty<Map<File, String>> lastSNDrivesProbeResult;

	private volatile ScheduledFuture<?> driveSNUpdaterRegularFuture;

	public SNDriveUpdater(final ToolRunner toolRunner) {
		this.toolRunner = Objects.requireNonNull(toolRunner, "\"toolRunner\" can't to be null");
		driveSNUpdaterRegularExecutor = Executors.newScheduledThreadPool(1);
		driveSNUpdaterRegularFuture = null;
		lastSNDrivesProbeResult = new SimpleObjectProperty<>();
	}

	public void update() {
		if (driveSNUpdaterRegularFuture != null) {
			if (driveSNUpdaterRegularFuture.isCancelled() == false && driveSNUpdaterRegularFuture.isDone() == false) {
				return;
			}
		}

		driveSNUpdaterRegularFuture = driveSNUpdaterRegularExecutor.scheduleAtFixedRate(() -> {
			try {
				final Map<File, String> probeResult = DriveProbe.get().getSNByMountedDrive(toolRunner).get(DriveProbe.timeLimitSec + 1, TimeUnit.SECONDS);
				log.info("Found S/N for drives: {}", probeResult.toString());
				driveSNUpdaterRegularFuture.cancel(false);
				Platform.runLater(() -> {
					lastSNDrivesProbeResult.set(probeResult);
				});
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				log.warn("Can't get drives S/N, but it will be a retry", e);
			}
		}, 0, DriveProbe.timeLimitSec / 2, TimeUnit.SECONDS);

	}

	public boolean isLastProbeResultNull() {
		return lastSNDrivesProbeResult.isNull().get();
	}

	public SimpleObjectProperty<Map<File, String>> getLastProbeResult() {
		return lastSNDrivesProbeResult;
	}
}
