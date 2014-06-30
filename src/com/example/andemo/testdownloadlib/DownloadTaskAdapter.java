package com.example.andemo.testdownloadlib;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PermissionGroupInfo;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.example.andemo.R;
import com.xhr.download.DownloadListener;
import com.xhr.download.DownloadManager;
import com.xhr.download.DownloadTask;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by xhrong on 2014/6/28.
 */
public class DownloadTaskAdapter extends ArrayAdapter<DownloadTask> {
    static class ViewHolder {
        public TextView text;
        public Button startBtn;
        public Button pauseBtn;
        public Button resumeBtn;
        public ProgressBar pBar;
    }

    private Context context;
    private ArrayList<DownloadTask> ddList;

    public DownloadTaskAdapter(Context context, ArrayList<DownloadTask> values) {
        super(context, R.layout.downloadlib_item_layout, values);
        this.context = context;
        this.ddList = values;
    }


    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return ddList.size();
    }

    @Override
    public DownloadTask getItem(int arg0) {
        // TODO Auto-generated method stub
        return ddList.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        final DownloadTask ddt = ddList.get(position);
        View rowView = convertView;
        // reuse views
        if (rowView == null) {

            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.downloadlib_item_layout, parent, false);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.text = (TextView) rowView.findViewById(R.id.textView);
            viewHolder.startBtn = (Button) rowView.findViewById(R.id.btnStart);
            viewHolder.pauseBtn = (Button) rowView.findViewById(R.id.btnPause);
            viewHolder.resumeBtn = (Button) rowView.findViewById(R.id.btnResume);
            viewHolder.pBar = (ProgressBar) rowView.findViewById(R.id.progressBar);
            viewHolder.text.setTag(ddt);
            rowView.setTag(viewHolder);
        }

        final ViewHolder holder = (ViewHolder) rowView.getTag();

        holder.text.setText(ddt.getName());


        holder.pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DownloadTask ddtask = (DownloadTask) holder.text.getTag();
                DownloadManager.getInstance().pauseDownload(ddtask);
            }
        });
        holder.resumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DownloadTask ddtask = (DownloadTask) holder.text.getTag();
                DownloadManager.getInstance().resumeDownload(ddtask);
            }
        });

        holder.startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DownloadTask ddtask = (DownloadTask) holder.text.getTag();
                // if(ddtask.getStatus()==DownloadTask.STATUS_FINISHED){//如果结束了，则重新下载
                ddtask.setStatus(DownloadTask.STATUS_RESTART);
                //  }
                String customParam = "{\"fileType\":\"zip\"}";
                ddtask.setCustomParam(customParam);
                DownloadManager.getInstance().addDownloadTask(ddtask, new DownloadListener() {

                    @Override
                    public void onDownloadStart(DownloadTask task) {
                        holder.pBar.setProgress(0);
                        holder.text.setText(task.getName() + " Started");
                    }

                    @Override
                    public void onDownloadUpdated(DownloadTask task, long finishedSize, long trafficSpeed) {
                        holder.text.setText(task.getName() + finishedSize);
                        holder.pBar.setProgress((int) (finishedSize * 100 / task.getDownloadTotalSize()));
                    }

                    @Override
                    public void onDownloadPaused(DownloadTask task) {

                    }

                    @Override
                    public void onDownloadResumed(DownloadTask task) {

                    }

                    @Override
                    public void onDownloadSuccessed(DownloadTask task) {
                        holder.text.setText(task.getName() + "finish");
                        holder.pBar.setProgress(100);
                        JSONObject jsonObject;
                        try{
                            jsonObject=new JSONObject(task.getCustomParam());
                            if(jsonObject.getString("fileType").equals("zip")){
                                Log.i("FileTypt","可以解ZIP了");
                            }
                        }catch (JSONException e){
                            e.printStackTrace();
                        }

                    }

                    @Override
                    public void onDownloadCanceled(DownloadTask task) {

                    }

                    @Override
                    public void onDownloadFailed(DownloadTask task) {

                    }

                    @Override
                    public void onDownloadRetry(DownloadTask task) {

                    }
                });
            }
        });
        return rowView;
    }


}
