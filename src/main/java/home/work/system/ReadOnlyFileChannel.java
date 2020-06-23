package home.work.system;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

/**
 * Helper classs to avoid returning byte[] directly from {@link FileSystem}.
 * {@link #offset} and {@link #size) params limit narrow channel to exactly the
 * length of the file content. Channel can be read only.
 */
public class ReadOnlyFileChannel implements AutoCloseable {
    private final FileChannel fileChannel;
    private final MappedByteBuffer buffer;
    private final int offset;
    private final int size;

    public ReadOnlyFileChannel(Path path, int offset, int size) throws IOException {
        this.fileChannel = FileChannel.open(path, READ);
        this.buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, size);
        this.offset = offset;
        this.size = size;
    }

    /**
     * Reads one byte. If end of the buffer is reached -1 is returned
     */
    public int read() {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    /**
     * Reads bytes to a specified byte array with specified offset and length.
     *
     * @param  bytes
     *         byte array to read into
     *
     * @param  off
     *         offset
     *
     * @param  len
     *         length
     */
    public int read(byte[] bytes, int off, int len) {
        if (buffer.position() == (size - offset)) {
            return -1;
        }
        len = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, len);
        return len;
    }

    public int size() {
        return size;
    }

    public void close() throws IOException {
        fileChannel.close();
    }
}
