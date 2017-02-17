package org.zephir.addic7eddownloader.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zephir.addic7eddownloader.core.model.DownloadedResource;
import org.zephir.addic7eddownloader.core.model.Episode;
import org.zephir.util.exception.CustomException;

public class Addic7edDownloaderCore implements Addic7edDownloaderConstants {
    private static Logger log = LoggerFactory.getLogger(Addic7edDownloaderCore.class);

    private File folderToProcess;
    private List<Locale> langList;

    private ExecutorService downloadEpisodeSubtitleUrlPool;
    private ExecutorService downloadSubtitlePool;

    public Addic7edDownloaderCore() {}

    public void processFolder() throws CustomException {
        try {
            if (!folderToProcess.exists()) {
                log.error("processFolder() KO: folder must exist ('" + folderToProcess.getAbsolutePath() + "')");
                throw new CustomException("processFolder() KO: folder must exist ('" + folderToProcess.getAbsolutePath() + "')");
            }
            if (langList.isEmpty()) {
                log.error("processFolder() KO: no language selected");
                throw new CustomException("processFolder() KO: no language selected");
            }
            final String folderStr = "Folder to process: '" + folderToProcess.getAbsolutePath() + "'";
            log.info(StringUtils.repeat("_", folderStr.length()));
            log.info(folderStr);
            String langListStr = "[" + langList.stream().map(lang -> {
                return lang.toLanguageTag();
            }).collect(Collectors.joining(", ")) + "]";
            log.info("Language(s) selected: " + langListStr);
            log.info(StringUtils.repeat("¯", folderStr.length()));

            // extract episode infos
            List<Episode> episodeList = extractEpisodeInfos(folderToProcess);
            log.info(episodeList.size() + " episode(s) found !");

            // get url and download
            downloadEpisodeSubtitleUrlPool = Executors.newFixedThreadPool(10);
            downloadSubtitlePool = Executors.newFixedThreadPool(10);
            for (Episode episode : episodeList) {
                downloadEpisodeSubtitleUrlPool.submit(() -> {
                    try {
                        for (Locale lang : langList) {
                            File srtFile = getSubtitleFile(episode, lang);
                            if (srtFile.exists()) {
                                log.info("Subtitle file already exists, skipping: " + srtFile.getName());
                            } else {
                                downloadEpisodeSubtitleUrl(episode, lang);
                            }
                        }
                    } catch (CustomException e) {
                        if (TRACE) {
                            log.error("processFolder() KO for episode='" + episode + "': " + e, e);
                        } else {
                            log.error(e.getMessage());
                        }
                    } catch (Exception e) {
                        log.error("processFolder() KO for episode='" + episode + "': " + e, e);
                    }
                });
            }
            downloadEpisodeSubtitleUrlPool.shutdown();
            downloadEpisodeSubtitleUrlPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            downloadSubtitlePool.shutdown();
            log.info("Waiting for downloads to finish");
            downloadSubtitlePool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            log.info("All downloads finished");

            log.info("Generating log file");
            writeLogFile(episodeList);
            log.info("Log file generated");

        } catch (CustomException e) {
            throw e;
        } catch (Throwable e) {
            log.error("processFolder() KO: " + e, e);
            throw new CustomException("processFolder() KO: " + e, e);
        }
    }

    private void writeLogFile(List<Episode> episodeList) throws IOException {
        File logFile = new File(folderToProcess, "addic7edDownloader.log");
        String logContent = "";
        if (logFile.exists()) {
            logContent = FileUtils.readFileToString(logFile, "UTF-8") + "\n\n";
        }
        String currentdDate = new SimpleDateFormat("yyyy_MM.dd HH:mm:ss").format(new Date());
        String dateSeparator = StringUtils.repeat("-", currentdDate.length());
        logContent += dateSeparator + "\n" + currentdDate + "\n" + dateSeparator + "\n";
        logContent += episodeList.stream().map(episode -> {
            String epSrtstr = episode.getSrtFilesToString();
            return StringUtils.isNotBlank(epSrtstr) ? episode.getFile().getName() + "\n" + epSrtstr : "";
        }).collect(Collectors.joining("\n"));
        FileUtils.write(logFile, logContent, "UTF-8");
    }

    private static File getSubtitleFile(Episode episode, Locale lang) {
        String destinationFilename = FilenameUtils.getBaseName(episode.getFile().getName()) + "-" + lang.toLanguageTag() + ".srt";
        return new File(episode.getFile().getParentFile(), destinationFilename);
    }

    private static void downloadSubtitle(Episode episode, String subtitleUrl, Locale lang, String refererUrl) {
        try {
            File destinationFile = getSubtitleFile(episode, lang);
            log.info("Downloading: '" + subtitleUrl + "' -> '" + destinationFile.getName() + "'");

            DownloadedResource srtResource = downloadResource(subtitleUrl, refererUrl);
            String srtStartStr = new String(srtResource.getContentAsByteArray(), 0, 100, "UTF-8");
            if (srtStartStr.contains("DOCTYPE")) {
                throw new Exception("Bad srt file starting with: " + srtStartStr);
            }
            FileUtils.writeByteArrayToFile(destinationFile, srtResource.getContentAsByteArray());
            episode.addSrtFile(lang, refererUrl, srtResource.getFilename());
            log.info("Download OK: '" + subtitleUrl + "': '" + srtResource.getFilename() + "' -> '" + destinationFile.getName() + "'");

        } catch (Exception e) {
            log.error("downloadSubtitle(episode='" + episode + "', url='" + subtitleUrl + "') KO: " + e, e);
        }
    }

    @SuppressWarnings("serial")
    public static final Map<Locale, String> LANG_MAP = new HashMap<Locale, String>() {
        {
            put(Locale.ENGLISH, "1");
            put(Locale.FRENCH, "8");
        }
    };

    private static String getAddic7edUrlForEpisode(Episode episode, Locale lang) throws Exception {
        // build url
        // http://www.addic7ed.com/serie/Preacher/1/1/1
        // http://www.addic7ed.com/serie/{ShowName}/{SeasonNb}/{EpisodeNb}/{LanguageNb}
        String addict7edShowName = getAddict7edShowName(episode.getShowName());
        if (!LANG_MAP.containsKey(lang)) {
            throw new Exception("getEpisodeSubtitle(episode='" + episode + "') KO: language '" + lang.getDisplayName() + "' not found");
        }
        String languageNb = LANG_MAP.get(lang);
        String urlSuffix = addict7edShowName.replaceAll(" ", "_") + "/" + episode.getSeasonNb() + "/" + episode.getEpisodeNb() + "/" + languageNb;
        String episodeSubtitlesUrlString = ADDIC7ED_URL + "serie/" + urlSuffix;
        return episodeSubtitlesUrlString;
    }

    public static DownloadedResource downloadResource(String url, String refererUrl) throws Exception {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            if (StringUtils.isNotEmpty(refererUrl)) {
                connection.setRequestProperty("Referer", refererUrl);
            }
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                byte[] pageSource = IOUtils.toByteArray(connection);
                DownloadedResource resource = new DownloadedResource(pageSource);
                String contentDisposition = connection.getHeaderField("Content-Disposition");
                if (StringUtils.isNotBlank(contentDisposition)) {
                    String filename = StringUtils.substringBetween(contentDisposition, "filename=\"", "\"");
                    resource.setFilename(filename);
                }
                resource.setEncoding(connection.getContentEncoding());
                return resource;
            } else if (responseCode == 404) {
                throw new FileNotFoundException(url);
            } else {
                throw new CustomException("Url '" + url + "' unreachable, check network (status='" + responseCode + "')");
            }

        } catch (CustomException | FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("downloadUrlToByteArray(url='" + url + "', referer='" + refererUrl + "') KO: " + e, e);
            throw e;
        }
    }

    public void downloadEpisodeSubtitleUrl(Episode episode, Locale lang) throws Exception {
        try {
            // Get release name and url
            List<String> foundReleaseListFromAddic7ed = new ArrayList<>();
            String firstReleaseNameFromAddic7ed = null;
            String firstUrlSuffixFromAddic7ed = null;
            String foundReleaseNameFromAddic7ed = null;
            String foundUrlSuffixFromAddic7ed = null;

            String episodeSubtitlesUrlString = getAddic7edUrlForEpisode(episode, lang);
            String pageSource;
            try {
                pageSource = downloadResource(episodeSubtitlesUrlString, ADDIC7ED_URL).getContentAsString();
            } catch (FileNotFoundException e) {
                if (TRACE) {
                    throw new Exception("getEpisodeSubtitle(episode='" + episode + "') KO: url empty '" + episodeSubtitlesUrlString + "'");
                } else {
                    throw new CustomException(
                            "Release page '" + episodeSubtitlesUrlString + "' doesn't exist for file '" + episode.getFile().getName() + "' (lang='" + lang.toLanguageTag() + "'");
                }
            }
            Document doc = Jsoup.parse(pageSource);
            Elements selectTags = doc.select("#container95m");
            for (Element element : selectTags) {
                try {
                    String releaseName = null, downloadUrl = null;
                    Elements releaseNameElementList = element.select("td.NewsTitle");
                    if (releaseNameElementList.size() > 0) {
                        String releaseNameStr = releaseNameElementList.get(0).textNodes().get(0).text();
                        releaseName = StringUtils.substringBetween(releaseNameStr, "Version ", ",");
                    }
                    Elements downloadUrlElementList = element.select("a.buttonDownload");
                    if (downloadUrlElementList.size() > 0) {
                        downloadUrl = downloadUrlElementList.get(0).attr("href");
                    }
                    if (releaseName != null && downloadUrl != null) {
                        foundReleaseListFromAddic7ed.add(releaseName);
                        if (firstReleaseNameFromAddic7ed == null) {
                            // store first release
                            firstReleaseNameFromAddic7ed = releaseName;
                            firstUrlSuffixFromAddic7ed = downloadUrl;
                        }
                        if (releaseName.equalsIgnoreCase(episode.getReleaseName())) {
                            // exact name
                            foundReleaseNameFromAddic7ed = releaseName;
                            foundUrlSuffixFromAddic7ed = downloadUrl;
                            log.warn("Exact release '" + releaseName + "' found");
                            break;
                        } else if (releaseName.contains(episode.getReleaseName())) {
                            // approximate name
                            foundReleaseNameFromAddic7ed = releaseName;
                            foundUrlSuffixFromAddic7ed = downloadUrl;
                            log.warn("Approximate release '" + releaseName + "' found for episode release '" + episode.getReleaseName() + "'");
                            break;
                        } else {
                            // get comment section
                            Elements commentElementList = element.select("td.newsDate");
                            if (!commentElementList.isEmpty()) {
                                String commentContent = commentElementList.get(0).ownText();
                                if (commentContent.contains(episode.getReleaseName())) {
                                    foundReleaseNameFromAddic7ed = releaseName;
                                    foundUrlSuffixFromAddic7ed = downloadUrl;
                                    log.warn("Working release '" + releaseName + "' found for episode release '" + episode.getReleaseName() + "' (from comment='" + commentContent + "')");
                                    break;
                                }
                            }
                        }
                    }
                    if (TRACE) {
                        log.debug("getEpisodeSubtitle(...) found releaseName='" + releaseName + "', downloadUrl='" + downloadUrl + "'");
                    }
                } catch (Exception e) {
                    log.error("getEpisodeSubtitle(episode='" + episode + "') KO with releaseName/downloadUrl parsing: " + e, e);
                }
            } ;

            if (foundReleaseListFromAddic7ed.isEmpty()) {
                if (TRACE) {
                    throw new CustomException("No release found on url '" + episodeSubtitlesUrlString + "' for episode='" + episode + "'");
                } else {
                    throw new CustomException("No release found on url '" + episodeSubtitlesUrlString + "' for file='" + episode.getFile().getName() + "'");
                }
            }

            if (foundReleaseNameFromAddic7ed == null) {
                String releaseListStr = "[" + StringUtils.join(foundReleaseListFromAddic7ed, ", ") + "]";
                // we get the first url
                foundUrlSuffixFromAddic7ed = firstUrlSuffixFromAddic7ed;
                if (TRACE) {
                    log.warn("getEpisodeSubtitle(episode='" + episode + "') release '" + episode.getReleaseName() + "' not found among " + releaseListStr + " -> using first one '"
                            + firstReleaseNameFromAddic7ed + "'");
                } else {
                    log.warn("Release '" + episode.getReleaseName() + "' not found among " + releaseListStr + " -> using first one '" + firstReleaseNameFromAddic7ed + "' ("
                            + episode.getFile().getName() + ")");
                }
            }

            String downloadUrl = ADDIC7ED_URL + foundUrlSuffixFromAddic7ed;
            downloadSubtitlePool.submit(() -> {
                downloadSubtitle(episode, downloadUrl, lang, episodeSubtitlesUrlString);
            });

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("getEpisodeSubtitle(episode='" + episode + "') KO: " + e, e);
            throw e;
        }
    }

    private static List<String> ADDIC7ED_SHOWNAME_LIST;
    private static synchronized List<String> getAddic7edShowNameList() throws Exception {
        try {
            if (ADDIC7ED_SHOWNAME_LIST == null) {
                ADDIC7ED_SHOWNAME_LIST = new ArrayList<>();
                String pageSource = downloadResource(ADDIC7ED_URL, null).getContentAsString();
                Document doc = Jsoup.parse(pageSource);
                Elements selectTags = doc.select("#qsShow");
                if (selectTags.size() < 1) {
                    throw new Error("Problem while downloading Addic7ed show list from url='" + ADDIC7ED_URL + "'");
                }
                selectTags.get(0).childNodes().forEach(node -> {
                    if (node instanceof Element && "option".equals(((Element) node).tagName())) {
//                        String value = node.attr("value");
                        String name = ((TextNode) node.childNode(0)).text();
                        ADDIC7ED_SHOWNAME_LIST.add(name);
                    }
                });
            }
            return ADDIC7ED_SHOWNAME_LIST;

        } catch (Exception e) {
            log.error("getMapShowNameToId() KO: " + e, e);
            throw e;
        }
    }

    private static String getAddict7edShowName(String showName) throws Exception {
        try {
            // Search for the show name in the map
            List<String> potentialAddict7edShowNameList = new ArrayList<>();
            int currentIntersectionSize = -1;
            for (String addic7edShowName : getAddic7edShowNameList()) {
                List<String> addic7edShowNameWordList = Arrays.asList(addic7edShowName.replaceAll("[()]", "").split(" "));
                List<String> searchShowNameWordList = Arrays.asList(showName.replaceAll("[()]", "").split(" "));
                int intersectionSize = CollectionUtils.intersection(addic7edShowNameWordList, searchShowNameWordList).size();
                if (intersectionSize > 0) {
                    if (intersectionSize >= currentIntersectionSize) {
//                        log.debug("getAddict7edShowId(showName='" + showName + "') match found with Addict7ed Show '" + entry.getKey() + "' -> id=" + entry.getValue());
                        if (intersectionSize > currentIntersectionSize) {
                            potentialAddict7edShowNameList.clear();
                        }
                        potentialAddict7edShowNameList.add(addic7edShowName);
                        currentIntersectionSize = intersectionSize;
                    }
                }
            }

            if (potentialAddict7edShowNameList.isEmpty()) {
                log.error("getAddict7edShowId(showName='" + showName + "') KO: name not found on addic7ed website show list !");
                throw new Exception("getAddict7edShowId(showName='" + showName + "') KO: name not found on addic7ed website show list !");

            } else {
                String addict7edShowName = potentialAddict7edShowNameList.get(0);
                if (potentialAddict7edShowNameList.size() > 1) {
                    String showListStr = "[" + potentialAddict7edShowNameList.stream().collect(Collectors.joining(", ")) + "]";
                    if (TRACE) {
                        log.warn("getAddict7edShowId(showName='" + showName + "') WARN: several matching names found on addic7ed show list: " + showListStr + " -> using '"
                                + addict7edShowName + "'");
                    } else {
                        log.warn("Show name '" + showName + "' match several names on addic7ed show list: " + showListStr + " -> using '" + addict7edShowName + "'");
                    }
                }
                return addict7edShowName;
            }

        } catch (Exception e) {
            log.error("getAddict7edShowId(showName='" + showName + "') KO: " + e, e);
            throw e;
        }
    }

//    ^(
//        (?P<ShowNameA>.*[^ (_.]) # Show name
//        [ (_.]+
//        ((S|s)(?P<SeasonA>\d{1,2})(E|e)(?P<EpisodeA>\d{1,2})  # Season and Episode S01E01
//         |(?P<SeasonB>\d{1,2})(X|x)(?P<EpisodeB>\d{1,2}) # Season and Episode 1x2
//        )
//        [ (_.]+(.)*?
//        [ (_.-](?P<Release>(\w|[\[\]])*) # Release
//    )$
    // https://regex101.com/r/mR6oD4/70
    private static final Pattern EPISODE_PATTERN = Pattern.compile(
            "^((?<ShowName>.*[^ (_.])[ (_.]+((S|s)(?<SeasonA>\\d{1,2})(E|e)(?<EpisodeA>\\d{1,2})|(?<SeasonB>\\d{1,2})(X|x)(?<EpisodeB>\\d{1,2}))[ (_.]+(.)*?[ (_.-](?<Release>(\\w|[\\[\\]])*))$",
            Pattern.CASE_INSENSITIVE);

    private static List<Episode> extractEpisodeInfos(File folder) {
        List<Episode> episodeList = new ArrayList<>();
        File[] videoFilesInFolder = folder.listFiles((File dir, String name) -> {
            return Addic7edDownloaderConstants.FILE_EXTENSIONS.contains(FilenameUtils.getExtension(name).toLowerCase());
        });
        for (File videoFile : videoFilesInFolder) {
            String baseFilename = FilenameUtils.getBaseName(videoFile.getName());
            Matcher matcher = EPISODE_PATTERN.matcher(baseFilename);
            if (!matcher.matches()) {
                log.error("Filename doesn't match Episode pattern '" + baseFilename + "'");
            }

            Episode episode = new Episode(videoFile);
            String showName = matcher.group("ShowName").replaceAll("\\.", " ").replaceAll("_", " ").trim();
            episode.setShowName(showName);
            String seasonNbStr = matcher.group("SeasonA") != null ? matcher.group("SeasonA") : (matcher.group("SeasonB") != null ? matcher.group("SeasonB") : null);
            int seasonNb = Integer.parseInt(seasonNbStr);
            episode.setSeasonNb(seasonNb);
            String episodeNbStr = matcher.group("EpisodeA") != null ? matcher.group("EpisodeA") : (matcher.group("EpisodeB") != null ? matcher.group("EpisodeB") : null);
            int episodeNb = Integer.parseInt(episodeNbStr);
            episode.setEpisodeNb(episodeNb);
            String release = StringUtils.substringBefore(matcher.group("Release"), "[");
            episode.setReleaseName(release);
            episodeList.add(episode);
            if (TRACE) {
                log.debug("Episode found: " + episode);
            }
        }
        return episodeList;
    }

    public File getFolderToProcess() {
        return folderToProcess;
    }

    public void setFolderToProcess(File folderToProcess) {
        this.folderToProcess = folderToProcess;
    }

    public List<Locale> getLangList() {
        return langList;
    }

    public void setLangList(List<Locale> langList) {
        this.langList = langList;
    }
}
