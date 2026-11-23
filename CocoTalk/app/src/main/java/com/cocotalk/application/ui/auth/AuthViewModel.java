package com.cocotalk.application.ui.auth;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.UserRepo;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class AuthViewModel extends ViewModel {
    private final AuthRepo authRepo = new AuthRepo();
    private final UserRepo userRepo = new UserRepo();

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> verificationIdLive = new MutableLiveData<>();
    private final MutableLiveData<FirebaseUser> authenticatedUser = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasExistingProfile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> profileSaved = new MutableLiveData<>(false);

    private static PhoneAuthProvider.ForceResendingToken sharedResendToken;

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getVerificationIdLive() { return verificationIdLive; }
    public LiveData<FirebaseUser> getAuthenticatedUser() { return authenticatedUser; }
    public LiveData<Boolean> getHasExistingProfile() { return hasExistingProfile; }
    public LiveData<Boolean> getProfileSaved() { return profileSaved; }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void clearVerificationId() {
        verificationIdLive.setValue(null);
    }

    public void sendOtp(@NonNull String normalizedPhone, @NonNull Activity activity, boolean isResend) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        isLoading.postValue(false);
                        errorMessage.postValue(e.getMessage());
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        isLoading.postValue(false);
                        sharedResendToken = token;
                        verificationIdLive.postValue(verificationId);
                    }
                };

        // Không truyền ForceResendingToken cho số thử nghiệm hoặc khi chưa có token hợp lệ để tránh bị Firebase bỏ qua
        authRepo.sendOtp(normalizedPhone, activity, callbacks, (isResend && sharedResendToken != null) ? sharedResendToken : null);
    }

    public void verifyOtpCode(@NonNull String verificationId, @NonNull String otpCode) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otpCode);
        signInWithCredential(credential);
    }

    public void signInWithCredential(@NonNull PhoneAuthCredential credential) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        authRepo.signInWithCredential(credential, new AuthRepo.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                authenticatedUser.postValue(user);
                checkUserProfile(user.getUid(), user.getPhoneNumber());
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

    public void checkUserProfile(@NonNull String uid, String phoneNumber) {
        isLoading.postValue(true);
        userRepo.getOrMigrateUserForAuth(uid, phoneNumber, new UserRepo.UserCallback() {
            @Override
            public void onResult(User user) {
                isLoading.postValue(false);
                boolean exists = user != null
                        && user.getDisplayName() != null
                        && !user.getDisplayName().trim().isEmpty();
                if (exists) {
                    userRepo.updateOnlineStatus(uid, true);
                    userRepo.refreshFcmToken(uid);
                }
                hasExistingProfile.postValue(exists);
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

    public void createOrUpdateProfile(@NonNull String displayName, @NonNull String language) {
        FirebaseUser currentUser = authRepo.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("User not authenticated");
            return;
        }

        isLoading.setValue(true);
        String phone = currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "";
        User user = new User(currentUser.getUid(), phone, displayName.trim(), "");
        user.setLanguage(language);

        userRepo.saveUserProfile(user, new UserRepo.CompleteCallback() {
            @Override
            public void onSuccess() {
                isLoading.postValue(false);
                profileSaved.postValue(true);
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }
}
