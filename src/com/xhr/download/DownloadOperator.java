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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Created by xhrong on 2014/6/28.
 */
public class DownloadOperator implements Runnable {

    private static final String TAG = "DownloadOperator";

    // 100 kb
    private static final long REFRESH_INTEVAL_SIZE = 100 * 1024;

    private DownloadManager manager;

    private DownloadTask task;

    // already try times
    private int tryTimes;

    private volatile boolean pauseFlag;
    private volatile boolean stopFlag;

    private String filePath;


    /* 线程数 */
    private DownloadThread[] threads;
    /* 缓存各线程下载的长度*/
    private Map<Integer, Integer> data = new ConcurrentHashMap<Integer, Integer>();
    /* 每条线程下载的长度 */
    private int block;
    private int fileSize;
    private File saveFile;
    private int downloadSize;

    DownloadOperator(DownloadManager manager, DownloadTask task) {
        this.manager = manager;
        this.task = task;
        this.tryTimes = 0;
        this.threads = new DownloadThread[3];
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

    long preTime=System.currentTimeMillis();
    public void append(int size) {
        this.downloadSize += size;
        long curTime=System.currentTimeMillis();
        if(curTime-preTime>3000){
            long speed=(this.downloadSize-task.getDownloadFinishedSize())*3000/(curTime-preTime);
            task.setDownloadFinishedSize(this.downloadSize);
            manager.updateDownloadTask(task, this.downloadSize, speed);
            preTime=curTime;
        }
    }


    /**
     * 下载不支持断点续传的文件
     */
    private void downloadUnresumableFile() {
        do {
            RandomAccessFile raf = null;
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                conn = initConnection(false);
                conn.connect();
                //这一句要放在确定是否支持断点之后
                raf = buildDownloadFile();

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

    @Override
    public void run() {
        try {
            HttpURLConnection conn = initConnection(true);
            conn.connect();
            String acceptRange = conn.getHeaderField("Accept-Ranges");
            if (acceptRange == null || acceptRange.equals("none")) {//不支持断点续传，需要重新处理
                Log.i(TAG, task.getName() + "不支持断点续传");
                conn.disconnect();
                //从头开始下载
                task.setDownloadTotalSize(0);
                task.setDownloadFinishedSize(0);

                downloadUnresumableFile();
            } else {
                buildDownloadFile();
                task.setDownloadSavePath(filePath);
                if (task.getDownloadTotalSize() == 0) {
                    task.setDownloadTotalSize(conn.getContentLength());
                }
                if (TextUtils.isEmpty(task.getMimeType())) {
                    task.setMimeType(conn.getContentType());
                }
                task.setStatus(DownloadTask.STATUS_RUNNING);
                manager.onDownloadStarted(task);
                this.fileSize = (int) task.getDownloadTotalSize();
                //计算每条线程下载的数据长度
                this.block = (this.fileSize % this.threads.length) == 0 ? this.fileSize / this.threads.length : this.fileSize / this.threads.length + 1;
                this.saveFile = new File(this.filePath);
                try {
                    RandomAccessFile randOut = new RandomAccessFile(this.saveFile, "rw");
                    if (this.fileSize > 0) randOut.setLength(this.fileSize);
                    randOut.close();
                    URL url = new URL(task.getUrl());

                    if (this.data.size() != this.threads.length) {
                        this.data.clear();

                        for (int i = 0; i < this.threads.length; i++) {
                            this.data.put(i + 1, 0);//初始化每条线程已经下载的数据长度为0
                        }
                    }

                    for (int i = 0; i < this.threads.length; i++) {//开启线程进行下载
                        int downLength = this.data.get(i + 1);

                        if (downLength < this.block && task.getDownloadFinishedSize() < this.fileSize) {//判断线程是否已经完成下载,否则继续下载
                            this.threads[i] = new DownloadThread(this, url, this.saveFile, this.block, this.data.get(i + 1), i + 1);
                            this.threads[i].setPriority(7);
                            this.threads[i].start();
                        } else {
                            this.threads[i] = null;
                        }
                    }

                    //  this.fileService.save(this.downloadUrl, this.data);
                    boolean notFinish = true;//下载未完成

                    while (notFinish) {// 循环判断所有线程是否完成下载
                        Thread.sleep(900);
                        notFinish = false;//假定全部线程下载完成

                        for (int i = 0; i < this.threads.length; i++) {
                            if (this.threads[i] != null && !this.threads[i].isFinish()) {//如果发现线程未完成下载
                                notFinish = true;//设置标志为下载没有完成

                                if (this.threads[i].getDownLength() == -1) {//如果下载失败,再重新下载
                                    this.threads[i] = new DownloadThread(this, url, this.saveFile, this.block, this.data.get(i + 1), i + 1);
                                    this.threads[i].setPriority(7);
                                    this.threads[i].start();
                                }
                            }
                        }

                        //  if (listener != null) listener.onDownloadSize(this.downloadSize);//通知目前已经下载完成的数据长度
                    }

                    //    fileService.delete(this.downloadUrl);
                } catch (Exception e) {
                    //  print(e.toString());
                    throw new Exception("file download fail");
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        if (file.exists() && task.getDownloadFinishedSize() == 0) {
            file.delete();
            file.createNewFile();
        }
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
        //    conn.setUseCaches(true);
        if (support) {
            Log.i("Range", "" + task.getDownloadFinishedSize());
            conn.setRequestProperty("Range", "bytes=" + task.getDownloadFinishedSize() + "-");
        }

        return conn;
    }

}