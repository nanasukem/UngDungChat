package com.cocotalk.application;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocotalk.application.data.repository.UserRepo;
import com.cocotalk.application.utils.LocaleHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CocoTalkApp extends Application {

    /** Số màn hình đang hiển thị. > 0 nghĩa là app đang mở trên màn hình. */
    private int startedActivities = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        registerActivityLifecycleCallbacks(new PresenceTracker());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    /**
     * Cập nhật trạng thái online (Người 2 – hiển thị "Đang hoạt động" / "Hoạt động lúc..."):
     *  - Mở app (màn hình đầu tiên hiện lên)  → isOnline = true
     *  - Thoát ra ngoài / tắt màn hình        → isOnline = false, lastSeen = giờ hiện tại
     * Xoay màn hình không bị tính là thoát app.
     */
    private class PresenceTracker implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (startedActivities++ == 0) setOnline(true);
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            startedActivities = Math.max(0, startedActivities - 1);
            if (startedActivities == 0 && !activity.isChangingConfigurations()) {
                setOnline(false);
            }
        }

        @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
        @Override public void onActivityResumed(@NonNull Activity a) { }
        @Override public void onActivityPaused(@NonNull Activity a) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
        @Override public void onActivityDestroyed(@NonNull Activity a) { }
    }

    private void setOnline(boolean online) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            new UserRepo().updateOnlineStatus(user.getUid(), online);
        }
    }
}
