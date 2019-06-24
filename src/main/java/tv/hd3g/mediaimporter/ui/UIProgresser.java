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

import org.apache.commons.lang3.time.DurationFormatUtils;

import tv.hd3g.mediaimporter.MainApp;
import tv.hd3g.mediaimporter.Messages;

public interface UIProgresser extends UIMainPanelProvider {

	default void updateProgress(final double progressRate, final int filesCopied, final int totalFiles, final long datasCopiedBytes, final long totalDatasBytes, final long startTimeMsec, final long etaMsec, final long meanCopySpeedBytesPerSec, final long instantCopySpeedBytesPerSec) {
		final var mainPanel = getMainPanel();
		mainPanel.getProgressBar().setProgress(progressRate);

		final String counter = String.format(Messages.getString("labelProgressProcess"), filesCopied + 1, totalFiles, MainApp.byteCountToDisplaySizeWithPrecision(datasCopiedBytes), MainApp.byteCountToDisplaySizeWithPrecision(totalDatasBytes));
		mainPanel.getLblProgressionCounter().setText(counter);

		if (etaMsec < 1) {
			mainPanel.getLblEta().setText("ETA: 00:00:00");
		} else {
			mainPanel.getLblEta().setText("ETA: " + DurationFormatUtils.formatDuration(etaMsec, "HH:mm:ss"));
		}

		final String since;
		if (startTimeMsec < 1) {
			since = "00:00:00";
		} else {
			since = DurationFormatUtils.formatDuration(startTimeMsec, "HH:mm:ss");
		}

		final String speedCopy = String.format(Messages.getString("labelProgressSpeed"), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(meanCopySpeedBytesPerSec)), MainApp.byteCountToDisplaySizeWithPrecision(Math.round(instantCopySpeedBytesPerSec)), since);
		mainPanel.getLblSpeedCopy().setText(speedCopy);
	}

}
