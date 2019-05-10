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
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

public class ConfigurationStore {
	private static Logger log = LogManager.getLogger();

	private final String url;

	public ConfigurationStore(final String name, final ObservableList<SourceEntry> sourcesList, final ObservableList<DestinationEntry> destsList, final TextField inputPrefixDirName) {
		Objects.requireNonNull(sourcesList, "\"sourcesList\" can't to be null");
		Objects.requireNonNull(destsList, "\"destsList\" can't to be null");

		final File sqliteFile = Path.of(Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse(System.getProperty("user.home") + File.separator + ".config"), name, "settings.sqlite").toFile();
		try {
			FileUtils.forceMkdir(sqliteFile.getParentFile());
		} catch (final IOException e1) {
			throw new RuntimeException("Can't create dir " + sqliteFile.getParent(), e1);
		}

		// sqliteFile.delete();
		final Properties config;

		url = "jdbc:sqlite:" + sqliteFile.getPath().replaceAll("\\\\", "/");
		if (sqliteFile.exists() == false) {
			try (Connection conn = DriverManager.getConnection(url)) {
				if (conn != null) {
					final DatabaseMetaData meta = conn.getMetaData();
					log.info("Create new " + meta.getDriverName() + " on " + sqliteFile);
				}
			} catch (final SQLException e) {
				throw new RuntimeException("Can't init database", e);
			}

			try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
				log.debug("Init SQLite tables");
				stmt.execute(IOUtils.toString(getClass().getResource("createdb-sources.sql").openStream(), Charset.defaultCharset()));
				stmt.execute(IOUtils.toString(getClass().getResource("createdb-destinations.sql").openStream(), Charset.defaultCharset()));
				stmt.execute(IOUtils.toString(getClass().getResource("createdb-config.sql").openStream(), Charset.defaultCharset()));
			} catch (final SQLException e) {
				throw new RuntimeException("Can't setup database", e);
			} catch (final IOException e) {
				throw new RuntimeException("Can't access to ressource", e);
			}
			config = new Properties();
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

				final ResultSet rsConfig = stmt.executeQuery("SELECT key, value FROM config");
				config = new Properties();
				while (rsConfig.next()) {
					config.put(rsDests.getString("key"), rsDests.getString("value"));
				}
			} catch (final SQLException e) {
				throw new RuntimeException("Can't read from SQLite", e);
			}
		}

		final ScheduledExecutorService delayedExecutor = Executors.newScheduledThreadPool(1);

		sourcesList.addListener((ListChangeListener<SourceEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(entry -> {
					final String path = entry.rootPath.getPath();
					delayedExecutor.execute(() -> {
						prepareStatement("INSERT INTO sources(path) VALUES(?)", pstmt -> {
							log.debug("INSERT sources " + path);
							pstmt.setString(1, path);
						});
					});
				});
				change.getRemoved().forEach(entry -> {
					final String path = entry.rootPath.getPath();
					delayedExecutor.execute(() -> {
						prepareStatement("DELETE FROM sources WHERE path = ?", pstmt -> {
							log.debug("DELETE sources " + path);
							pstmt.setString(1, path);
						});
					});
				});
			}
		});
		destsList.addListener((ListChangeListener<DestinationEntry>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(entry -> {
					final String path = entry.rootPath.getPath();
					delayedExecutor.execute(() -> {
						prepareStatement("INSERT INTO destinations(path) VALUES(?)", pstmt -> {
							log.debug("INSERT destinations " + path);
							pstmt.setString(1, path);
						});
					});
				});
				change.getRemoved().forEach(entry -> {
					final String path = entry.rootPath.getPath();
					delayedExecutor.execute(() -> {
						prepareStatement("DELETE FROM destinations WHERE path = ?", pstmt -> {
							log.debug("DELETE destinations " + path);
							pstmt.setString(1, path);
						});
					});
				});
			}
		});

		inputPrefixDirName.setText(config.getProperty("inputPrefixDirName", ""));

		inputPrefixDirName.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
			final String textOnEvent = inputPrefixDirName.getText().trim();
			delayedExecutor.schedule(() -> {
				Platform.runLater(() -> {
					final String textAfterEvent = inputPrefixDirName.getText().trim();
					if (textAfterEvent.equals(textOnEvent) == false) {
						return;
					}
					delayedExecutor.execute(() -> {
						prepareStatement("INSERT OR REPLACE INTO config(key, value) VALUES(?, ?)", pstmt -> {
							log.debug("INSERT config inputPrefixDirName = " + textOnEvent);
							pstmt.setString(1, "inputPrefixDirName");
							pstmt.setString(2, textOnEvent);
						});
					});
				});
			}, 300, TimeUnit.MILLISECONDS);
		});

	}

	private void prepareStatement(final String sql, final PreparedStatementConsumerWithSQLException withPstmt) {
		exec(conn -> {
			try (final PreparedStatement pstmt = conn.prepareStatement(sql)) {
				withPstmt.accept(pstmt);
				pstmt.executeUpdate();
			} catch (final SQLException e) {
				throw new RuntimeException("Can't execute query on SQLite", e);
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
