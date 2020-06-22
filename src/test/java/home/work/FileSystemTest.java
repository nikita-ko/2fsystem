package home.work;

import home.work.system.File;
import home.work.system.FileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.qala.datagen.RandomShortApi.*;
import static org.junit.jupiter.api.Assertions.*;

public class FileSystemTest {
    private final static int DEFAULT_FILE_SYSTEM_SIZE = 2048;
    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        fileSystem = getNewFileSystem(DEFAULT_FILE_SYSTEM_SIZE);
    }

    @AfterEach
    public void cleanUp() {
        java.io.File fileSystem = new java.io.File("fileSystem");
        fileSystem.delete();
    }

    @Test
    public void shouldWriteFileToFSAndReadIt() throws IOException {
        File original = getFileWithNameAndContent(alphanumeric(1, 20), unicode(20));
        fileSystem.writeFileToFileSystem(original);
        File fromFS = fileSystem.readFileFromFileSystem(original.getName());
        assertFilesEqual(original, fromFS);
    }

    @Test
    public void shouldRestoreFileSystemFromExistingFile() throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        cleanUp();
        FileSystem fileSystem1 = getNewFileSystem(DEFAULT_FILE_SYSTEM_SIZE);
        File original = getFileWithNameAndContent(alphanumeric(1, 20), unicode(20));
        fileSystem1.writeFileToFileSystem(original);
        FileSystem fileSystem2 = getNewFileSystem(DEFAULT_FILE_SYSTEM_SIZE);
        assertEquals(fileSystem1.getAvailableSpace(), fileSystem2.getAvailableSpace());
    }

    @Test
    public void shouldReadFileWithSpecificName() throws IOException {
        List<File> listOfFiles = writeSomeFilesToFileSystem(fileSystem);
        int randomFileNumber = integer(1, listOfFiles.size() - 1);
        File fromFS = fileSystem.readFileFromFileSystem("file-" + randomFileNumber);
        File original = listOfFiles.get(randomFileNumber);
        assertFilesEqual(original, fromFS);
    }

    @Test
    public void shouldThrowFileNotFoundException_whenTryToReadRemovedFile() throws IOException {
        List<File> listOfFiles = writeSomeFilesToFileSystem(fileSystem);
        int randomFileNumber = integer(1, listOfFiles.size() - 1);
        String randomFilename = "file-" + randomFileNumber;
        fileSystem.removeFileFromFileSystem(randomFilename);
        assertThrows(FileNotFoundException.class,
                () -> fileSystem.readFileFromFileSystem(randomFilename),
                String.format("File %s not found", randomFilename));
    }

    @Test
    public void shouldThrowFileNotFoundException_whenTryToReadNonExistingFile() {
        String randomFilename = alphanumeric(10, 20);
        assertThrows(FileNotFoundException.class,
                () -> fileSystem.readFileFromFileSystem(randomFilename),
                String.format("File %s not found", randomFilename));
    }

    @Test
    public void shouldThrowFileNotFoundException_whenTryToRemoveNonExistingFile() {
        String randomFilename = alphanumeric(1, 20);
        assertThrows(FileNotFoundException.class,
                () -> fileSystem.removeFileFromFileSystem(randomFilename),
                String.format("File %s not found", randomFilename));
    }

    @Test
    public void shouldThrowIllegalArgumentException_whenTryToWriteFileWithTheSameName() throws IOException {
        fileSystem.writeFileToFileSystem(getFileWithNameAndContent("file", alphanumeric(1, 20)));
        assertThrows(IllegalArgumentException.class,
                () -> fileSystem.writeFileToFileSystem(getFileWithNameAndContent("file", alphanumeric(1, 20))),
                "File with \"file\" name already exists");
    }

    @Test
    public void shouldThrowIllegalArgumentException_andResetCurrentPosition_ifNotEnoughSpace() throws IOException {
        int expectedAvailableSpace = fileSystem.getAvailableSpace();
        String url = "https://github.com/kynyan/2fsystem/blob/master/readme.md";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        assertThrows(IllegalArgumentException.class,
                () -> fileSystem.writeFileFromConnection(connection, "file"),
                "Available space of 2 kB is less then file size");
        assertEquals(expectedAvailableSpace, fileSystem.getAvailableSpace());
    }

    @Test
    public void shouldDefragmentFileSystemAndReleaseSpace() throws IOException {
        List<File> listOfFiles = writeSomeFilesToFileSystem(fileSystem);
        int randomFileNumber = integer(0, listOfFiles.size() - 1);
        String randomFilename = "file-" + randomFileNumber;
        fileSystem.removeFileFromFileSystem(randomFilename);
        long availableSpaceBeforeDefrag = fileSystem.getAvailableSpace();
        long expectedSpace = availableSpaceBeforeDefrag+listOfFiles.get(randomFileNumber).getTotalLength();
        fileSystem.defragmentFileSystem();
        long availableSpaceAfterDefrag = fileSystem.getAvailableSpace();
        assertEquals(expectedSpace, availableSpaceAfterDefrag);
    }

    @Test
    public void shouldDefragmentFileSystemAndReadFiles() throws IOException {
        List<File> listOfFilesBeforeDefrag = writeSomeFilesToFileSystem(fileSystem);
        int randomFileNumber = integer(0, listOfFilesBeforeDefrag.size() - 1);
        String randomFilename = "file-" + randomFileNumber;
        fileSystem.removeFileFromFileSystem(randomFilename);
        listOfFilesBeforeDefrag.remove(randomFileNumber);
        fileSystem.defragmentFileSystem();
        List<File> listOfFilesAfterDefrag = readAllFiles(fileSystem, listOfFilesBeforeDefrag);
        assertEquals(listOfFilesBeforeDefrag.size(), listOfFilesAfterDefrag.size());
        assertAllFilesEqual(listOfFilesBeforeDefrag, listOfFilesAfterDefrag);
    }

    @Test
    public void defragShouldMovePointer_whenRemovedFileWereAtTheEndOfFileSystem() throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        FileSystem fileSystem = getNewFileSystem(1024);
        File file1 = getFileWithNameAndContent("file1", unicode(1, 40));
        File file2 = getFileWithNameAndContent("file2", unicode(1, 40));
        File file3 = getFileWithNameAndContent("file3", unicode(1, 40));
        fileSystem.writeFileToFileSystem(file1);
        fileSystem.writeFileToFileSystem(file2);
        fileSystem.writeFileToFileSystem(file3);
        fileSystem.removeFileFromFileSystem(file2.getName());
        fileSystem.removeFileFromFileSystem(file3.getName());
        long availableSpaceBeforeDefrag = fileSystem.getAvailableSpace();
        long expectedSpace = availableSpaceBeforeDefrag + file2.getTotalLength() + file3.getTotalLength();
        fileSystem.defragmentFileSystem();
        long availableSpaceAfterDefrag = fileSystem.getAvailableSpace();
        assertEquals(expectedSpace, availableSpaceAfterDefrag);;
    }

    @Test
    public void shouldNotDefragmentFileSystem_whenThereAreNoRemovedFiles() throws IOException {
        File file1 = getFileWithNameAndContent("file1", unicode(1, 40));
        File file2 = getFileWithNameAndContent("file2", unicode(1, 40));
        fileSystem.writeFileToFileSystem(file1);
        fileSystem.writeFileToFileSystem(file2);
        long availableSpaceBeforeDefrag = fileSystem.getAvailableSpace();
        fileSystem.defragmentFileSystem();
        long availableSpaceAfterDefrag = fileSystem.getAvailableSpace();
        assertEquals(availableSpaceBeforeDefrag, availableSpaceAfterDefrag);
    }

    @Test
    public void shouldFormatFileSystem() throws IOException {
        writeSomeFilesToFileSystem(fileSystem);
        fileSystem.formatFileSystem();
        long availableSpaceAfterFormat = fileSystem.getAvailableSpace();
        assertEquals(DEFAULT_FILE_SYSTEM_SIZE - 8, availableSpaceAfterFormat);
    }

    @Test
    public void shouldOverwriteFile() throws IOException {
        List<File> listOfFiles = writeSomeFilesToFileSystem(fileSystem);
        int randomFileNumber = integer(0, listOfFiles.size() - 1);
        File original = listOfFiles.get(randomFileNumber);
        File expected = getFileWithNameAndContent(original.getName(), "overwrittenContent");
        fileSystem.overwriteFile(expected);
        assertFilesEqual(expected, fileSystem.readFileFromFileSystem(original.getName()));
    }

    @Test
    public void shouldListAllFiles() throws IOException {
        List<File> files = writeSomeFilesToFileSystem(fileSystem);
        List<String> expected = files.stream().map(File::getName).collect(Collectors.toList());
        List<String> actual = fileSystem.listFiles();
        assertEquals(expected.size(), actual.size());
        actual.sort(new ComparatorOfFilenames());
        assertEquals(expected, actual);
    }

    class ComparatorOfFilenames implements Comparator<String> {
        public int compare(String filename1, String filename2) {
            Integer first = Integer.valueOf(filename1.split("-")[1]);
            Integer second = Integer.valueOf(filename2.split("-")[1]);
            return first - second;
        }
    }

    private List<File> writeSomeFilesToFileSystem(FileSystem fileSystem) throws IOException {
        List<File> listOfFiles = new ArrayList<>();
        for (int i = 0; i < integer(5, 20); i++) {
            String fileName = "file-" + i;
            File file = getFileWithNameAndContent(fileName, unicode(1, 40));
            listOfFiles.add(file);
            fileSystem.writeFileToFileSystem(file);
        }
        return listOfFiles;
    }

    private File getFileWithNameAndContent(String name, String content) {
        return new File(name, content.getBytes());
    }

    private List<File> readAllFiles(FileSystem fileSystem, List<File> files) throws IOException {
        List<File> newFiles = new ArrayList<>();
        for (File file: files) {
            newFiles.add(fileSystem.readFileFromFileSystem(file.getName()));
        }
        return newFiles;
    }

    private void assertFilesEqual(File expected, File actual) {
        assertEquals(expected.getContentLength(), actual.getContentLength());
        assertEquals(expected.getName(), actual.getName());
        assertArrayEquals(expected.getContent(), actual.getContent());
    }

    private void assertAllFilesEqual(List<File> expectedList, List<File> actualList) {
        for (int i = 0; i < expectedList.size(); i++) {
            assertFilesEqual(expectedList.get(i), actualList.get(i));
        }
    }

    private FileSystem getNewFileSystem(int size) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<FileSystem> constructor = FileSystem.class.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(size);
    }
}
