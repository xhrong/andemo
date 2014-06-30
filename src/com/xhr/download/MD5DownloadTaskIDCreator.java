package com.xhr.download;

import com.xhr.download.util.MD5;

/**
 * Created by xhrong on 2014/6/28.
 */
public class MD5DownloadTaskIDCreator implements DownloadTaskIDCreator {

    @Override
    public String createId(DownloadTask task) {
        return MD5.getMD5(task.getUrl());
    }

}
