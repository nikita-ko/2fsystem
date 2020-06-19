package home.work;

import home.work.system.File;
import home.work.system.FileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.qala.datagen.RandomShortApi.alphanumeric;
import static io.qala.datagen.RandomShortApi.unicode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileSystemThreadSafetyTest {
    private final static int DEFAULT_FILE_SYSTEM_SIZE = 10*1024*1024; //10MB
    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        fileSystem = getNewFileSystem(DEFAULT_FILE_SYSTEM_SIZE);
    }

    @AfterEach
    public void cleanUp() {
        java.io.File fileSystem = new java.io.File("fileSystem");
        fileSystem.delete();
    }

    @Test
    public void shouldReadInParallel() throws IOException, ExecutionException, InterruptedException {
        List<File> expectedFiles = write5FilesToFileSystem(fileSystem);
        List<File> actualFiles = readAndWriteFilesInParallel(expectedFiles, true);
        assertEquals(expectedFiles.size(), actualFiles.size());
        assertAllFilesEqual(expectedFiles, actualFiles);
    }

    @Test
    public void shouldReadAndWriteInParallel() throws IOException, ExecutionException, InterruptedException {
        List<File> expectedFiles = write5FilesToFileSystem(fileSystem);
        List<File> actualFiles = readAndWriteFilesInParallel(expectedFiles, false);
        assertEquals(expectedFiles.size(), actualFiles.size());
        assertAllFilesEqual(expectedFiles, actualFiles);
    }

    @Test
    public void shouldReadAndRemoveInParallel() throws IOException, ExecutionException, InterruptedException {
        List<File> expectedFiles = write5FilesToFileSystem(fileSystem);
        List<File> actualFiles = readAndWriteFilesInParallel(expectedFiles, true);
        assertEquals(expectedFiles.size(), actualFiles.size());
        assertAllFilesEqual(expectedFiles, actualFiles);
    }

    private List<File> readAndWriteFilesInParallel(List<File> expectedFiles, boolean readOnly) throws ExecutionException, InterruptedException {
        List<Future<File>> actualFutureFiles = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);
        for (File file : expectedFiles) {
            actualFutureFiles.add(
                    pool.submit(new ReadTask(latch, file.getName()))
            );
        }
        if (!readOnly) {
            for (int i = 0; i < expectedFiles.size(); i ++) {
                pool.submit(new WriteTask(latch));
            }
        }
        latch.countDown();
        return getResultsFromFutures(actualFutureFiles);
    }

    class ReadTask implements Callable<File> {
        private CountDownLatch latch;
        private String filename;

        private ReadTask(CountDownLatch latch, String filename) {
            this.latch = latch;
            this.filename = filename;
        }

        @Override
        public File call() {
            File actual = new File("wrong result for file " + filename, new byte[0]);
            try {
                //wait to make threads overlap
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                actual = fileSystem.readFileFromFileSystem(filename);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return actual;
        }
    }

    class WriteTask implements Runnable {
        private CountDownLatch latch;

        private WriteTask(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                //wait to make threads overlap
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                fileSystem.writeFileToFileSystem(getFileWithNameAndContent(alphanumeric(5), unicode(1, 40)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<File> getResultsFromFutures(List<Future<File>> actualFutureFiles) throws ExecutionException, InterruptedException {
        List<File> actualFiles = new ArrayList<>();
        for (Future<File> f : actualFutureFiles) {
            actualFiles.add(f.get());
        }
        return actualFiles;
    }

    private void assertAllFilesEqual(List<File> expectedList, List<File> actualList) {
        for (int i = 0; i < expectedList.size(); i++) {
            assertFilesEqual(expectedList.get(i), actualList.get(i));
        }
    }

    private void assertFilesEqual(File expected, File actual) {
        assertEquals(expected.getContentLength(), actual.getContentLength());
        assertEquals(expected.getName(), actual.getName());
        assertArrayEquals(expected.getContent(), actual.getContent());
    }

    private File getFileWithNameAndContent(String name, String content) {
        return new File(name, content.getBytes());
    }

    private List<File> write5FilesToFileSystem(FileSystem fileSystem) throws IOException {
        List<File> listOfFiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String fileName = "file" + i;
            File file = getFileWithNameAndContent(fileName, unicode(1, 40));
            listOfFiles.add(file);
            fileSystem.writeFileToFileSystem(file);
        }
        return listOfFiles;
    }

    private FileSystem getNewFileSystem(int size) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<FileSystem> constructor = FileSystem.class.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(size);
    }
}
