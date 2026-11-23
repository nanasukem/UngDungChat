package com.cocotalk.application.ui.contacts;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cocotalk.application.R;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.FriendRepo;
import com.cocotalk.application.databinding.ItemFriendRequestBinding;
import com.cocotalk.application.utils.PhoneUtils;

import java.util.ArrayList;
import java.util.List;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.ViewHolder> {

    public interface OnRequestActionListener {
        void onAccept(FriendRepo.FriendItem item);
        void onDeclineOrCancel(FriendRepo.FriendItem item, boolean isSentByMe);
    }

    private final List<FriendRepo.FriendItem> items = new ArrayList<>();
    private final List<Boolean> isSentByMeFlags = new ArrayList<>();
    private final OnRequestActionListener listener;

    private static final int[] AVATAR_BG_COLORS = {
            R.color.avatar_bg_1, R.color.avatar_bg_2, R.color.avatar_bg_3,
            R.color.avatar_bg_4, R.color.avatar_bg_5
    };
    private static final int[] AVATAR_FG_COLORS = {
            R.color.avatar_fg_1, R.color.avatar_fg_2, R.color.avatar_fg_3,
            R.color.avatar_fg_4, R.color.avatar_fg_5
    };

    public FriendRequestAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FriendRepo.FriendItem> incoming, List<FriendRepo.FriendItem> sent) {
        items.clear();
        isSentByMeFlags.clear();
        if (incoming != null) {
            for (FriendRepo.FriendItem item : incoming) {
                items.add(item);
                isSentByMeFlags.add(false);
            }
        }
        if (sent != null) {
            for (FriendRepo.FriendItem item : sent) {
                items.add(item);
                isSentByMeFlags.add(true);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFriendRequestBinding binding = ItemFriendRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), isSentByMeFlags.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendRequestBinding binding;

        ViewHolder(ItemFriendRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FriendRepo.FriendItem item, boolean isSentByMe) {
            User user = item.getUser();
            String displayName = (user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                    ? user.getDisplayName()
                    : PhoneUtils.formatDisplayPhone(user.getPhone());

            binding.tvName.setText(displayName);
            binding.tvPhone.setText(PhoneUtils.formatDisplayPhone(user.getPhone()));

            int colorIndex = Math.abs(displayName != null ? displayName.hashCode() : 0) % AVATAR_BG_COLORS.length;
            int bgColor = ContextCompat.getColor(itemView.getContext(), AVATAR_BG_COLORS[colorIndex]);
            int fgColor = ContextCompat.getColor(itemView.getContext(), AVATAR_FG_COLORS[colorIndex]);
            binding.tvAvatarInitial.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            binding.tvAvatarInitial.setTextColor(fgColor);

            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                binding.imgAvatar.setVisibility(View.VISIBLE);
                binding.tvAvatarInitial.setVisibility(View.GONE);
                Glide.with(itemView.getContext())
                        .load(user.getAvatarUrl())
                        .into(binding.imgAvatar);
            } else {
                binding.imgAvatar.setVisibility(View.GONE);
                binding.tvAvatarInitial.setVisibility(View.VISIBLE);
                binding.tvAvatarInitial.setText(extractInitial(displayName));
            }

            if (isSentByMe) {
                // Người gửi lời mời: Ẩn nút "Đồng ý", nút bên phải hiển thị "Thu hồi" (Hủy lời mời)
                binding.btnAccept.setVisibility(View.GONE);
                binding.btnDecline.setVisibility(View.VISIBLE);
                binding.btnDecline.setText(R.string.add_friend_btn_cancel_request);
            } else {
                // Người nhận lời mời: Hiển thị cả "Đồng ý" và "Từ chối"
                binding.btnAccept.setVisibility(View.VISIBLE);
                binding.btnDecline.setVisibility(View.VISIBLE);
                binding.btnAccept.setText(R.string.btn_accept);
                binding.btnDecline.setText(R.string.btn_decline);
            }

            binding.btnAccept.setOnClickListener(v -> {
                if (listener != null && !isSentByMe) {
                    listener.onAccept(item);
                }
            });

            binding.btnDecline.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeclineOrCancel(item, isSentByMe);
                }
            });
        }

        private String extractInitial(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "?";
            }
            String[] parts = name.trim().split("\\s+");
            String last = parts[parts.length - 1];
            return String.valueOf(Character.toUpperCase(last.charAt(0)));
        }
    }
}
