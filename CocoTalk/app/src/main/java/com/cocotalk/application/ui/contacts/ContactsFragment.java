package com.cocotalk.application.ui.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cocotalk.application.R;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.FriendRepo;
import com.cocotalk.application.databinding.FragmentContactsBinding;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class ContactsFragment extends Fragment {
    private FragmentContactsBinding binding;
    private AuthRepo authRepo;
    private FriendRepo friendRepo;
    private FriendRequestAdapter requestAdapter;
    private FriendAdapter friendAdapter;
    private ListenerRegistration friendsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentContactsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepo = new AuthRepo();
        friendRepo = new FriendRepo();

        setupRecyclerViews();

        // Nút Thêm bạn bằng số điện thoại
        binding.cardAddFriend.setOnClickListener(v -> openAddFriendScreen());

        // Bấm nút + thêm người trên thanh toolbar của trang danh bạ
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_friend) {
                openAddFriendScreen();
                return true;
            }
            return false;
        });

        startListeningFriends();
    }

    private void setupRecyclerViews() {
        requestAdapter = new FriendRequestAdapter(new FriendRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(FriendRepo.FriendItem item) {
                String currentUid = authRepo.getCurrentUid();
                if (currentUid == null) return;
                friendRepo.acceptFriendRequest(currentUid, item.getRequest().getFriendUid(), new FriendRepo.CompleteCallback() {
                    @Override
                    public void onSuccess() {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.msg_friend_request_accepted, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (getContext() != null && message != null) {
                            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onDeclineOrCancel(FriendRepo.FriendItem item, boolean isSentByMe) {
                String currentUid = authRepo.getCurrentUid();
                if (currentUid == null) return;
                friendRepo.declineOrCancelFriendRequest(currentUid, item.getRequest().getFriendUid(), new FriendRepo.CompleteCallback() {
                    @Override
                    public void onSuccess() {
                        if (getContext() != null) {
                            int msgRes = isSentByMe
                                    ? R.string.msg_friend_request_canceled
                                    : R.string.msg_friend_request_declined;
                            Toast.makeText(getContext(), msgRes, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (getContext() != null && message != null) {
                            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        friendAdapter = new FriendAdapter();

        binding.rvFriendRequests.setAdapter(requestAdapter);
        binding.rvFriends.setAdapter(friendAdapter);
    }

    private void startListeningFriends() {
        String currentUid = authRepo.getCurrentUid();
        if (currentUid == null) return;

        friendsListener = friendRepo.listenFriendsAndRequests(currentUid, new FriendRepo.FriendsListCallback() {
            @Override
            public void onResult(List<FriendRepo.FriendItem> incomingRequests,
                                 List<FriendRepo.FriendItem> sentRequests,
                                 List<FriendRepo.FriendItem> acceptedFriends) {
                if (binding == null) return;

                int totalRequests = incomingRequests.size() + sentRequests.size();
                binding.tvRequestsTitle.setVisibility(totalRequests > 0 ? View.VISIBLE : View.GONE);
                binding.rvFriendRequests.setVisibility(totalRequests > 0 ? View.VISIBLE : View.GONE);
                if (totalRequests > 0 && getContext() != null) {
                    binding.tvRequestsTitle.setText(getString(R.string.section_friend_requests) + " (" + totalRequests + ")");
                }
                requestAdapter.setRequests(incomingRequests, sentRequests);

                binding.tvFriendsTitle.setVisibility(acceptedFriends.isEmpty() ? View.GONE : View.VISIBLE);
                binding.rvFriends.setVisibility(acceptedFriends.isEmpty() ? View.GONE : View.VISIBLE);
                if (!acceptedFriends.isEmpty() && getContext() != null) {
                    binding.tvFriendsTitle.setText(getString(R.string.section_friends) + " (" + acceptedFriends.size() + ")");
                }
                friendAdapter.setFriends(acceptedFriends);

                boolean isAllEmpty = (totalRequests == 0 && acceptedFriends.isEmpty());
                binding.tvPlaceholder.setVisibility(isAllEmpty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                // Không làm gián đoạn giao diện nếu lỗi mạng tạm thời
            }
        });
    }

    private void openAddFriendScreen() {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), AddFriendActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (friendsListener != null) {
            friendsListener.remove();
            friendsListener = null;
        }
        binding = null;
    }
}
