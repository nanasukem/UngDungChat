package com.cocotalk.application.ui.conversations;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cocotalk.application.R;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.databinding.FragmentConversationsBinding;
import com.cocotalk.application.ui.chat.ChatActivity;
import com.cocotalk.application.utils.SampleData;

/** Tab "Tin nhắn": danh sách cuộc trò chuyện realtime từ Firestore. */
public class ConversationsFragment extends Fragment {
    private static final String TAG = "ConversationsFragment";

    private FragmentConversationsBinding binding;
    private ConversationAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentConversationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String myUid = SampleData.ENABLED ? SampleData.MY_UID : new AuthRepo().getCurrentUid();

        adapter = new ConversationAdapter(myUid, (conversation, title, otherUid, avatarUrl) ->
                ChatActivity.start(requireContext(), conversation.getId(), conversation.getType(),
                        conversation.getMembers(), otherUid, title, avatarUrl));
        binding.rvConversations.setAdapter(adapter);

        // Nút trên thanh tiêu đề – làm ở các tuần sau
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_new_group || item.getItemId() == R.id.action_search) {
                Toast.makeText(requireContext(), R.string.msg_coming_soon, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        binding.searchBar.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.msg_coming_soon, Toast.LENGTH_SHORT).show());

        if (myUid == null) {
            binding.tvPlaceholder.setVisibility(View.VISIBLE);
            return;
        }

        ConversationsViewModel viewModel = new ViewModelProvider(this).get(ConversationsViewModel.class);
        viewModel.getConversations().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            binding.tvPlaceholder.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
        viewModel.getUsers().observe(getViewLifecycleOwner(), adapter::setUsers);
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            // Nếu thấy lỗi "requires an index" trong Logcat: bấm vào link trong lỗi để tạo index
            if (message != null) Log.e(TAG, "Lỗi tải danh sách chat: " + message);
        });
        viewModel.start(myUid);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
