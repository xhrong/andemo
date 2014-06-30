package com.xhr.download;

import android.os.Environment;

import java.io.File;

/**
 * Created by xhrong on 2014/6/28.
 */
public class Env {

    public static  String ROOT_DIR = Environment.getExternalStorageDirectory().getPath() + File.separator + "xhrong";

}
