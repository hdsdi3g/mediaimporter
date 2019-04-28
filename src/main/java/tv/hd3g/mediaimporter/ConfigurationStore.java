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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class ConfigurationStore {
	private static Logger log = LogManager.getLogger();

	private final String url;

	public ConfigurationStore(final String name, final ObservableList<SourceEntry> sourcesList, final ObservableList<DestinationEntry> destsList) {
		Objects.requireNonNull(sourcesList, "\"sourcesList\" can't to be null");
		Objects.requireNonNull(destsList, "\"destsList\" can't to be null");

		final File sqliteFile = Path.of(Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse(System.getProperty("user.home") + File.separator + ".config"), name, "settings.sqlite").toFile();
		try {
			FileUtils.forceMkdir(sqliteFile.getParentFile());
		} catch (final IOException e1) {
			throw new RuntimeException("Can't create dir " + sqliteFile.getParent(), e1);
		}

		url = "jdbc:sqlite:" + sqliteFile.getPath().replaceAll("\\\\", "/");
		if (sqliteFile.exists() == false) {
			try (Connection conn = DriverManager.getConnection(url)) {
				if (conn != null) {
					final DatabaseMetaData meta = conn.getMetaData();
					log.info("Create new " + meta.getDriverName() + " DB on " + sqliteFile);
				}
			} catch (final SQLException e) {
				throw new RuntimeException("Can't init database", e);
			}

			try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
				log.debug("Init SQLite tables");
				stmt.execute(IOUtils.toString(getClass().getResource("createdb-sources.sql").openStream(), Charset.defaultCharset()));
				stmt.execute(IOUtils.toString(getClass().getResource("createdb-destinations.sql").openStream(), Charset.defaultCharset()));
			} catch (final SQLException e) {
				throw new RuntimeException("Can't setup database", e);
			} catch (final IOException e) {
				throw new RuntimeException("Can't access to ressource", e);
			}
		} else {
			try {
				final Connection conn = DriverManager.getConnection(url);
				final Statement stmt = conn.createStatement();

				final ResultSet rsSources = stmt.executeQuery("SELECT path FROM sources");
				while (rsSources.next()) {
					final File f = new File(rsSources.getString("path"));
					if (f.exists() == false) {
						continue;
					}
					sourcesList.add(new SourceEntry(f));
				}

				final ResultSet rsDests = stmt.executeQuery("SELECT path FROM destinations");
				while (rsDests.next()) {
					final File f = new File(rsDests.getString("path"));
					if (f.exists() == false) {
						continue;
					}
					destsList.add(new DestinationEntry(f));
				}
			} catch (final SQLException e) {
				throw new RuntimeException("Can't read from SQLite", e);
			}
		}

		sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(entry -> {
					prepareStatement("INSERT INTO sources(path) VALUES(?)", pstmt -> {
						pstmt.setString(1, entry.rootPath.getPath());
					});
				});
				change.getRemoved().forEach(entry -> {
					prepareStatement("DELETE FROM sources WHERE path = ?", pstmt -> {
						pstmt.setString(1, entry.rootPath.getPath());
					});
				});
			}
		});
		destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(entry -> {
					prepareStatement("INSERT INTO destinations(path) VALUES(?)", pstmt -> {
						pstmt.setString(1, entry.rootPath.getPath());
					});
				});
				change.getRemoved().forEach(entry -> {
					prepareStatement("DELETE FROM destinations WHERE path = ?", pstmt -> {
						pstmt.setString(1, entry.rootPath.getPath());
					});
				});
			}
		});
	}

	private void prepareStatement(final String sql, final PreparedStatementConsumerWithSQLException withPstmt) {
		exec(conn -> {
			try (final PreparedStatement pstmt = conn.prepareStatement(sql)) {
				withPstmt.accept(pstmt);
				pstmt.executeUpdate();
			} catch (final SQLException e) {
				throw new RuntimeException("Can't connect to SQLite", e);
			}
		});
	}

	private void exec(final Consumer<Connection> withDb) {
		try (Connection conn = DriverManager.getConnection(url)) {
			withDb.accept(conn);
		} catch (final SQLException e) {
			throw new RuntimeException("Can't connect to SQLite", e);
		}
	}

	@FunctionalInterface
	private interface PreparedStatementConsumerWithSQLException {
		void accept(PreparedStatement pstmt) throws SQLException;
	}

}
