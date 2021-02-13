package com.rzm.myhotfix;

import android.app.Application;
import android.util.Log;

import com.rzm.hotfix.MyHotFix;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            MyHotFix.fixApp(this);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MyApplication",e.toString());
        }
    }
}
