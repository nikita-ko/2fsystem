package home.work;

import home.work.system.ContextConfig;
import home.work.system.FileSystem;
import home.work.system.FileSystemDriver;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.qala.datagen.RandomShortApi.alphanumeric;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = ContextConfig.class)
@TestPropertySource(locations = "classpath:/test.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileSystemDriverTest {
    @Autowired
    private FileSystem fileSystem;

    private FileSystemDriver fileSystemDriver;

    @BeforeAll
    public void setUp() throws IOException {
        fileSystemDriver = new FileSystemDriver(fileSystem);
        fileSystemDriver.formatFileSystem();
    }

    @AfterAll
    public void removeFile() {
        java.io.File fileSystem = new java.io.File("fileSystem");
        fileSystem.delete();
    }

    @AfterEach
    public void cleanUp() throws IOException {
        fileSystemDriver.formatFileSystem();
    }

    @Test
    public void shouldCreateEmptyFile() throws IOException {
        String randomName = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        byte[] result = fileSystemDriver.readFromFile(randomName);
        int actual = convertBytesToInt(result);
        assertEquals(-1, actual);
    }

    @Test
    public void shouldWriteFileWithContent() throws IOException {
        String randomName = alphanumeric(10);
        String randomContent = alphanumeric(10);
        fileSystemDriver.createFile(randomName, randomContent.getBytes());
        byte[] result = fileSystemDriver.readFromFile(randomName);
        assertEquals(randomContent, new String(result));
    }

    @Test
    public void shouldThrowException_whenTryToCreateFileWithExistingFilename() throws IOException {
        String randomName = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        assertThrows(IllegalArgumentException.class,
                () -> fileSystemDriver.createFile(randomName, alphanumeric(10).getBytes()),
                "File with \"" + randomName + "\" name already exists");
    }

    @Test
    public void shouldOverwriteFile() throws IOException {
        String randomName = alphanumeric(10);
        String randomContent = alphanumeric(10);
        fileSystemDriver.createFile(randomName);
        fileSystemDriver.overwriteFile(randomName, randomContent.getBytes());
        byte[] result = fileSystemDriver.readFromFile(randomName);
        assertEquals(randomContent, new String(result));
    }

    @Test
    public void shouldCopyExistingFile_GivenAbsolutePath() throws IOException {
        String path = getClass().getClassLoader().getResource("data/some_file.txt").getPath();
        fileSystemDriver.copyExistingFile(path);
        byte[] result = fileSystemDriver.readFromFile("some_file.txt");
        assertEquals("file content 123", new String(result));
    }

    @Test
    public void shouldThrowException_IfNotEnoughSpace() {
        String path = getClass().getClassLoader().getResource("data/large_image.jpeg").getPath();
        assertThrows(IllegalArgumentException.class, () -> fileSystemDriver.copyExistingFile(path));
    }

    @Test
    public void shouldDownloadAndSaveFile() throws IOException {
        String url = "https://raw.githubusercontent.com/kynyan/2fsystem/master/src/test/resources/data/readme.md";
        fileSystemDriver.downloadAndSaveFile(url);
        byte[] actual = fileSystemDriver.readFromFile("readme.md");
        byte[] expected = getBytesFromPath("data/readme.md");
        assertEquals(new String(expected), new String(actual).trim());
    }

    @Test
    public void shouldThrowException_ifFileNotFound() {
        String url = "https://raw.githubusercontent.com/kynyan/2fsystem/master/src/test/resources/data/readme1.md";
        String expectedErrorMsg = String.format("Connection to %s returned 404", url);
        assertThrows(IllegalArgumentException.class,
                () -> fileSystemDriver.downloadAndSaveFile(url),
                expectedErrorMsg);
    }

    @Test
    public void shouldThrowException_ifNotEnoughSpace() {
        String url = "https://raw.githubusercontent.com/kynyan/2fsystem/master/src/test/resources/data/large_image.jpeg";
        String expectedErrorMsg = String.format("Available space of 3 kB is less then file size of 8 kB");
        assertThrows(IllegalArgumentException.class,
                () -> fileSystemDriver.downloadAndSaveFile(url),
                expectedErrorMsg);
    }

    @Test
    public void shouldThrowException_ifUrlIsMalformed() {
        String url = "readme1.md";
        assertThrows(MalformedURLException.class,
                () -> fileSystemDriver.downloadAndSaveFile(url));
    }

    private int convertBytesToInt(byte[] array) {
        ByteBuffer wrapped = ByteBuffer.wrap(array);
        return wrapped.getInt();
    }

    private byte[] getBytesFromPath(String path) throws IOException {
        String absolutePath = getClass().getClassLoader().getResource(path).getPath();
        return Files.readAllBytes(Paths.get(absolutePath));
    }
}
