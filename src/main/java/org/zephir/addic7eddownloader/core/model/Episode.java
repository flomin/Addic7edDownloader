package org.zephir.addic7eddownloader.core.model;

import java.io.File;

public class Episode {
    private File file;
    private String showName;
    private String releaseName;
    private int seasonNb;
    private int episodeNb;

    public Episode(File file) {
        this.file = file;
    }

    public Episode(File file, String serieName, String releaseName, int seasonNb, int episodeNb) {
        super();
        this.file = file;
        this.showName = serieName;
        this.releaseName = releaseName;
        this.seasonNb = seasonNb;
        this.episodeNb = episodeNb;
    }

    @Override
    public String toString() {
        return "Episode [file=" + file + ", showName=" + showName + ", releaseName=" + releaseName + ", seasonNb=" + seasonNb + ", episodeNb=" + episodeNb + "]";
    }

    public String getShowName() {
        return showName;
    }
    public void setShowName(String serieName) {
        this.showName = serieName;
    }
    public String getReleaseName() {
        return releaseName;
    }
    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }
    public int getSeasonNb() {
        return seasonNb;
    }
    public void setSeasonNb(int seasonNb) {
        this.seasonNb = seasonNb;
    }
    public int getEpisodeNb() {
        return episodeNb;
    }
    public void setEpisodeNb(int episodeNb) {
        this.episodeNb = episodeNb;
    }
    public File getFile() {
        return file;
    }
    public void setFile(File file) {
        this.file = file;
    }
}
