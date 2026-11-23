package com.cocotalk.application.data.repository;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class AuthRepo {
    private final FirebaseAuth firebaseAuth;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public AuthRepo() {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public String getCurrentUid() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public void sendOtp(@NonNull String normalizedPhone,
                        @NonNull Activity activity,
                        @NonNull PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks,
                        PhoneAuthProvider.ForceResendingToken resendToken) {
        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(normalizedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks);

        if (resendToken != null) {
            builder.setForceResendingToken(resendToken);
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    public void signInWithCredential(@NonNull PhoneAuthCredential credential,
                                     @NonNull AuthCallback callback) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                        callback.onSuccess(task.getResult().getUser());
                    } else {
                        String error = task.getException() != null
                                ? task.getException().getMessage()
                                : "Verification failed";
                        callback.onError(error);
                    }
                });
    }

    public void signOut() {
        firebaseAuth.signOut();
    }
}
