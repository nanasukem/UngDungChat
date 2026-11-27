package com.cocotalk.application.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocotalk.application.data.model.Message;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.ChatRepo;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.SampleData;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Giữ tin nhắn và thông tin người kia cho màn hình Chat.
 * Listener Firestore nằm trong ViewModel (không nằm trong Activity) nên khi xoay màn hình
 * danh sách tin nhắn được giữ nguyên, không tải lại, không bị nhân đôi.
 */
public class ChatViewModel extends ViewModel {
    private final ChatRepo chatRepo = new ChatRepo();

    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>();
    private final MutableLiveData<User> otherUser = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration messagesRegistration;
    private ListenerRegistration userRegistration;

    private String conversationId;
    private String conversationType;
    private String myUid;
    private List<String> members = new ArrayList<>();
    private boolean initialized;

    public LiveData<List<Message>> getMessages() { return messages; }
    public LiveData<User> getOtherUser() { return otherUser; }
    public LiveData<String> getError() { return error; }
    public String getMyUid() { return myUid; }

    public void init(@NonNull String conversationId,
                     @NonNull String conversationType,
                     @NonNull String myUid,
                     @Nullable String otherUid,
                     @Nullable List<String> members) {
        if (initialized) return; // đã khởi tạo (VD sau khi xoay màn hình) – giữ nguyên dữ liệu
        initialized = true;
        this.conversationId = conversationId;
        this.conversationType = conversationType;
        this.myUid = myUid;
        if (members != null && !members.isEmpty()) {
            this.members = new ArrayList<>(members);
        } else {
            this.members = new ArrayList<>();
            this.members.add(myUid);
            if (otherUid != null) this.members.add(otherUid);
        }

        if (SampleData.ENABLED) {
            messages.setValue(SampleData.messages(conversationId, myUid, otherUid));
            if (otherUid != null) otherUser.setValue(SampleData.user(otherUid));
            return;
        }

        messagesRegistration = chatRepo.listenMessages(conversationId, new ChatRepo.MessagesCallback() {
            @Override
            public void onResult(@NonNull List<Message> list) {
                messages.setValue(list);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });

        if (otherUid != null) {
            userRegistration = chatRepo.listenUser(otherUid, otherUser::setValue);
        }
    }

    /** Gửi tin nhắn chữ. */
    public void sendText(@NonNull String text) {
        Message m = new Message();
        m.setType(Constants.MSG_TYPE_TEXT);
        m.setText(text);
        sendMessage(m, text);
    }

    /**
     * Gửi một tin bất kỳ (Người 3 dùng hàm này cho tin ảnh / video / file / cuộc gọi):
     *   Message m = new Message(); m.setType(Constants.MSG_TYPE_IMAGE); m.getAttachments().add(att);
     *   viewModel.sendMessage(m, ChatRepo.previewFor(context, m));
     */
    public void sendMessage(@NonNull Message message, @NonNull String preview) {
        message.setSenderId(myUid);
        if (SampleData.ENABLED) {
            message.setCreatedAt(System.currentTimeMillis());
            message.getSeenBy().add(myUid);
            List<Message> current = messages.getValue() != null
                    ? new ArrayList<>(messages.getValue()) : new ArrayList<>();
            current.add(message);
            messages.setValue(current);
            return;
        }
        chatRepo.sendMessage(conversationId, conversationType, members, myUid, message, preview,
                new ChatRepo.CompleteCallback() {
                    @Override
                    public void onSuccess() { }

                    @Override
                    public void onError(String msg) {
                        error.setValue(msg);
                    }
                });
    }

    public void markAsRead() {
        if (!SampleData.ENABLED && conversationId != null && myUid != null) {
            chatRepo.markAsRead(conversationId, myUid);
        }
    }

    @Override
    protected void onCleared() {
        if (messagesRegistration != null) messagesRegistration.remove();
        if (userRegistration != null) userRegistration.remove();
    }
}
