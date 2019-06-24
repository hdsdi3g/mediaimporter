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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import tv.hd3g.mediaimporter.FileEntry;
import tv.hd3g.mediaimporter.FileEntryStatus;
import tv.hd3g.mediaimporter.MainApp;
import tv.hd3g.mediaimporter.Messages;

public class StatusMsgBox extends Alert {

	private static AlertType getAlertType(final Map<FileEntryStatus, Integer> countByStatuses, final Map<FileEntryStatus, Long> sizeByStatuses) {
		if (countByStatuses.get(FileEntryStatus.ERROR_OR_INCOMPLETE).intValue() > 0) {
			return AlertType.ERROR;
		} else if (countByStatuses.get(FileEntryStatus.PARTIAL_DONE).intValue() > 0) {
			return AlertType.WARNING;
		} else {
			return AlertType.INFORMATION;
		}
	}

	public StatusMsgBox(final ObservableList<FileEntry> fileList, final Image appIcon) {
		super(getAlertType(FileEntryStatus.countByStatuses(fileList), FileEntryStatus.sizeByStatuses(fileList)));

		final Map<FileEntryStatus, Integer> countByStatuses = FileEntryStatus.countByStatuses(fileList);
		final Map<FileEntryStatus, Long> sizeByStatuses = FileEntryStatus.sizeByStatuses(fileList);

		final String header;
		if (countByStatuses.get(FileEntryStatus.ERROR_OR_INCOMPLETE).intValue() > 0) {
			header = Messages.getString("statusMsgboxHeader_ERROR_OR_INCOMPLETE");
		} else if (countByStatuses.get(FileEntryStatus.PARTIAL_DONE).intValue() > 0) {
			header = Messages.getString("statusMsgboxHeader_PARTIAL_DONE");
		} else if (countByStatuses.get(FileEntryStatus.NOT_STARTED).intValue() > 0) {
			header = Messages.getString("statusMsgboxHeader_NOT_STARTED");
		} else if (countByStatuses.get(FileEntryStatus.INTEGRITY_INVALID).intValue() > 0) {
			header = Messages.getString("statusMsgboxHeader_INTEGRITY_INVALID");
		} else if (countByStatuses.get(FileEntryStatus.INTEGRITY_VALID).intValue() > 0) {
			header = Messages.getString("statusMsgboxHeader_INTEGRITY_VALID");
		} else {
			header = Messages.getString("statusMsgboxHeader_ALL_COPIES_DONE");
		}

		setTitle(Messages.getString("statusMsgboxTitle"));
		setHeaderText(header);

		setContentText(Arrays.stream(FileEntryStatus.values()).filter(status -> {
			return countByStatuses.get(status).intValue() > 0;
		}).map(status -> {
			final int count = countByStatuses.get(status).intValue();
			final String size = MainApp.byteCountToDisplaySizeWithPrecision(sizeByStatuses.get(status).longValue());
			return String.format(Messages.getString("statusMsgboxContent_" + status.name()), count, size);
		}).collect(Collectors.joining(System.lineSeparator())));

		((Stage) getDialogPane().getScene().getWindow()).getIcons().add(appIcon);
	}

}
