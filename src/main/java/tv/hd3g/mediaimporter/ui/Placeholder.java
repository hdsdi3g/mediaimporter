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

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public class Placeholder extends VBox {

	public Placeholder(final String text) {
		super(50);
		setAlignment(Pos.CENTER);
		final Text welcomeLabel = new Text(text);
		welcomeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");
		welcomeLabel.setStrokeType(StrokeType.INSIDE);
		welcomeLabel.setTextAlignment(TextAlignment.CENTER);
		welcomeLabel.setFill(Color.web("#bbb"));
		getChildren().add(welcomeLabel);
	}

}
