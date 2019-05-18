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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Deprecated
public class SourceListStore implements Closeable { // TODO remove
	private static Logger log = LogManager.getLogger();

	private final Connection conn;
	private static final long maxFileSizeDigestCompute = 30_000;

	public SourceListStore(final File baseDir) {
		Objects.requireNonNull(baseDir, "\"baseDir\" can't to be null");
		try {
			if (baseDir.exists() == false) {
				FileUtils.forceMkdir(baseDir);
			}
			if (baseDir.canRead() == false | baseDir.canWrite() == false) {
				throw new IOException("Can't access to " + baseDir.getPath());
			}

			final File sqliteFile = new File(baseDir.getPath() + File.separator + "db.sqlite");
			final String url = "jdbc:sqlite:" + sqliteFile.getPath().replaceAll("\\\\", "/");
			if (sqliteFile.exists() == false) {
				conn = DriverManager.getConnection(url);
				final DatabaseMetaData meta = conn.getMetaData();
				log.debug("Create new " + meta.getDriverName() + " on " + sqliteFile);

				try (final Statement stmt = conn.createStatement()) {
					log.debug("Init SQLite table");
					stmt.execute(IOUtils.toString(getClass().getResource("createdb-contentlist.sql").openStream(), Charset.defaultCharset()));
				}
			} else {
				conn = DriverManager.getConnection(url);
			}
		} catch (final SQLException | IOException e) {
			throw new RuntimeException("Can't prepare SQLite db", e);
		}
	}

	public void add(final String relativePath, final String driveSN, final File reference) {
		final String sql = "INSERT INTO source_list(source_rpath, source_size, source_date, digest) VALUES(?, ?, ?, ?)"; // INSERT OR REPLACE
		try (final PreparedStatement pstmt = conn.prepareStatement(sql)) {
			log.debug("INSERT source_list = " + driveSN + ":" + relativePath);
			pstmt.setString(1, driveSN + ":" + relativePath.replaceAll("\\\\", "/"));
			pstmt.setLong(2, reference.length());
			pstmt.setLong(3, reference.lastModified());

			if (reference.length() < maxFileSizeDigestCompute) {
				pstmt.setLong(4, FileUtils.checksumCRC32(reference));
			} else {
				pstmt.setLong(4, 0l);
			}
			if (pstmt.executeUpdate() != 1) {
				throw new RuntimeException("Can't INSERT " + driveSN + ":" + relativePath);
			}
		} catch (final SQLException | IOException e) {
			throw new RuntimeException("Can't execute query on SQLite", e);
		}
	}

	public Optional<FileDto> get(final String relativePath, final String driveSN) {
		final String sql = "SELECT source_rpath, source_size, source_date, digest FROM source_list WHERE source_rpath = ?";

		try (final PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, driveSN + ":" + relativePath.replaceAll("\\\\", "/"));

			try (final ResultSet rsSource = pstmt.executeQuery()) {
				while (rsSource.next()) {
					return Optional.of(new FileDto(rsSource));
				}
			}
			return Optional.empty();
		} catch (final SQLException e) {
			throw new RuntimeException("Can't execute query on SQLite", e);
		}
	}

	public class FileDto {
		private final String relativePath;
		// private final String driveSN;
		private final long sourceSize;
		private final long sourceDate;
		private final long digest;

		private FileDto(final ResultSet rsSource) throws SQLException {
			final String[] path = rsSource.getString("source_rpath").split(":");
			// driveSN = path[0];
			relativePath = path[1].replaceAll("/", File.separator);
			sourceSize = rsSource.getLong("source_size");
			sourceDate = rsSource.getLong("source_date");
			digest = rsSource.getLong("digest");
		}

		public boolean compareTo(final File canditate) throws IOException {
			if (canditate.getAbsolutePath().endsWith(relativePath) == false) {
				throw new RuntimeException("Invalid comparaison with " + relativePath + " <> " + canditate.getAbsolutePath());
			} else if (canditate.lastModified() != sourceDate) {
				return false;
			} else if (canditate.length() != sourceSize) {
				return false;
			} else if (digest != 0l) {
				return digest == FileUtils.checksumCRC32(canditate);
			} else {
				return true;
			}
		}
	}

	@Override
	public void close() {
		try {
			conn.close();
		} catch (final SQLException e) {
			throw new RuntimeException("Can't close SQL DB", e);
		}
	}

}
