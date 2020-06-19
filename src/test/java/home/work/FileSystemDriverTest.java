package home.work;

import home.work.system.ContextConfig;
import home.work.system.FileSystem;
import home.work.system.FileSystemDriver;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.ByteBuffer;

import static io.qala.datagen.RandomShortApi.alphanumeric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(classes = ContextConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileSystemDriverTest {
    @Autowired
    private FileSystem fileSystem;

    private FileSystemDriver fileSystemDriver;

    @BeforeAll
    public void setUp() {
        fileSystemDriver = new FileSystemDriver(fileSystem);
        fileSystemDriver.formatFileSystem();
    }

    @AfterAll
    public void removeFile() {
        java.io.File fileSystem = new java.io.File("fileSystem");
        fileSystem.delete();
    }

    @AfterEach
    public void cleanUp() {
        fileSystemDriver.formatFileSystem();
    }

    @Test
    public void shouldCreateEmptyFile() {
        String randomName = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        byte[] result = fileSystemDriver.readFromFile(randomName);
        int actual = convertBytesToInt(result);
        assertEquals(-1, actual);
    }

    @Test
    public void shouldWriteFileWithContent() {
        String randomName = alphanumeric(10);
        String randomContent = alphanumeric(10);
        fileSystemDriver.createFile(randomName, randomContent.getBytes());
        byte[] result = fileSystemDriver.readFromFile(randomName);
        assertEquals(randomContent, new String(result));
    }

    @Test
    public void shouldThrowException_whenTryToCreateFileWithExistingFilename() {
        String randomName = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        assertThrows(IllegalArgumentException.class,
                () -> fileSystemDriver.createFile(randomName, alphanumeric(10).getBytes()),
                "File with \"" + randomName + "\" name already exists");
    }

    @Test
    public void shouldOverwriteFile() {
        String randomName = alphanumeric(10);
        String randomContent = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        fileSystemDriver.overwriteFile(randomName, randomContent.getBytes());
        byte[] result = fileSystemDriver.readFromFile(randomName);
        assertEquals(randomContent, new String(result));
    }

    private int convertBytesToInt(byte[] array) {
        ByteBuffer wrapped = ByteBuffer.wrap(array);
        return wrapped.getInt();
    }
}
