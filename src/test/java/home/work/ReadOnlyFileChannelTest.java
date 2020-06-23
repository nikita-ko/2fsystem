package home.work;

import home.work.system.ReadOnlyFileChannel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadOnlyFileChannelTest {
    @Test
    public void shouldReadBytesToArray() throws IOException {
        String path = getClass().getClassLoader().getResource("data/some_file.txt").getPath();
        File file = new File(path);
        byte[] actual;
        try (ReadOnlyFileChannel channel = new ReadOnlyFileChannel(file.toPath(), 0, (int) file.length())) {
            actual = new byte[channel.size()];
            channel.read(actual, 0, channel.size());
        }
        assertEquals("file content 123", new String(actual));
    }

    @Test
    public void shouldReadBytesOneByOne() throws IOException {
        String path = getClass().getClassLoader().getResource("data/some_file.txt").getPath();
        File file = new File(path);
        byte[] actual;
        try (ReadOnlyFileChannel channel = new ReadOnlyFileChannel(file.toPath(), 0, (int) file.length())) {
            actual = new byte[channel.size()];
            int counter = 0;
            int byteRead;
            while ((byteRead = channel.read()) != -1) {
                actual[counter] = (byte) byteRead;
                counter++;
            }
        }
        assertEquals("file content 123", new String(actual));
    }
}
