package com.cocotalk.application.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocotalk.application.R;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.ChatRepo;
import com.cocotalk.application.databinding.ActivityChatBinding;
import com.cocotalk.application.utils.AvatarUtils;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.LocaleHelper;
import com.cocotalk.application.utils.SampleData;
import com.cocotalk.application.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Màn hình Chat (1-1 và nhóm) – theo mockup Figma "02 – Chat". */
public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_CONVERSATION_TYPE = "extra_conversation_type";
    public static final String EXTRA_MEMBERS = "extra_members";
    public static final String EXTRA_OTHER_UID = "extra_other_uid";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_AVATAR_URL = "extra_avatar_url";

    private ActivityChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private LinearLayoutManager layoutManager;
    private String title;

    /** Mở chat 1-1 với một người (VD từ màn hình Danh bạ). Cuộc trò chuyện được tạo khi gửi tin đầu tiên. */
    public static void startDirect(@NonNull Context context, @NonNull String myUid, @NonNull String otherUid,
                                   @Nullable String title, @Nullable String avatarUrl) {
        start(context, ChatRepo.directConversationId(myUid, otherUid), Constants.CONVERSATION_TYPE_DIRECT,
                Arrays.asList(myUid, otherUid), otherUid, title, avatarUrl);
    }

    /** Mở một cuộc trò chuyện có sẵn (VD từ danh sách chat). */
    public static void start(@NonNull Context context, @NonNull String conversationId, @Nullable String type,
                             @Nullable List<String> members, @Nullable String otherUid,
                             @Nullable String title, @Nullable String avatarUrl) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_CONVERSATION_TYPE, type != null ? type : Constants.CONVERSATION_TYPE_DIRECT);
        if (members != null) intent.putStringArrayListExtra(EXTRA_MEMBERS, new ArrayList<>(members));
        intent.putExtra(EXTRA_OTHER_UID, otherUid);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_AVATAR_URL, avatarUrl);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String myUid = SampleData.ENABLED ? SampleData.MY_UID : new AuthRepo().getCurrentUid();
        String conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        if (myUid == null || conversationId == null) {
            Toast.makeText(this, R.string.msg_not_logged_in, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String type = getIntent().getStringExtra(EXTRA_CONVERSATION_TYPE);
        if (type == null) type = Constants.CONVERSATION_TYPE_DIRECT;
        List<String> members = getIntent().getStringArrayListExtra(EXTRA_MEMBERS);
        String otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        String avatarUrl = getIntent().getStringExtra(EXTRA_AVATAR_URL);

        setupHeader(title, avatarUrl);
        setupMessageList(myUid);
        setupInputBar();

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(conversationId, type, myUid, otherUid, members);

        viewModel.getMessages().observe(this, list -> {
            int oldCount = adapter.getItemCount();
            boolean wasAtBottom = oldCount == 0
                    || layoutManager.findLastVisibleItemPosition() >= oldCount - 2;
            adapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) {
                boolean lastIsMine = myUid.equals(list.get(list.size() - 1).getSenderId());
                if (wasAtBottom || lastIsMine) binding.rvMessages.scrollToPosition(list.size() - 1);
            }
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.markAsRead();
            }
        });
        viewModel.getOtherUser().observe(this, this::showOtherUser);
        viewModel.getError().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, getString(R.string.msg_send_failed, msg), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.markAsRead();
    }

    // ===================== THANH TIÊU ĐỀ =====================
    private void setupHeader(@Nullable String name, @Nullable String avatarUrl) {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvTitle.setText(name != null ? name : getString(R.string.unknown_user));
        AvatarUtils.bind(binding.tvAvatarInitial, binding.imgAvatar, name, avatarUrl);

        // Chỗ dành cho nút gọi ZegoCloud (Người 3 – tuần 4).
        // Người 3 có thể thay 2 ImageButton này bằng ZegoSendCallInvitationButton
        // đặt trong callVoiceContainer / callVideoContainer của activity_chat.xml.
        binding.btnVoiceCall.setOnClickListener(v -> onCallClicked(Constants.CALL_TYPE_VOICE));
        binding.btnVideoCall.setOnClickListener(v -> onCallClicked(Constants.CALL_TYPE_VIDEO));
    }

    private void onCallClicked(@NonNull String callType) {
        Toast.makeText(this, R.string.msg_call_coming_soon, Toast.LENGTH_SHORT).show();
    }

    private void showOtherUser(@Nullable User user) {
        adapter.setOtherUser(user);
        if (user == null) {
            binding.tvStatus.setVisibility(View.GONE);
            binding.viewOnline.setVisibility(View.GONE);
            return;
        }
        if ((title == null || title.isEmpty()) && user.getDisplayName() != null) {
            title = user.getDisplayName();
            binding.tvTitle.setText(title);
        }
        AvatarUtils.bind(binding.tvAvatarInitial, binding.imgAvatar, title, user.getAvatarUrl());

        binding.tvStatus.setVisibility(View.VISIBLE);
        binding.viewOnline.setVisibility(user.isOnline() ? View.VISIBLE : View.GONE);
        if (user.isOnline()) {
            binding.tvStatus.setText(R.string.status_online);
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_online_text));
        } else if (user.getLastSeen() > 0) {
            binding.tvStatus.setText(getString(R.string.status_last_seen,
                    TimeUtils.formatConversationTime(this, user.getLastSeen())));
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.md_on_surface_variant));
        } else {
            binding.tvStatus.setVisibility(View.GONE);
        }
    }

    // ===================== DANH SÁCH TIN NHẮN =====================
    private void setupMessageList(@NonNull String myUid) {
        adapter = new MessageAdapter(myUid);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // tin mới nằm sát ô nhập
        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(adapter);
    }

    // ===================== Ô NHẬP TIN =====================
    private void setupInputBar() {
        updateSendButton(false);
        binding.edtMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                updateSendButton(s.toString().trim().length() > 0);
            }
        });

        binding.btnSend.setOnClickListener(v -> {
            String text = binding.edtMessage.getText() != null
                    ? binding.edtMessage.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            viewModel.sendText(text);
            binding.edtMessage.setText("");
        });

        // Đính kèm file / gửi ảnh – phần của Người 3 (Media)
        binding.btnAttach.setOnClickListener(v ->
                Toast.makeText(this, R.string.msg_coming_soon, Toast.LENGTH_SHORT).show());
        binding.btnImage.setOnClickListener(v ->
                Toast.makeText(this, R.string.msg_coming_soon, Toast.LENGTH_SHORT).show());
    }

    private void updateSendButton(boolean enabled) {
        binding.btnSend.setEnabled(enabled);
        binding.btnSend.setAlpha(enabled ? 1f : 0.4f);
    }

    /** Người 3 gọi hàm này sau khi upload xong để gửi tin media. */
    public ChatViewModel getChatViewModel() {
        return viewModel;
    }
}
