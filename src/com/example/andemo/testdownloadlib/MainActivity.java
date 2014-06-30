package com.example.andemo.testdownloadlib;

import android.app.ListActivity;
import android.os.Bundle;
import com.example.andemo.R;
import com.xhr.download.DownloadListener;
import com.xhr.download.DownloadTask;

/**
 * Created by xhrong on 2014/6/28.
 */
public class MainActivity extends ListActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.downloadlib_main_layout);

        // create an array of Strings, that will be put to our ListActivity
        DownloadTaskAdapter adapter = new DownloadTaskAdapter(this,
                SourceProvicer.getTaskList());
        setListAdapter(adapter);
    }
}
