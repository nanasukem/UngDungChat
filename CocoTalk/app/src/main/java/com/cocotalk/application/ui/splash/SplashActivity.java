package com.cocotalk.application.ui.splash;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.UserRepo;
import com.cocotalk.application.databinding.ActivitySplashBinding;
import com.cocotalk.application.ui.auth.CreateProfileActivity;
import com.cocotalk.application.ui.auth.LoginActivity;
import com.cocotalk.application.ui.main.MainActivity;
import com.cocotalk.application.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 700L;
    private AuthRepo authRepo;
    private UserRepo userRepo;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepo = new AuthRepo();
        userRepo = new UserRepo();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndRoute, SPLASH_DELAY_MS);
    }

    private void checkAuthAndRoute() {
        FirebaseUser currentUser = authRepo.getCurrentUser();
        if (currentUser == null) {
            navigateTo(LoginActivity.class);
            return;
        }

        String uid = currentUser.getUid();
        userRepo.getOrMigrateUserForAuth(uid, currentUser.getPhoneNumber(), new UserRepo.UserCallback() {
            @Override
            public void onResult(User user) {
                if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    userRepo.updateOnlineStatus(uid, true);
                    userRepo.refreshFcmToken(uid);
                    navigateTo(MainActivity.class);
                } else {
                    navigateTo(CreateProfileActivity.class);
                }
            }

            @Override
            public void onError(String message) {
                navigateTo(MainActivity.class);
            }
        });
    }

    private void navigateTo(Class<?> targetActivity) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(this, targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
