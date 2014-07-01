package com.xhr.download;

import android.text.TextUtils;
import android.util.Log;
import com.xhr.download.util.FileUtil;
import com.xhr.download.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by xhrong on 2014/6/28.
 */
public class DownloadOperator implements Runnable {

    private static final String TAG="DownloadOperator";

    // 100 kb
    private static final long REFRESH_INTEVAL_SIZE = 100 * 1024;

    private DownloadManager manager;

    private DownloadTask task;

    // already try times
    private int tryTimes;

    private volatile boolean pauseFlag;
    private volatile boolean stopFlag;

    private String filePath;

    DownloadOperator(DownloadManager manager, DownloadTask task) {
        this.manager = manager;
        this.task = task;
        this.tryTimes = 0;
    }

    void pauseDownload() {
        if (pauseFlag) {
            return;
        }
        pauseFlag = true;
    }

    void resumeDownload() {
        if (!pauseFlag) {
            return;
        }
        pauseFlag = false;
        synchronized (this) {
            notify();
        }
    }

    void cancelDownload() {
        stopFlag = true;
        resumeDownload();
    }

    @Override
    public void run() {
        do {
            RandomAccessFile raf = null;
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                raf = buildDownloadFile();
                conn = initConnection(true);
                conn.connect();
                String acceptRange = conn.getHeaderField("Accept-Ranges");
                if (acceptRange == null || acceptRange.equals("none")) {//不支持断点续传，需要重新处理
                    Log.i(TAG,task.getName()+"不支持断点续传");
                    conn.disconnect();
                    conn = initConnection(false);
                    conn.connect();
                }

                task.setDownloadSavePath(filePath);
                if (task.getDownloadTotalSize() == 0) {
                    task.setDownloadTotalSize(conn.getContentLength());
                }
                if (TextUtils.isEmpty(task.getMimeType())) {
                    task.setMimeType(conn.getContentType());
                }
                task.setStatus(DownloadTask.STATUS_RUNNING);
                manager.onDownloadStarted(task);


                is = conn.getInputStream();

                byte[] buffer = new byte[8192];
                int count = 0;
                long total = task.getDownloadFinishedSize();
                long prevTime = System.currentTimeMillis();
                long achieveSize = total;
                while (!stopFlag && (count = is.read(buffer)) != -1) {
                    while (pauseFlag) {
                        manager.onDownloadPaused(task);
                        synchronized (this) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                manager.onDownloadResumed(task);
                            }
                        }
                    }

                    raf.write(buffer, 0, count);
                    total += count;

                    long tempSize = total - achieveSize;
                    if (tempSize > REFRESH_INTEVAL_SIZE) {
                        long tempTime = System.currentTimeMillis() - prevTime;
                        long speed = tempSize * 1000 / tempTime;
                        achieveSize = total;
                        prevTime = System.currentTimeMillis();
                        task.setDownloadFinishedSize(total);
                        task.setDownloadSpeed(speed);
                        manager.updateDownloadTask(task, total, speed);
                    }
                }
                task.setDownloadFinishedSize(total);

                if (stopFlag) {
                    manager.onDownloadCanceled(task);
                } else {
                    manager.onDownloadSuccessed(task);
                }
                break;
            } catch (IOException e) {
                e.printStackTrace();
                if (tryTimes > manager.getConfig().getRetryTime()) {
                    manager.onDownloadFailed(task);
                    break;
                } else {
                    tryTimes++;
                    manager.onDownloadRetry(task);
                    continue;
                }
            }
        } while (true);
    }

    private RandomAccessFile buildDownloadFile() throws IOException {
        String fileName = FileUtil.getFileNameByUrl(task.getUrl());
        //优先使用任务中设定的文件保存路径，如果没有，则用默认路径
        String fileSavePath = task.getDownloadSavePath();
        File file = new File(fileSavePath);
        if (file.isDirectory()) {
            file = new File(fileSavePath, fileName);
        } else if (file.isFile()) {
            //不用处理
        } else {
            file = new File(manager.getConfig().getDownloadSavePath(), fileName);
        }
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
            throw new IOException("cannot create download folder");
        }
        filePath = file.getAbsolutePath();
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        if (task.getDownloadFinishedSize() != 0) {
            raf.seek(task.getDownloadFinishedSize());
        }

        return raf;
    }

    private HttpURLConnection initConnection(boolean support) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(task.getUrl()).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setUseCaches(true);
        if (support) {
            Log.i("Range", "" + task.getDownloadFinishedSize());
            conn.setRequestProperty("Range", "bytes=" + task.getDownloadFinishedSize() + "-");
        }

        return conn;
    }

}