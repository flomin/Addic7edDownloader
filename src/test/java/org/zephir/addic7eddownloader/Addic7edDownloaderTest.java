package org.zephir.addic7eddownloader;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zephir.addic7eddownloader.core.Addic7edDownloaderConstants;
import org.zephir.addic7eddownloader.core.Addic7edDownloaderCore;

public class Addic7edDownloaderTest {
    private static Logger log = LoggerFactory.getLogger(Addic7edDownloaderTest.class);

    private static final File SOURCE_FOLDER = new File("C:\\wInd\\Boulot\\Java\\Addic7edDownloader\\src\\test\\resources\\source\\");
    private static final File WORK_FOLDER = new File("C:\\wInd\\Boulot\\Java\\Addic7edDownloader\\src\\test\\resources\\testFolder\\");

    @BeforeClass
    public static void setUpClass() throws Exception {}

    @AfterClass
    public static void tearDownClass() throws Exception {}

    @Before
    public void setUp() throws Exception {
//        createFakeEpisodeList(new File("E:\\Vidz\\Séries\\You Me Her"), SOURCE_FOLDER);

        FileUtils.deleteDirectory(WORK_FOLDER);
        FileUtils.copyDirectory(SOURCE_FOLDER, WORK_FOLDER);
    }

    @After
    public void tearDown() {}

    public void createFakeEpisodeList(File folder, File destination) throws Exception {
        File[] foldersInFolder = folder.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
        for (File dir : foldersInFolder) {
            createFakeEpisodeList(dir, destination);
        }
        File[] videoFilesInFolder = folder.listFiles((File dir, String name) -> {
            return Addic7edDownloaderConstants.FILE_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase());
        });

        for (File videoFile : videoFilesInFolder) {
            File emptyFileToCreate = new File(destination, videoFile.getName());
            FileUtils.touch(emptyFileToCreate);
            log.debug("createEpisodeList(...) empty file created: '" + emptyFileToCreate.getAbsolutePath() + "'");
        }
    }

    @Test
    public void testCore() throws Exception {
        final Addic7edDownloaderCore core = new Addic7edDownloaderCore();
        core.setFolderToProcess(WORK_FOLDER);
        core.setLangList(Arrays.asList(Locale.FRENCH, Locale.ENGLISH));
        core.processFolder();
    }
}
