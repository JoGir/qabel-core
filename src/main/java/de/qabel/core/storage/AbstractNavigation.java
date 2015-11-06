package de.qabel.core.storage;

import de.qabel.core.crypto.CryptoUtils;
import de.qabel.core.crypto.QblECKeyPair;
import de.qabel.core.exceptions.QblStorageException;
import de.qabel.core.exceptions.QblStorageNameConflict;
import de.qabel.core.exceptions.QblStorageNotFound;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.util.*;

public abstract class AbstractNavigation implements BoxNavigation {

	private static final Logger logger = LoggerFactory.getLogger(AbstractNavigation.class.getName());

	DirectoryMetadata dm;
	final QblECKeyPair keyPair;
	final byte[] deviceId;
	final CryptoUtils cryptoUtils;

	final StorageReadBackend readBackend;
	final StorageWriteBackend writeBackend;

	private final Set<String> deleteQueue = new HashSet<>();
	private final Set<FileUpdate> updatedFiles = new HashSet<>();



	AbstractNavigation(DirectoryMetadata dm, QblECKeyPair keyPair, byte[] deviceId,
	                   StorageReadBackend readBackend, StorageWriteBackend writeBackend) {
		this.dm = dm;
		this.keyPair = keyPair;
		this.deviceId = deviceId;
		this.readBackend = readBackend;
		this.writeBackend = writeBackend;
		cryptoUtils = new CryptoUtils();
	}

	@Override
	public BoxNavigation navigate(BoxFolder target) throws QblStorageException {
		try {
			InputStream indexDl = readBackend.download(target.ref);
			Path tmp = Files.createTempFile(null, null);
			SecretKey key = makeKey(target.key);
			if (cryptoUtils.decryptFileAuthenticatedSymmetricAndValidateTag(indexDl, tmp.toFile(), key)) {
				DirectoryMetadata dm = DirectoryMetadata.openDatabase(tmp, deviceId, target.ref);
				return new FolderNavigation(dm, keyPair, target.key, deviceId, readBackend, writeBackend);
			} else {
				throw new QblStorageNotFound("Invalid key");
			}
		} catch (IOException | InvalidKeyException e) {
			throw new QblStorageException(e);
		}
	}

	protected abstract DirectoryMetadata reloadMetadata() throws QblStorageException;

	protected SecretKey makeKey(byte[] key2) {
		return new SecretKeySpec(key2, "AES");
	}

	@Override
	public void commit() throws QblStorageException {
		byte[] version = dm.getVersion();
		dm.commit();
		logger.info("Committing version "+ Hex.encodeHexString(dm.getVersion())
				+ " with device id " + Hex.encodeHexString(dm.deviceId));
		DirectoryMetadata updatedDM = null;
		try {
			updatedDM = reloadMetadata();
			logger.info("Remote version is " + Hex.encodeHexString(updatedDM.getVersion()));
		} catch (QblStorageNotFound e) {
			logger.info("Could not reload metadata");
		}
		// the remote version has changed from the _old_ version
		if ((updatedDM != null) && (!Arrays.equals(version, updatedDM.getVersion()))) {
			logger.info("Conflicting version");
			// ignore our local directory metadata
			// all changes that are not inserted in the new dm are _lost_!
			dm = updatedDM;
			for (FileUpdate update: updatedFiles) {
				handleConflict(update);
			}
			dm.commit();
		}
		uploadDirectoryMetadata();
		for (String ref: deleteQueue) {
			writeBackend.delete(ref);
		}
		// TODO: make a test fail without these
		deleteQueue.clear();
		updatedFiles.clear();
	}

	private void handleConflict(FileUpdate update) throws QblStorageException {
		BoxFile local = update.updated;
		BoxFile newFile = dm.getFile(local.name);
		if (newFile == null) {
			try {
				dm.insertFile(local);
			} catch (QblStorageNameConflict e) {
				// name clash with a folder or external
				local.name = conflictName(local);
				// try again until we get no name clash
				handleConflict(update);
			}
		} else if (newFile.equals(update.old)) {
			logger.info("No conflict for the file " + local.name);
		} else {
			logger.info("Inserting conflict marked file");
			local.name = conflictName(local);
			if (update.old != null) {
				dm.deleteFile(update.old);
			}
			if (dm.getFile(local.name) == null) {
				dm.insertFile(local);
			}
		}
	}

	private String conflictName(BoxFile local) {
		return local.name + "_conflict_" + local.mtime.toString();
	}

	protected abstract void uploadDirectoryMetadata() throws QblStorageException;

	@Override
	public BoxNavigation navigate(BoxExternal target) {
		throw new NotImplementedException("Externals are not yet implemented!");
	}

	@Override
	public List<BoxFile> listFiles() throws QblStorageException {
		return dm.listFiles();
	}

	@Override
	public List<BoxFolder> listFolders() throws QblStorageException {
		return dm.listFolders();
	}

	@Override
	public List<BoxExternal> listExternals() throws QblStorageException {
		//return dm.listExternals();
		throw new NotImplementedException("Externals are not yet implemented!");
	}

	@Override
	public BoxFile upload(String name, File file) throws QblStorageException {
		SecretKey key = cryptoUtils.generateSymmetricKey();
		String block = UUID.randomUUID().toString();
		BoxFile boxFile = new BoxFile(block, name, file.length(), 0L, key.getEncoded());
		boxFile.mtime = uploadEncrypted(file, key, "blocks/" + block);
		// Overwrite = delete old file, upload new file
		BoxFile oldFile = dm.getFile(name);
		if (oldFile != null) {
			deleteQueue.add(oldFile.block);
			dm.deleteFile(oldFile);
		}
		updatedFiles.add(new FileUpdate(oldFile, boxFile));
		dm.insertFile(boxFile);
		return boxFile;
	}

	protected long uploadEncrypted(File file, SecretKey key, String block) throws QblStorageException {
		try {
			Path tempFile = Files.createTempFile("", "");
			OutputStream outputStream = Files.newOutputStream(tempFile);
			if (!cryptoUtils.encryptFileAuthenticatedSymmetric(file, outputStream, key)) {
				throw new QblStorageException("Encryption failed");
			}
			outputStream.flush();
			return writeBackend.upload(block, Files.newInputStream(tempFile));
		} catch (IOException | InvalidKeyException e) {
			throw new QblStorageException(e);
		}
	}

	@Override
	public InputStream download(BoxFile boxFile) throws QblStorageException {
		InputStream download = readBackend.download("blocks/" + boxFile.block);
		File temp;
		SecretKey key = makeKey(boxFile.key);
		try {
			temp = Files.createTempFile("", "").toFile();
			if (!cryptoUtils.decryptFileAuthenticatedSymmetricAndValidateTag(download, temp, key)) {
				throw new QblStorageException("Decryption failed");
			}
			return Files.newInputStream(temp.toPath());
		} catch (IOException | InvalidKeyException e) {
			throw new QblStorageException(e);
		}
	}

	@Override
	public BoxFolder createFolder(String name) throws QblStorageException {
		DirectoryMetadata dm = DirectoryMetadata.newDatabase(null, deviceId);
		SecretKey secretKey = cryptoUtils.generateSymmetricKey();
		BoxFolder folder = new BoxFolder(dm.getFileName(), name, secretKey.getEncoded());
		this.dm.insertFolder(folder);
		BoxNavigation newFolder = new FolderNavigation(dm, keyPair, secretKey.getEncoded(),
				deviceId, readBackend, writeBackend);
		newFolder.commit();
		return folder;
	}

	@Override
	public void delete(BoxFile file) throws QblStorageException {
		dm.deleteFile(file);
		deleteQueue.add("blocks/" + file.block);
	}

	@Override
	public void delete(BoxFolder folder) throws QblStorageException {
		BoxNavigation folderNav = navigate(folder);
		for (BoxFile file: folderNav.listFiles()) {
			logger.info("Deleting file " + file.name);
			folderNav.delete(file);
		}
		for (BoxFolder subFolder: folderNav.listFolders()) {
			logger.info("Deleting folder " + folder.name);
			folderNav.delete(subFolder);
		}
		folderNav.commit();
		dm.deleteFolder(folder);
		deleteQueue.add(folder.ref);
	}

	@Override
	public void delete(BoxExternal external) throws QblStorageException {

	}

	private static class FileUpdate {
		final BoxFile old;
		final BoxFile updated;

		public FileUpdate(BoxFile old, BoxFile updated) {
			this.old = old;
			this.updated = updated;
		}

		@Override
		public int hashCode() {
			int result = old != null ? old.hashCode() : 0;
			result = 31 * result + (updated != null ? updated.hashCode() : 0);
			return result;
		}
	}
}
