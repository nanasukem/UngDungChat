package com.cocotalk.application.ui.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocotalk.application.data.model.Conversation;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.ChatRepo;
import com.cocotalk.application.data.repository.UserRepo;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.SampleData;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Giữ danh sách cuộc trò chuyện và thông tin người dùng liên quan.
 * Dữ liệu nằm trong ViewModel nên khi xoay màn hình không phải tải lại.
 */
public class ConversationsViewModel extends ViewModel {
    private final ChatRepo chatRepo = new ChatRepo();
    private final UserRepo userRepo = new UserRepo();

    private final MutableLiveData<List<Conversation>> conversations = new MutableLiveData<>();
    private final MutableLiveData<Map<String, User>> users = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final Map<String, User> userCache = new HashMap<>();
    private final Set<String> loadingUids = new HashSet<>();
    private ListenerRegistration registration;
    private String myUid;

    public LiveData<List<Conversation>> getConversations() { return conversations; }
    public LiveData<Map<String, User>> getUsers() { return users; }
    public LiveData<String> getError() { return error; }

    public void start(@NonNull String uid) {
        if (registration != null || uid.equals(myUid) && conversations.getValue() != null) return;
        myUid = uid;

        if (SampleData.ENABLED) {
            userCache.putAll(SampleData.users());
            users.setValue(new HashMap<>(userCache));
            conversations.setValue(SampleData.conversations(uid));
            return;
        }

        registration = chatRepo.listenConversations(uid, new ChatRepo.ConversationsCallback() {
            @Override
            public void onResult(@NonNull List<Conversation> list) {
                conversations.setValue(list);
                loadMissingUsers(list);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    /** Lấy tên / avatar của người kia trong các chat 1-1 (chỉ tải những người chưa có). */
    private void loadMissingUsers(@NonNull List<Conversation> list) {
        for (Conversation c : list) {
            if (!Constants.CONVERSATION_TYPE_DIRECT.equals(c.getType())) continue;
            String other = otherMember(c, myUid);
            if (other == null || userCache.containsKey(other) || loadingUids.contains(other)) continue;
            loadingUids.add(other);
            userRepo.getUserByUid(other, new UserRepo.UserCallback() {
                @Override
                public void onResult(@Nullable User user) {
                    loadingUids.remove(other);
                    if (user != null) {
                        userCache.put(other, user);
                        users.setValue(new HashMap<>(userCache));
                    }
                }

                @Override
                public void onError(String message) {
                    loadingUids.remove(other);
                }
            });
        }
    }

    @Nullable
    public static String otherMember(@NonNull Conversation c, @Nullable String myUid) {
        if (c.getMembers() == null) return null;
        for (String uid : c.getMembers()) {
            if (uid != null && !uid.equals(myUid)) return uid;
        }
        return null;
    }

    @Override
    protected void onCleared() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
