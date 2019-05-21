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

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ResourceBundle;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import tv.hd3g.mediaimporter.MainApp;

@Plugin(name = "DialogAppender", category = "Core", elementType = "appender", printObject = true)
public class DialogAppender extends AbstractAppender {

	private final ResourceBundle messages = MainApp.messages;

	protected DialogAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout, final boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
	}

	protected DialogAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout) {
		super(name, filter, layout);
	}

	@Override
	public void append(final LogEvent event) {
		if (event.getLevel().isInRange(Level.TRACE, Level.DEBUG)) {
			return;
		}

		Platform.runLater(() -> {
			final AlertType alertType;
			final String title;
			if (event.getLevel().equals(Level.WARN)) {
				alertType = AlertType.WARNING;
				title = messages.getString("alertWarning");
			} else if (event.getLevel().equals(Level.ERROR)) {
				alertType = AlertType.ERROR;
				title = messages.getString("alertError");
			} else {
				alertType = AlertType.INFORMATION;
				title = messages.getString("alertInformation");
			}

			final Alert alert = new Alert(alertType);
			alert.setTitle(title);
			alert.setHeaderText(event.getMessage().getFormattedMessage());

			// alert.setContentText();

			final GridPane expContent = new GridPane();
			expContent.setMaxWidth(Double.MAX_VALUE);
			int i = 0;

			expContent.add(new Label("‹" + event.getThreadName() + "›"), 0, i++);
			expContent.add(new Label(" ‣ " + event.getSource().toString()), 0, i++);

			final Throwable error = event.getThrown();
			if (error != null) {
				final StringWriter sw = new StringWriter();
				final PrintWriter pw = new PrintWriter(sw);
				error.printStackTrace(pw);

				final TextArea textArea = new TextArea(sw.toString());
				textArea.setEditable(false);
				textArea.setWrapText(true);

				textArea.setMaxWidth(Double.MAX_VALUE);
				textArea.setMaxHeight(Double.MAX_VALUE);
				GridPane.setVgrow(textArea, Priority.ALWAYS);
				GridPane.setHgrow(textArea, Priority.ALWAYS);
				expContent.add(new Label(messages.getString("alertDisplayStacktrace")), 0, i++);
				expContent.add(textArea, 0, i++);
			}

			alert.getDialogPane().setExpandableContent(expContent);
			alert.showAndWait();
		});
	}

	@PluginFactory
	public static DialogAppender createAppender(@PluginAttribute("name") final String name, @PluginElement("Layout") Layout<? extends Serializable> layout, @PluginElement("Filter") final Filter filter) {
		if (name == null) {
			LOGGER.error("No name provided for DialogAppender");
			return null;
		}
		if (layout == null) {
			layout = PatternLayout.createDefaultLayout();
		}
		return new DialogAppender(name, filter, layout, true);
	}

}
