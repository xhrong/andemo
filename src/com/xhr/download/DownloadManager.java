package com.xhr.download;

import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by xhrong on 2014/6/28.
 */



public class DownloadManager {

    private static final String TAG = "DownloadManager";

    private static DownloadManager instance;

    private DownloadConfig config;

    private HashMap<DownloadTask, DownloadOperator> taskOperators = new HashMap<DownloadTask, DownloadOperator>();

    private HashMap<DownloadTask, DownloadListener> taskListeners = new HashMap<DownloadTask, DownloadListener>();

    private LinkedList<DownloadObserver> taskObservers = new LinkedList<DownloadObserver>();

    private DownloadProvider provider;

    private static Handler handler = new Handler();

    private ExecutorService pool;

    private DownloadManager() {

    }

    public static synchronized DownloadManager getInstance() {
        if(instance == null) {
            instance = new DownloadManager();
        }

        return instance;
    }

    public void init() {
        config = DownloadConfig.getDefaultDownloadConfig(this);
        provider = config.getProvider(this);
        pool = Executors.newFixedThreadPool(config.getMaxDownloadThread());
    }

    public void init(DownloadConfig config) {
        if(config == null) {
            init();
            return ;
        }
        this.config = config;
        provider = config.getProvider(this);
        pool = Executors.newFixedThreadPool(config.getMaxDownloadThread());
    }

    public DownloadConfig getConfig() {
        return config;
    }

    public void setConfig(DownloadConfig config) {
        this.config = config;
    }

    /**
     * 添加下载任务
     * @param task
     */
    public void addDownloadTask(DownloadTask task) {
        addDownloadTask(task, null);
    }

    /**
     * 添加下载任务
     * @param task
     * @param listener  任务监听器
     */
    public void addDownloadTask(DownloadTask task, DownloadListener listener) {
        //如果没有下载地下载，就不要添加任务了
        if(TextUtils.isEmpty(task.getUrl())) {
            throw new IllegalArgumentException("task's url cannot be empty");
        }
        //如果没有任务id，就不要下载了，让用户自行添加任务id，有利于用户管理下载任务
        if(TextUtils.isEmpty(task.getId())){
            throw new IllegalArgumentException("task's id cannot be empty");
        }
        //如果这个任务已经存在了，就不要添加了
        if(taskOperators.containsKey(task)) {
            return ;
        }
         //如果是重新下载 ，则将已下载数据量置0，注：在下载过程中，不能重新下载
        if(task.getStatus()==DownloadTask.STATUS_RESTART){
            task.setDownloadFinishedSize(0);
        }

        Log.v(TAG, "addDownloadTask: " + task.getName());

        DownloadOperator operator = new DownloadOperator(this, task);
        taskOperators.put(task, operator);
        if(listener != null) {
            taskListeners.put(task, listener);
        }

        task.setStatus(DownloadTask.STATUS_PENDDING);
        //查询是否有下载记录
        DownloadTask historyTask = provider.findDownloadTaskById(task.getId());
        if(historyTask == null) {
            provider.saveDownloadTask(task);
        } else {
            provider.updateDownloadTask(task);
        }
        pool.submit(operator);
    }

    /**
     * 获取任务的监听器
     * @param task
     * @return
     */
    public DownloadListener getDownloadListenerForTask(DownloadTask task) {
        if(task == null) {
            return null;
        }
        return taskListeners.get(task);
    }

    /**
     * 更新任务监听器
     * @param task
     * @param listener
     */
    public void updateDownloadTaskListener(DownloadTask task, DownloadListener listener) {
        Log.v(TAG, "try to updateDownloadTaskListener");
        if(task == null || !taskOperators.containsKey(task)) {
            return ;
        }
        Log.v(TAG, "updateDownloadTaskListener");
        //HashMap，会直接替换掉对就的值
        taskListeners.put(task, listener);
    }

    /**
     * 移除任务监听器
     * @param task
     */
    public void removeDownloadTaskListener(DownloadTask task) {
        Log.v(TAG, "try to removeDownloadTaskListener");
        if(task == null || !taskListeners.containsKey(task)) {
            return ;
        }
        Log.v(TAG, "removeDownloadTaskListener");
        taskListeners.remove(task);
    }

    public void pauseDownload(DownloadTask task) {
        Log.v(TAG, "pauseDownload: " + task.getName());
        DownloadOperator operator = taskOperators.get(task);
        if(operator != null) {
            operator.pauseDownload();
        }
    }

    public void resumeDownload(DownloadTask task) {
        Log.v(TAG, "resumeDownload: "+ task.getName());
        DownloadOperator operator = taskOperators.get(task);
        if(operator != null) {
            operator.resumeDownload();
        }
    }

    public void cancelDownload(DownloadTask task) {
        Log.v(TAG, "cancelDownload: "+ task.getName());
        DownloadOperator operator = taskOperators.get(task);
        if(operator != null) {
            operator.cancelDownload();
        } else {
            task.setStatus(DownloadTask.STATUS_CANCELED);
            provider.updateDownloadTask(task);
        }
    }

    public DownloadTask findDownloadTaskById(String id) {
        Iterator<DownloadTask> iterator = taskOperators.keySet().iterator();
        while(iterator.hasNext()) {
            DownloadTask task = iterator.next();
            if(task.getId().equals(id)) {
                Log.v(TAG, "findDownloadTaskByAdId from map");
                return task;
            }
        }

        Log.v(TAG, "findDownloadTaskByAdId from provider");
        return provider.findDownloadTaskById(id);
    }

    public List<DownloadTask> getAllDownloadTask() {
        return provider.getAllDownloadTask();
    }

    public void registerDownloadObserver(DownloadObserver observer) {
        if(observer == null) {
            return ;
        }
        taskObservers.add(observer);
    }

    public void unregisterDownloadObserver(DownloadObserver observer) {
        if(observer == null) {
            return ;
        }
        taskObservers.remove(observer);
    }

    public void close() {
        pool.shutdownNow();
    }

    public void notifyDownloadTaskStatusChanged(final DownloadTask task) {
        handler.post(new Runnable() {

            public void run() {
                for(DownloadObserver observer : taskObservers) {
                    observer.onDownloadTaskStatusChanged(task);
                }
            }
        });
    }

    /**
     * 更新任务状态
     * @param task
     * @param finishedSize  已经下载大小
     * @param trafficSpeed  下载速度
     */
    void updateDownloadTask(final DownloadTask task, final long finishedSize, final long trafficSpeed) {
        task.setStatus(DownloadTask.STATUS_RUNNING);
        final DownloadListener listener = taskListeners.get(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadUpdated(task, finishedSize, trafficSpeed);
                }
            }

        });
    }

    void onDownloadStarted(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_RUNNING);
        final DownloadListener listener = taskListeners.get(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadStart(task);
                }
            }
        });
    }


    void onDownloadPaused(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_PAUSED);
        final DownloadListener listener = taskListeners.get(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadPaused(task);
                }
            }
        });
    }

    void onDownloadResumed(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_RUNNING);
        final DownloadListener listener = taskListeners.get(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadResumed(task);
                }
            }
        });
    }

    void onDownloadCanceled(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_CANCELED);
        final DownloadListener listener = taskListeners.get(task);
        removeTask(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadCanceled(task);
                }

            }
        });
    }

    void onDownloadSuccessed(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_FINISHED);
        final DownloadListener listener = taskListeners.get(task);
        removeTask(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadSuccessed(task);
                }
            }

        });
    }

    void onDownloadFailed(final DownloadTask task) {
        task.setStatus(DownloadTask.STATUS_ERROR);
        final DownloadListener listener = taskListeners.get(task);
        removeTask(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                provider.updateDownloadTask(task);
                if(listener != null) {
                    listener.onDownloadFailed(task);
                }
            }
        });
    }

    void onDownloadRetry(final DownloadTask task) {
        final DownloadListener listener = taskListeners.get(task);
        handler.post(new Runnable() {

            @Override
            public void run() {
                if(listener != null) {
                    listener.onDownloadRetry(task);
                }
            }
        });
    }

    private void removeTask(DownloadTask task) {
        taskOperators.remove(task);
        taskListeners.remove(task);
    }

}