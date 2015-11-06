package de.qabel.core.storage;

import de.qabel.core.crypto.QblECPublicKey;
import de.qabel.core.exceptions.QblStorageException;
import de.qabel.core.exceptions.QblStorageNameConflict;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class DirectoryMetadata {
	private static final Logger logger = LoggerFactory.getLogger(DirectoryMetadata.class.getName());
	private static final String JDBC_CLASS_NAME = "org.sqlite.JDBC";
	private static final String JDBC_PREFIX = "jdbc:sqlite:";
	public static final int TYPE_NONE = -1;
	private final Connection connection;

	private final String fileName;
	byte[] deviceId;
	String root;
	Path path;

	private static final int TYPE_FILE = 0;
	private static final int TYPE_FOLDER = 1;
	private static final int TYPE_EXTERNAL = 2;

	private final String initSql =
			"CREATE TABLE meta ("
					+ " name VARCHAR(24) PRIMARY KEY,"
					+ " value TEXT );"
					+ "CREATE TABLE spec_version ("
					+ " version INTEGER PRIMARY KEY );"
					+ "CREATE TABLE version ("
					+ " id INTEGER PRIMARY KEY,"
					+ " version BLOB NOT NULL,"
					+ " time LONG NOT NULL );"
					+ "CREATE TABLE shares ("
					+ " id INTEGER PRIMARY KEY,"
					+ " ref VARCHAR(255)NOT NULL,"
					+ " recipient BLOB NOT NULL,"
					+ " type INTEGER NOT NULL );"
					+ "CREATE TABLE files ("
					+ " block VARCHAR(255)NOT NULL,"
					+ " name VARCHAR(255)NOT NULL PRIMARY KEY,"
					+ " size LONG NOT NULL,"
					+ " mtime LONG NOT NULL,"
					+ " key BLOB NOT NULL );"
					+ "CREATE TABLE folders ("
					+ " id INTEGER PRIMARY KEY,"
					+ " ref VARCHAR(255)NOT NULL,"
					+ " name VARCHAR(255)NOT NULL,"
					+ " key BLOB NOT NULL );"
					+ "CREATE TABLE externals ("
					+ " id INTEGER PRIMARY KEY,"
					+ " owner BLOB NOT NULL,"
					+ " name VARCHAR(255)NOT NULL,"
					+ " key BLOB NOT NULL,"
					+ " url TEXT NOT NULL );"
					+ "INSERT INTO spec_version (version) VALUES(0)";


	public DirectoryMetadata(Connection connection, String root, byte[] deviceId,
	                         Path path, String fileName) {
		this.connection = connection;
		this.root = root;
		this.deviceId = deviceId;
		this.path = path;
		this.fileName = fileName;
	}

	public DirectoryMetadata(Connection connection, byte[] deviceId, Path path, String fileName) {
		this.connection = connection;
		this.deviceId = deviceId;
		this.path = path;
		this.fileName = fileName;
	}

	static DirectoryMetadata newDatabase(String root, byte[] deviceId) throws QblStorageException {
		Path path;
		try {
			path = Files.createTempFile("", "");
		} catch (IOException e) {
			throw new QblStorageException(e);
		}
		Connection connection;
		try {
			Class.forName(JDBC_CLASS_NAME);
			connection = DriverManager.getConnection(JDBC_PREFIX + path.toAbsolutePath().toString());
			connection.setAutoCommit(true);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Cannot load JDBC class!", e);
		} catch (SQLException e) {
			throw new RuntimeException("Cannot load in-memory database!", e);
		}
		DirectoryMetadata dm = new DirectoryMetadata(connection, root, deviceId, path,
				UUID.randomUUID().toString());
		try {
			dm.initDatabase();
		} catch (SQLException e) {
			throw new RuntimeException("Cannot init the database", e);
		}
		return dm;
	}

	static DirectoryMetadata openDatabase(Path path, byte[] deviceId, String fileName) throws QblStorageException {
		Connection connection;
		try {
			Class.forName(JDBC_CLASS_NAME);
			connection = DriverManager.getConnection(JDBC_PREFIX + path.toAbsolutePath().toString());
			connection.setAutoCommit(true);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Cannot load JDBC class!", e);
		} catch (SQLException e) {
			throw new RuntimeException("Cannot load in-memory database!", e);
		}
		return new DirectoryMetadata(connection, deviceId, path, fileName);
	}

	public Path getPath() {
		return path;
	}

	public String getFileName() {
		return fileName;
	}

	private void initDatabase() throws SQLException, QblStorageException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(initSql);
		}
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO version (version, time) VALUES (?, ?)")) {
			statement.setBytes(1, initVersion());
			statement.setLong(2, DateTimeUtils.currentTimeMillis());
			statement.executeUpdate();
		}
		setLastChangedBy();
		// only set root if this actually has a root attribute
		// (only index metadata files have it)
		if (root != null) {
			setRoot(root);
		}
	}

	private void setRoot(String root) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT OR REPLACE INTO meta (name, value) VALUES ('root', ?)")) {
			statement.setString(1, root);
			statement.executeUpdate();
		}
	}

	String getRoot() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT value FROM meta WHERE name='root'")) {
				if (rs.next()) {
					return rs.getString(1);
				} else {
					throw new QblStorageException("No root found!");
				}
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	Integer getSpecVersion() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT version FROM spec_version")) {
				if (rs.next()) {
					return rs.getInt(1);
				} else {
					throw new QblStorageException("No version found!");
				}
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	void setLastChangedBy() throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT OR REPLACE INTO meta (name, value) VALUES ('last_change_by', ?)")) {
			String x = Hex.encodeHexString(deviceId);
			statement.setString(1, x);
			statement.executeUpdate();
		}

	}

	byte[] getLastChangedBy() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT value FROM meta WHERE name='last_change_by'")) {
				if (rs.next()) {
					String lastChanged = rs.getString(1);
					return Hex.decodeHex(lastChanged.toCharArray());
				} else {
					throw new QblStorageException("No version found!");
				}
			} catch (DecoderException e) {
				throw new QblStorageException(e);
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	private byte[] initVersion() throws QblStorageException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new QblStorageException(e);
		}
		md.update(new byte[] {0, 0});
		md.update(deviceId);
		return md.digest();
	}

	byte[] getVersion() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT version FROM version ORDER BY id DESC LIMIT 1")) {
				if (rs.next()) {
					return rs.getBytes(1);
				} else {
					throw new QblStorageException("No version found!");
				}
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	void commit() throws QblStorageException {
		byte[] oldVersion = getVersion();
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new QblStorageException(e);
		}
		md.update(new byte[] {0, 1});
		md.update(oldVersion);
		md.update(deviceId);
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO version (version, time) VALUES (?, ?)")) {
			statement.setBytes(1, md.digest());
			statement.setLong(2, DateTimeUtils.currentTimeMillis());
			if (statement.executeUpdate() != 1) {
				throw new QblStorageException("Could not update version!");
			}
			setLastChangedBy();
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}


	List<BoxFile> listFiles() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT block, name, size, mtime, key FROM files")) {
				List<BoxFile> files = new ArrayList<>();
				while (rs.next()) {
					files.add(new BoxFile(rs.getString(1),
							rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getBytes(5)));
				}
				return files;
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}


	void insertFile(BoxFile file) throws QblStorageException {
		int type = isA(file.name);
		if ((type != TYPE_NONE) && (type != TYPE_FILE)) {
			throw new QblStorageNameConflict(file.name);
		}
		try {
			PreparedStatement st = connection.prepareStatement(
					"INSERT INTO files (block, name, size, mtime, key) VALUES(?, ?, ?, ?, ?)");
			st.setString(1, file.block);
			st.setString(2, file.name);
			st.setLong(3, file.size);
			st.setLong(4, file.mtime);
			st.setBytes(5, file.key);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to insert file");
			}

		} catch (SQLException e) {
			logger.error("Could not insert file " + file.name);
			throw new QblStorageException(e);
		}
	}

	void deleteFile(BoxFile file) throws QblStorageException {
		try {
			PreparedStatement st = connection.prepareStatement(
					"DELETE FROM files WHERE name=?");
			st.setString(1, file.name);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to delete file: Not found");
			}

		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}
	void insertFolder(BoxFolder folder) throws QblStorageException {
		int type = isA(folder.name);
		if ((type != TYPE_NONE) && (type != TYPE_FOLDER)) {
			throw new QblStorageNameConflict(folder.name);
		}
		try {
			PreparedStatement st = connection.prepareStatement(
					"INSERT INTO folders (ref, name, key) VALUES(?, ?, ?)");
			st.setString(1, folder.ref);
			st.setString(2, folder.name);
			st.setBytes(3, folder.key);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to insert folder");
			}

		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	void deleteFolder(BoxFolder folder) throws QblStorageException {
		try {
			PreparedStatement st = connection.prepareStatement(
					"DELETE FROM folders WHERE name=?");
			st.setString(1, folder.name);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to insert folder");
			}

		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	List<BoxFolder> listFolders() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT ref, name, key FROM folders")) {
				List<BoxFolder> folders = new ArrayList<>();
				while (rs.next()) {
					folders.add(new BoxFolder(rs.getString(1), rs.getString(2), rs.getBytes(3)));
				}
				return folders;
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	void insertExternal(BoxExternal external) throws QblStorageException {
		int type = isA(external.name);
		if ((type != TYPE_NONE) && (type != TYPE_EXTERNAL)) {
			throw new QblStorageNameConflict(external.name);
		}
		try {
			PreparedStatement st = connection.prepareStatement(
					"INSERT INTO externals (url, name, owner, key) VALUES(?, ?, ?, ?)");
			st.setString(1, external.url);
			st.setString(2, external.name);
			st.setBytes(3, external.owner.getKey());
			st.setBytes(4, external.key);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to insert external");
			}

		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	void deleteExternal(BoxExternal external) throws QblStorageException {
		try {
			PreparedStatement st = connection.prepareStatement(
					"DELETE FROM externals WHERE name=?");
			st.setString(1, external.name);
			if (st.executeUpdate() != 1) {
				throw new QblStorageException("Failed to insert external");
			}

		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	List<BoxExternal> listExternals() throws QblStorageException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"SELECT url, name, owner, key FROM externals")) {
				List<BoxExternal> externals = new ArrayList<>();
				while (rs.next()) {
					externals.add(new BoxExternal(rs.getString(1), rs.getString(2),
							new QblECPublicKey(rs.getBytes(3)), rs.getBytes(4)));
				}
				return externals;
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	BoxFile getFile(String name) throws QblStorageException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT block, name, size, mtime, key FROM files WHERE name=?")) {
			statement.setString(1, name);
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.next()) {
					return new BoxFile(rs.getString(1),
							rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getBytes(5));
				}
				return null;
			}
		} catch (SQLException e) {
			throw new QblStorageException(e);
		}
	}

	int isA(String name) throws QblStorageException {
		String[] types = new String[] {"files", "folders", "externals"};
		for (int type = 0; type < 3; type++) {
			try (PreparedStatement statement = connection.prepareStatement(
					"SELECT name FROM "+ types[type] +" WHERE name=?")) {
				statement.setString(1, name);
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return type;
					}
				}
			} catch (SQLException e) {
				throw new QblStorageException(e);
			}
		}
		return TYPE_NONE;
	}

}

