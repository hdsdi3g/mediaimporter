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
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.processlauncher.cmdline.Parameters;
import tv.hd3g.processlauncher.tool.ExecutableTool;
import tv.hd3g.processlauncher.tool.ToolRunner;

public class NavigateToWindows implements NavigateTo {
	private static Logger log = LogManager.getLogger();

	@Override
	public void navigateTo(final File selectedTarget, final ToolRunner runner) {
		try {
			runner.execute(new Explorer(selectedTarget)).get().checkExecutionGetText();
		} catch (InterruptedException | ExecutionException e) {
			log.error("Can't open explorer", e);
		}
	}

	class Explorer implements ExecutableTool {

		final File selectedTarget;

		Explorer(final File selectedTarget) {
			this.selectedTarget = selectedTarget;
		}

		@Override
		public Parameters getReadyToRunParameters() {
			final Parameters parameters = new Parameters();
			parameters.addParameters("/c", "start explorer /separate,/select,\"" + selectedTarget.getPath() + "\"");
			return parameters;
		}

		@Override
		public String getExecutableName() {
			return "cmd";
		}
	}

}
