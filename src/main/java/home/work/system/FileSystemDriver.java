package home.work.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

/**
 * Class representing client for access to file system.
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
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void createFile(String filename) throws IOException {
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
     * @throws  IOException
     *          If some other I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          In case there is not enough space
     */
    public void createFile(String filename, byte[] content) throws IOException {
        File file = new File(filename, content);
        checkThereIsEnoughSpace(file.getTotalLength());
        fileSystem.writeFileToFileSystem(file);
    }

    /**
     * Copies existing file to a filesystem
     *
     * @param  pathToFile
     *         Absolute path to the file
     *
     * @throws  IOException
     *          If some other I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          In case there is not enough space
     */
    public void copyExistingFile(String pathToFile) throws IOException {
        java.io.File original = new java.io.File(pathToFile);
        byte[] content = Files.readAllBytes(original.toPath());
        checkThereIsEnoughSpace(content.length);
        createFile(original.getName(), content);
    }

    /**
     * Downloads file and writes it to the file system with the specified filename.
     * Connection may not return file size. Then we only check if filename fits available space.
     * If file content's size is larger than available space then exception will be thrown
     * at {@link FileSystem} level
     *
     * @param  uri
     *         URI to download file
     *
     * @param  filename
     *         Filename which will be written to the file system
     *
     * @throws  IOException
     *          If some other I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          In case url is malformed, connection returned
     *          anything other than 200, or if there is not enough space
     */
    public void downloadAndSaveFile(String uri, String filename) throws IOException {
        HttpURLConnection connection = openConnection(uri);
        int fileSize = connection.getContentLength();
        if (fileSize < 1) {
            fileSize = filename.getBytes().length;
        }
        checkThereIsEnoughSpace(fileSize);
        logger.info("Started downloading file from: " + uri);
        fileSystem.writeFileFromConnection(connection, filename);
    }

    /**
     * Opens HttpURLConnection to download file
     *
     * @param  uri
     *         URI to download file
     *
     * @throws  IOException
     *          If some other I/O error occurs
     *
     * @throws  IllegalArgumentException
     *          In case url is malformed or connection
     *          returned anything other than 200
     */
    private HttpURLConnection openConnection(String uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
        int response = connection.getResponseCode();
        if (response != HttpURLConnection.HTTP_OK) {
            String errorMsg = String.format("Connection to %s returned %d", uri, response);
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        return connection;
    }

    /**
     * Checks if there is enough space in the filesystem.
     * Overwrites file if it exists with the same filename, or
     * creates a new one.
     *
     * @param  filename
     *         Name of the file to create
     *
     * @param  content
     *         Bytes of the file content
     *
     * @throws  IllegalArgumentException
     *          In case there is not enough space
     */
    public void overwriteFile(String filename, byte[] content) throws IOException {
        File file = new File(filename, content);
        checkThereIsEnoughSpace(file.getTotalLength());
        fileSystem.overwriteFile(file);
    }

    /**
     * Checks if file with specified name exists in the filesystem.
     *
     * @return {@code true} if file exists, {@code false} if not
     *
     * @param  filename
     *         Filename to search for
     */
    public boolean fileExists(String filename) {
        return fileSystem.fileExists(filename);
    }

    /**
     * Returns list of filenames existing in the file system.
     *
     * @return list of existing filenames
     *
     */
    public List<String> listFiles() {
        return fileSystem.listFiles();
    }

    /**
     * Removes file with the specified name from the file system.
     *
     * @param  filename
     *         Filename to remove
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void deleteFile(String filename) throws IOException {
        fileSystem.removeFileFromFileSystem(filename);
    }

    /**
     * Reads file content from the file system.
     *
     * @param  filename
     *         Filename to search for
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public byte[] readFromFile(String filename) throws IOException {
        File file = fileSystem.readFileFromFileSystem(filename);
        return file.getContent();
    }

    /**
     * Removes all data (except file system size and current position)
     * from the filesystem
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void formatFileSystem() throws IOException {
        fileSystem.formatFileSystem();
    }

    /**
     * Checks if file system has enough space to write specified number of bytes.
     * Metadata (filename and content's length, isRemovedFlag) size is taken into account.
     *
     * @throws  IllegalArgumentException
     *          If there is not enough space
     */
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
