package com.cocotalk.application;

import android.app.Application;
import android.content.Context;

import com.cocotalk.application.utils.LocaleHelper;
import com.google.firebase.FirebaseApp;

public class CocoTalkApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }
}