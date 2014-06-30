package com.example.andemo.testdownloadlib;

import com.xhr.download.DownloadTask;
import com.xhr.download.DownloadTaskIDCreator;

/**
 * Created by xhrong on 2014/6/28.
 */
public class IDCreator implements DownloadTaskIDCreator {

    @Override
    public String createId(DownloadTask task) {
        return task.getUrl();
    }

}
