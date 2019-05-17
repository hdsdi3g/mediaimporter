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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import tv.hd3g.mediaimporter.tools.NavigateTo;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class TableContextMenu implements EventHandler<ContextMenuEvent> {

	private final TableView<? extends TargetedFileEntries> table;
	private final NavigateTo navigate;
	private final ToolRunner toolRunner;

	public TableContextMenu(final TableView<? extends TargetedFileEntries> table, final NavigateTo navigate, final ToolRunner toolRunner) {
		this.table = Objects.requireNonNull(table, "\"table\" can't to be null");
		this.navigate = Objects.requireNonNull(navigate, "\"navigate\" can't to be null");
		this.toolRunner = Objects.requireNonNull(toolRunner, "\"toolRunner\" can't to be null");
		table.setOnContextMenuRequested(this);
	}

	@Override
	public void handle(final ContextMenuEvent event) {
		event.consume();
		final TargetedFileEntries selected = table.getSelectionModel().getSelectedItem();
		if (selected == null) {
			return;
		}
		final ContextMenu contextMenu = new ContextMenu();

		final List<MenuItem> menus = selected.getTargetedFileEntries().stream().map(entry -> {
			final MenuItem item;
			if (entry.isInvalid()) {
				item = new MenuItem(entry.getLabel() + " " + MainApp.messages.getString("tableContextInvalid"));
			} else {
				item = new MenuItem(entry.getLabel());
			}

			if (entry.getFile().exists()) {
				item.setOnAction(eventMenu -> {
					navigate.navigateTo(entry.getFile(), toolRunner);
				});
			} else {
				item.setDisable(true);
			}
			return item;
		}).collect(Collectors.toUnmodifiableList());

		contextMenu.getItems().addAll(menus);
		contextMenu.show(table, event.getScreenX(), event.getScreenY());

		/**
		 * Remove menu on table click
		 */
		final EventHandler<MouseEvent> mouseEventHandler = mouseEvent -> {
			contextMenu.hide();
		};
		table.addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEventHandler);
		contextMenu.setOnHidden(onHiddenEvent -> {
			table.removeEventHandler(MouseEvent.MOUSE_PRESSED, mouseEventHandler);
		});
	}
}
