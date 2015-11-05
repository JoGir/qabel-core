package de.qabel.core.storage;

import de.qabel.core.exceptions.QblStorageException;
import de.qabel.core.exceptions.QblStorageNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class LocalReadBackend extends StorageReadBackend {

	private static final Logger logger = LoggerFactory.getLogger(LocalReadBackend.class.getName());
	private final Path root;


	LocalReadBackend(Path root) {
		this.root = root;
	}


	InputStream download(String name) throws QblStorageException {
		Path file = root.resolve(name);
		logger.info("Downloading file path " + file.toString());
		try {
			return Files.newInputStream(file);
		} catch (IOException e) {
			throw new QblStorageNotFound(e);
		}
	}

}
