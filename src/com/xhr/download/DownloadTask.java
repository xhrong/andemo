package com.xhr.download;

import org.json.JSONObject;

/**
 * Created by xhrong on 2014/6/28.
 */
public class DownloadTask {

    public static final String ID = "_id";
    public static final String URL = "a";
    public static final String MIMETYPE = "b";
    public static final String SAVEPATH = "c";
    public static final String FINISHEDSIZE = "d";
    public static final String TOTALSIZE = "e";
    public static final String NAME = "f";
    public static final String STATUS = "g";
    public static final String CUSTOMPARAM = "f";

    public static final int STATUS_PENDDING = 1;
    public static final int STATUS_RUNNING = 2;
    public static final int STATUS_PAUSED = 4;
    public static final int STATUS_CANCELED = 8;
    public static final int STATUS_FINISHED = 16;
    public static final int STATUS_ERROR = 32;
    //  public static final int STATUS_RESTART = 1 << 6;

    private String id;
    private String name;
    private String url;
    private String mimeType;
    private String downloadSavePath;
    private long downloadFinishedSize;
    private long downloadTotalSize;
    private int status;

    private String customParam;//用户自定义参数，方便用户控制

    // @Transparent no need to persist
    private long downloadSpeed;


    public DownloadTask() {
        downloadFinishedSize = 0;
        downloadTotalSize = 0;
        status = STATUS_PENDDING;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof DownloadTask)) {
            return false;
        }
        DownloadTask task = (DownloadTask) o;
        if (this.name == null || this.downloadSavePath == null) {
            return this.url.equals(task.url);
        }
        return this.name.equals(task.name) && this.url.equals(task.url) && this.downloadSavePath.equals(task.downloadSavePath);
    }

    @Override
    public int hashCode() {
        int code = name == null ? 0 : name.hashCode();
        code += url.hashCode();
        return code;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDownloadSavePath() {
        return downloadSavePath;
    }

    public void setDownloadSavePath(String downloadSavePath) {
        this.downloadSavePath = downloadSavePath;
    }

    public long getDownloadFinishedSize() {
        return downloadFinishedSize;
    }

    public void setDownloadFinishedSize(long downloadFinishedSize) {
        this.downloadFinishedSize = downloadFinishedSize;
    }

    public long getDownloadTotalSize() {
        return downloadTotalSize;
    }

    public void setDownloadTotalSize(long downloadTotalSize) {
        this.downloadTotalSize = downloadTotalSize;
    }

    public long getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(long downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCustomParam() {
        return customParam;
    }

    public void setCustomParam(String customParam) {
        this.customParam = customParam;
    }

    @Override
    public String toString() {
        String str = "ID:" + this.getId() + "\n";
        return str;
    }
}