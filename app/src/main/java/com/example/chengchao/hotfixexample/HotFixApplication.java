package com.example.chengchao.hotfixexample;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.hotfixlib.HotFix;

/**
 * Created by chengchao on 2016/11/1.
 */

public class HotFixApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        try {
            //Check patch
            if(HotFix.hasPatch(base)) {
                Log.d("HotFixApplication", "Has patch file, patch it!");
                HotFix.patch(base);
            }else{
                Log.d("HotFixApplication", "No patch file");
            }
        }catch (Exception ex){
            Log.w("HotFixApplication", "Failed to patch: " + ex.getMessage());
        }
    }
}
