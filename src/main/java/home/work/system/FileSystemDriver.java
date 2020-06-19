package home.work.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Class representing client for access to file system.
 * All checked exceptions from {@link FileSystem} are caught here and logged.
 * Actually they should be wrapped in some nicer exception and re-thrown, but
 * that would be among possible improvements.
 *
 */
public class FileSystemDriver {
    private static Logger logger = LoggerFactory.getLogger(FileSystemDriver.class);
    private final FileSystem fileSystem;

    public FileSystemDriver(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void createFile(String filename) {
        try {
            fileSystem.writeFileToFileSystem(new File(filename, new byte[0]));
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    public void createFile(String filename, byte[] content) {
        File file = new File(filename, content);
        try {
            fileSystem.writeFileToFileSystem(file);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    public void overwriteFile(String filename, byte[] content) {
        try {
            fileSystem.overwriteFile(new File(filename, content));
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    public boolean fileExists(String filename) {
        return fileSystem.fileExists(filename);
    }

    public List<String> listFiles() {
        return fileSystem.listFiles();
    }

    public void deleteFile(String filename) {
        try {
            fileSystem.removeFileFromFileSystem(filename);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    public byte[] readFromFile(String filename) {
        File file = new File(filename, new byte[0]);
        try {
            file = fileSystem.readFileFromFileSystem(filename);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
        return file.getContent();
    }

    public void formatFileSystem() {
        try {
            fileSystem.formatFileSystem();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }
}
