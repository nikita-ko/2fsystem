package home.work.system;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Class for direct access to file system. Uses custom {@link #LOCK} object to sync
 * access to the {@link #fileSystem} file, {@link #fileSystemTree}, {@link #currentPosition},
 * and {@link #defragNeeded}
 *
 */
@Component
public class FileSystem {
    private final static int INT_SIZE = 4;
    private final static int BOOL_SIZE = 1;
    private final static int HEADER_SIZE = 8;
    private final static int CHUNK_SIZE = 512;
    private final static String FILENAME = "fileSystem";

    private final int fileSystemSize;
    private final File fileSystem;

    private int currentPosition;
    private final Map<String, Integer> fileSystemTree = new HashMap<>();
    private boolean defragNeeded;

    private final static ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    /**
     * Creates a new FileSystem instance based on newly created file with the given size
     * or uses existing one. If file is created from scratch, {@link #fileSystemSize}
     * must be at least 8 bytes. If "fileSystem" file already exists, it is used to fill
     * {@link #fileSystemTree}, set {@link #currentPosition} and {@link #defragNeeded} flag
     *
     * @throws  IllegalArgumentException
     *          If specified size is less than 8 bytes
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    FileSystem(int fileSystemSize) throws IOException {
        if (fileSystemSize < HEADER_SIZE) {
            throw new IllegalArgumentException("File system size must be at least 8 bytes");
        }
        this.fileSystem = new File(FILENAME);
        LOCK.writeLock().lock();
        try {
            if (fileSystem.createNewFile()) {
                try (FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
                    MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
                    memory.putInt(fileSystemSize);
                    this.fileSystemSize = fileSystemSize;
                    memory.putInt(HEADER_SIZE);
                    this.currentPosition = HEADER_SIZE;
                }
            } else {
                try (FileChannel fc = FileChannel.open(fileSystem.toPath(), READ)) {
                    MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                    this.fileSystemSize = memory.getInt();
                    this.currentPosition = memory.getInt();
                    if (fc.size() > HEADER_SIZE) {
                        restoreFileSystemTree(memory);
                    }
                }
            }
        } finally {
            LOCK.writeLock().unlock();
        }

    }

    /**
     * Iterates existing "fileSystem" file to fill {@link #fileSystemTree},
     * set {@link #currentPosition} and {@link #defragNeeded} flag
     *
     * @param  memory
     *         MemoryByteBuffer containing all bytes of the {@link #fileSystem} file
     */
    private void restoreFileSystemTree(MappedByteBuffer memory) {
        int position = HEADER_SIZE;
        while (position < currentPosition) {
            //read isRemoved flag
            memory.position(position);
            boolean isRemoved = memory.get() == 1;
            //read filename
            int filenameLength = memory.getInt();
            if (isRemoved) {
                memory.position(memory.position() + filenameLength);
                defragNeeded = true;
            } else {
                byte[] filenameBytes = new byte[filenameLength];
                memory.get(filenameBytes);
                //update fileSystemTree
                fileSystemTree.put(new String(filenameBytes), position);
            }
            //get file content length and update position
            int fileLength = memory.getInt();
            position = position + 2 * INT_SIZE + BOOL_SIZE + filenameLength + fileLength;
        }
    }

    /**
     * Uses {@link #checkIfFileWithSameNameExists(String)} to check if file
     * with the same name already exists. Opens MemoryMappedBuffer on top
     * of {@link #fileSystem} file to write filename length, filename, content length,
     * and content. Sets currentPosition to the start of unoccupied file space
     *
     * @param  file
     *         Contains String filename and byte[] content to write
     *
     * @throws  IllegalArgumentException
     *          If file with the same name already exists in file system
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void writeFileToFileSystem(home.work.system.File file) throws IOException {
        LOCK.writeLock().lock();
        try {
            int totalLength = file.getTotalLength();
            String filename = file.getName();
            checkIfFileWithSameNameExists(filename);
            int offset = currentPosition;
            currentPosition = currentPosition + totalLength;
            fileSystemTree.put(file.getName(), offset);
            int lowerBoundary = INT_SIZE;
            int channelSize = (offset - INT_SIZE) + totalLength;
            try(FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
                MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, lowerBoundary, channelSize);
                //write isRemoved flag
                memory.position(offset - lowerBoundary);
                memory.put((byte) 0);
                //write filename
                memory.putInt(file.getNameLength());
                memory.put(filename.getBytes());
                //write file content
                memory.putInt(file.getContentLength());
                memory.put(file.getContent());
                //update current position
                memory.position(0);
                memory.putInt(currentPosition);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public void writeFileFromConnection(HttpURLConnection connection, String filename) throws IOException {
        LOCK.writeLock().lock();
        try {
            try (InputStream in = connection.getInputStream()) {
                int offset = currentPosition;
                int lowerBoundary = INT_SIZE;
                int channelSize = fileSystemSize - INT_SIZE;

                try(FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
                    MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, lowerBoundary, channelSize);
                    //write isRemoved flag
                    memory.position(offset - lowerBoundary);
                    memory.put((byte) 0);
                    //write filename
                    byte[] filenameBytes = filename.getBytes();
                    memory.putInt(filenameBytes.length);
                    memory.put(filenameBytes);
                    //write file content skipping content size
                    int contentLengthPosition = memory.position();
                    memory.position(contentLengthPosition + INT_SIZE);
                    currentPosition += contentLengthPosition + INT_SIZE;
                    byte[] dataBuffer = new byte[CHUNK_SIZE];
                    while (in.read(dataBuffer, 0, CHUNK_SIZE) != -1) {
                        if (isEnoughSpace(CHUNK_SIZE)) {
                            currentPosition += CHUNK_SIZE;
                            memory.put(dataBuffer);
                        } else {
                            currentPosition = offset;
                            String errorMsg = String.format("Available space of %d kB is less then file size",
                                    getAvailableSpace() / 1024);
                            throw new IllegalArgumentException(errorMsg);
                        }
                        dataBuffer = new byte[CHUNK_SIZE];
                    }
                    //write content size
                    int contentLength = memory.position() - contentLengthPosition - INT_SIZE;
                    memory.position(contentLengthPosition);
                    memory.putInt(contentLength);
                    fileSystemTree.put(filename, offset);
                    //update current position
                    memory.position(0);
                    memory.putInt(currentPosition);
                }
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private void checkIfFileWithSameNameExists(String filename) {
        if (fileSystemTree.containsKey(filename)) {
            throw new IllegalArgumentException(String.format("File with \"%s\" name already exists", filename));
        }
    }

    /**
     * Uses {@link #fileSystemTree} to check if file with the specified name exists.
     * Opens MemoryMappedBuffer on top of {@link #fileSystem} file to read content.
     *
     * @param  filename
     *         Is needed to find file in file system
     *
     * @return  Found File with name and content
     *
     * @throws  FileNotFoundException
     *          If file is not in {@link #fileSystemTree}
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public home.work.system.File readFileFromFileSystem(String filename) throws IOException {
        LOCK.readLock().lock();
        home.work.system.File file;
        try {
            Integer offset = fileSystemTree.get(filename);
            if (offset == null) {
                throw new FileNotFoundException(String.format("File %s not found", filename));
            }

            int lowerBoundary = offset + BOOL_SIZE;
            int channelSize = currentPosition - offset - BOOL_SIZE;
            try(FileChannel fc = FileChannel.open(fileSystem.toPath(), READ)) {
                MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_ONLY, lowerBoundary, channelSize);
                //read filename length
                int filenameLength = memory.getInt();
                //read file content length
                memory.position(INT_SIZE + filenameLength);
                int contentLength = memory.getInt();
                //read file content
                byte[] content = new byte[contentLength];
                memory.get(content);
                file = new home.work.system.File(filename, content);
            }
        } finally {
            LOCK.readLock().unlock();
        }
        return file;
    }

    /**
     * Uses {@link #fileSystemTree} to check if file with the specified name exists.
     *
     * @param  filename
     *         Is needed to find file in file system
     *
     * @return  true if file was found, false otherwise
     *
     */
    public boolean fileExists(String filename) {
        boolean exists;
        LOCK.readLock().lock();
        try {
            exists = fileSystemTree.containsKey(filename);
        } finally {
            LOCK.readLock().unlock();
        }
        return exists;
    }

    /**
     * Uses {@link #fileSystemTree} to get filenames of all existing files.
     *
     * @return  list of filenames of all files existing in file system
     *
     */
    public List<String> listFiles() {
        Set<String> filenames;
        LOCK.readLock().lock();
        try {
            filenames = fileSystemTree.keySet();
        } finally {
            LOCK.readLock().unlock();
        }
        return new ArrayList<>(filenames);
    }

    /**
     * Uses {@link #fileSystemTree} to check if file with the specified name exists.
     * Opens MemoryMappedBuffer on top of {@link #fileSystem} file to write 0 byte
     * in front of file to indicate that file is removed from the file system. Removes
     * file entry from {@link #fileSystemTree}, sets {@link #defragNeeded} to true
     *
     * @param  filename
     *         Is needed to find file in file system
     *
     * @throws  FileNotFoundException
     *          If file is not in {@link #fileSystemTree}
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void removeFileFromFileSystem(String filename) throws IOException {
        LOCK.writeLock().lock();
        try {
            if (!fileSystemTree.containsKey(filename)) {
                throw new FileNotFoundException(String.format("File %s not found", filename));
            }
            delete(filename);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private void delete(String filename) throws IOException {
        try(FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
            MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, fileSystemTree.get(filename), BOOL_SIZE);
            //write isRemoved flag
            memory.put((byte) 1);
        }
        fileSystemTree.remove(filename);
        defragNeeded = true;
    }

    /**
     * Performs garbage clean of the file if there are some gaps between written
     * data ({@link #defragNeeded} is true) in order to release some space.
     * All valid data are copied to the empty buffer from file, and then written
     * back to the clean file. Sets {@link #defragNeeded} to false, when the process
     * is over.
     * <p>This method should block the whole instance of filesystem, hence it is syncronized></p>
     *
     * @throws  FileNotFoundException
     *          If file is not in {@link #fileSystemTree}
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public synchronized void defragmentFileSystem() throws IOException {
        if (defragNeeded) {
            //get the map with positions as keys in ascending order
            TreeMap<Integer, String> positionToFilename = fileSystemTree.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (k1, k2) -> k2, TreeMap::new));
            defragment(positionToFilename);
            //clear the flag
            defragNeeded = false;
        }
    }

    private void defragment(TreeMap<Integer, String> positionToFilename) throws IOException {
        //open file
        try (FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
            MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSystemSize);

            //get temp buffer to copy
            ByteBuffer byteBuffer = ByteBuffer.allocate(memory.capacity());
            Map<String, Integer> newFileSystemTree = new HashMap<>();
            //new file position
            int newCurrentPosition = HEADER_SIZE;
            //just copy all existing files to the temp byte buffer
            for (Map.Entry<Integer, String> entry : positionToFilename.entrySet()) {
                int oldFilePosition = entry.getKey();
                //read old filename
                memory.position(oldFilePosition + BOOL_SIZE);
                int filenameLength = memory.getInt();
                byte[] filenameBytes = new byte[filenameLength];
                memory.get(filenameBytes);
                //read content
                int contentLength = memory.getInt();
                byte[] fileContent = new byte[contentLength];
                memory.get(fileContent);
                //write to new system
                byteBuffer.position(newCurrentPosition);
                byteBuffer.put((byte) 0);
                byteBuffer.putInt(filenameLength);
                byteBuffer.put(filenameBytes);
                byteBuffer.putInt(contentLength);
                byteBuffer.put(fileContent);
                newFileSystemTree.put(entry.getValue(), newCurrentPosition);
                newCurrentPosition = byteBuffer.position();
            }
            //write header
            byteBuffer.position(0);
            byteBuffer.putInt(fileSystemSize);
            byteBuffer.putInt(newCurrentPosition);
            //update file
            memory.clear();
            memory.put(byteBuffer.array());
            memory.force();

            //update fileSystemTree
            for (Map.Entry<String, Integer> entry: newFileSystemTree.entrySet()) {
                fileSystemTree.put(entry.getKey(), entry.getValue());
            }
            currentPosition = newCurrentPosition;
        }
    }

    public boolean isEnoughSpace(long length) {
        return getAvailableSpace() >= length;
    }

    /**
     * Calculates free space based on the difference
     * between {@link #fileSystemSize} and {@link #currentPosition}
     *
     * @return  Available space to write to
     *
     */
    public int getAvailableSpace() {
        LOCK.readLock().lock();
        int availableSpace;
        try {
            availableSpace = fileSystemSize - currentPosition;
        } finally {
            LOCK.readLock().unlock();
        }
        return availableSpace;
    }

    /**
     * Clears file system without removing the underlying file.
     * Re-sets {@link #currentPosition} to the {@link #HEADER_SIZE}
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public synchronized void formatFileSystem() throws IOException {
        try (FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
            MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSystemSize);
            memory.clear();
            //write header
            memory.putInt(fileSystemSize);
            memory.putInt(HEADER_SIZE);
            //move currentPosition
            this.currentPosition = HEADER_SIZE;
            memory.force();
        }
    }

    /**
     * If file with the filename already exists, it is deleted. New file is written
     * in the same way it is done by {@link #writeFileToFileSystem(home.work.system.File)}.
     *
     * @param  file
     *         Contains String filename and byte[] content to write
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public void overwriteFile(home.work.system.File file) throws IOException {
        LOCK.writeLock().lock();
        String filename = file.getName();
        try {
            //first remove
            delete(filename);
            //then write
            int totalLength = file.getTotalLength();
            int offset = currentPosition;
            currentPosition = currentPosition + totalLength;
            fileSystemTree.put(filename, offset);
            int lowerBoundary = INT_SIZE;
            int channelSize = (offset - INT_SIZE) + totalLength;
            try(FileChannel fc = FileChannel.open(fileSystem.toPath(), READ, WRITE)) {
                MappedByteBuffer memory = fc.map(FileChannel.MapMode.READ_WRITE, lowerBoundary, channelSize);
                //write isRemoved flag
                memory.position(offset - lowerBoundary);
                memory.put((byte) 0);
                //write filename
                memory.putInt(file.getNameLength());
                memory.put(filename.getBytes());
                //write file content
                memory.putInt(file.getContentLength());
                memory.put(file.getContent());
                //update current position
                memory.position(0);
                memory.putInt(currentPosition);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }
}
