package org.zephir.addic7eddownloader.core.model;

public class DownloadedResource {
    private byte[] content = null;
    private String encoding = null;
    private String filename = null;

    public DownloadedResource(byte[] content) {
        super();
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public byte[] getContentAsByteArray() {
        return content;
    }
    public String getContentAsString() throws Exception {
        return new String(content, encoding != null ? encoding : "UTF-8");
    }
    public void setContent(byte[] content) {
        this.content = content;
    }
    public String getEncoding() {
        return encoding;
    }
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
