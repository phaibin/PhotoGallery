package com.bignerdranch.android.photogallery;

import android.app.Application;

import org.xutils.x;

/**
 * Created by leon on 5/25/16.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        x.Ext.init(this);
        x.Ext.setDebug(BuildConfig.DEBUG);
    }
}
