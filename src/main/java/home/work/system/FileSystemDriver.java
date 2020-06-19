package home.work.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
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

    /**
     * Creates empty file in a filesystem
     *
     * @param  filename
     *         Name of the file to create
     */
    public void createFile(String filename) {
        createFile(filename, new byte[0]);
    }

    /**
     * Checks if there is enough space in the filesystem.
     * Creates file with filename and content
     *
     * @param  filename
     *         Name of the file to create
     *
     * @param  content
     *         Bytes of the file content
     *
     * @throws IllegalArgumentException
     *         In case there is not enough space
     */
    public void createFile(String filename, byte[] content) {
        File file = new File(filename, content);
        checkThereIsEnoughSpace(file.getTotalLength());
        try {
            fileSystem.writeFileToFileSystem(file);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    /**
     * Copies existing file to a filesystem
     *
     * @param  pathToFile
     *         Absolute path to the file
     *
     * @throws  IOException
     *          If some other I/O error occurs or if there is not enough free space
     */
    public void copyExistingFile(String pathToFile) throws IOException {
        java.io.File original = new java.io.File(pathToFile);
        long fileSize = original.length();
        createFile(original.getName(), Files.readAllBytes(original.toPath()));
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

    private void checkThereIsEnoughSpace(long fileSize) {
        if (!fileSystem.isEnoughSpace(fileSize + 9)) {
            //9 is 8 bytes for filename and content lengths + 1 byte for isRemoved flag
            String errorMsg = String.format("Available space of %d kB is less then file size of %d kB",
                    fileSystem.getAvailableSpace() / 1024, (fileSize) / 1024);
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }
}
