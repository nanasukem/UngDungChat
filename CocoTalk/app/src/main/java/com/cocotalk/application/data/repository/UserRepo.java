package com.cocotalk.application.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocotalk.application.data.model.User;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.PhoneUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class UserRepo {
    private final FirebaseFirestore db;

    public interface UserCallback {
        void onResult(@Nullable User user);
        void onError(String message);
    }

    public interface CompleteCallback {
        void onSuccess();
        void onError(String message);
    }

    public UserRepo() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getUserByUid(@NonNull String uid, @NonNull UserCallback callback) {
        db.collection(Constants.KEY_COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onResult(parseUserSnapshot(snapshot));
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Kiểm tra hồ sơ của tài khoản đã đăng ký khi đăng nhập lại:
     */
    public void getOrMigrateUserForAuth(@NonNull String uid,
                                        @Nullable String rawPhone,
                                        @NonNull UserCallback callback) {
        getUserByUid(uid, new UserCallback() {
            @Override
            public void onResult(@Nullable User user) {
                if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    callback.onResult(user);
                    return;
                }
                if (rawPhone == null || rawPhone.trim().isEmpty()) {
                    callback.onResult(user);
                    return;
                }
                findUserByPhone(rawPhone, new UserCallback() {
                    @Override
                    public void onResult(@Nullable User byPhoneUser) {
                        if (byPhoneUser != null
                                && byPhoneUser.getDisplayName() != null
                                && !byPhoneUser.getDisplayName().trim().isEmpty()) {
                            byPhoneUser.setUid(uid);
                            saveUserProfile(byPhoneUser, new CompleteCallback() {
                                @Override
                                public void onSuccess() {
                                    callback.onResult(byPhoneUser);
                                }

                                @Override
                                public void onError(String message) {
                                    callback.onResult(byPhoneUser);
                                }
                            });
                        } else {
                            callback.onResult(null);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onResult(user);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private User parseUserSnapshot(@NonNull DocumentSnapshot snapshot) {
        User user = null;
        try {
            user = snapshot.toObject(User.class);
        } catch (Exception ignored) {
        }
        if (user == null) {
            user = new User();
        }
        user.setUid(snapshot.getId());
        String displayName = snapshot.getString("displayName");
        if (displayName != null) {
            user.setDisplayName(displayName);
        }
        String phone = snapshot.getString("phone");
        if (phone != null) {
            user.setPhone(phone);
        }
        String avatarUrl = snapshot.getString("avatarUrl");
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        String bio = snapshot.getString("bio");
        if (bio != null) {
            user.setBio(bio);
        }
        Boolean isOnline = snapshot.getBoolean("isOnline");
        if (isOnline != null) {
            user.setOnline(isOnline);
        }
        String language = snapshot.getString("language");
        if (language != null) {
            user.setLanguage(language);
        }
        return user;
    }

    public void saveUserProfile(@NonNull User user, @NonNull CompleteCallback callback) {
        String normalizedPhone = PhoneUtils.normalizePhone(user.getPhone());
        user.setPhone(normalizedPhone);
        if (user.getDisplayName() != null) {
            user.setNameLower(user.getDisplayName().trim().toLowerCase());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("phone", normalizedPhone);
        data.put("displayName", user.getDisplayName());
        data.put("nameLower", user.getNameLower());
        data.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        data.put("bio", user.getBio() != null ? user.getBio() : "");
        data.put("isOnline", true);
        data.put("lastSeen", System.currentTimeMillis());
        data.put("language", user.getLanguage() != null ? user.getLanguage() : "vi");

        db.collection(Constants.KEY_COLLECTION_USERS)
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    refreshFcmToken(user.getUid());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void findUserByPhone(@NonNull String rawPhone, @NonNull UserCallback callback) {
        String normalizedPhone = PhoneUtils.normalizePhone(rawPhone);
        if (normalizedPhone.isEmpty()) {
            callback.onResult(null);
            return;
        }

        db.collection(Constants.KEY_COLLECTION_USERS)
                .whereEqualTo("phone", normalizedPhone)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        callback.onResult(parseUserSnapshot(doc));
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void refreshFcmToken(@NonNull String uid) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token != null && !token.isEmpty()) {
                        db.collection(Constants.KEY_COLLECTION_USERS)
                                .document(uid)
                                .update("fcmToken", token);
                    }
                });
    }

    public void updateOnlineStatus(@NonNull String uid, boolean isOnline) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);
        updates.put("lastSeen", System.currentTimeMillis());
        db.collection(Constants.KEY_COLLECTION_USERS)
                .document(uid)
                .update(updates);
    }
}
