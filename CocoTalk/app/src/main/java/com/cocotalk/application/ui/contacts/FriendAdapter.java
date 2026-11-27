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
import com.cocotalk.application.databinding.ItemFriendBinding;
import com.cocotalk.application.utils.PhoneUtils;

import java.util.ArrayList;
import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.ViewHolder> {

    private final List<FriendRepo.FriendItem> friends = new ArrayList<>();

    /** Bấm vào một người bạn: mở Chat (Người 2). Nút gọi để Người 3 nối với Zego. */
    public interface OnFriendClickListener {
        void onChatClick(User user, String displayName);

        default void onCallClick(User user, String displayName) { }
    }

    private OnFriendClickListener listener;

    public void setOnFriendClickListener(OnFriendClickListener listener) {
        this.listener = listener;
    }

    private static final int[] AVATAR_BG_COLORS = {
            R.color.avatar_bg_1, R.color.avatar_bg_2, R.color.avatar_bg_3,
            R.color.avatar_bg_4, R.color.avatar_bg_5
    };
    private static final int[] AVATAR_FG_COLORS = {
            R.color.avatar_fg_1, R.color.avatar_fg_2, R.color.avatar_fg_3,
            R.color.avatar_fg_4, R.color.avatar_fg_5
    };

    public void setFriends(List<FriendRepo.FriendItem> list) {
        friends.clear();
        if (list != null) {
            friends.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFriendBinding binding = ItemFriendBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(friends.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFriendBinding binding;

        ViewHolder(ItemFriendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FriendRepo.FriendItem item, OnFriendClickListener listener) {
            User user = item.getUser();
            String nickname = item.getRequest().getNickname();
            String displayName = (nickname != null && !nickname.trim().isEmpty())
                    ? nickname.trim()
                    : ((user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                    ? user.getDisplayName()
                    : PhoneUtils.formatDisplayPhone(user.getPhone()));

            binding.tvName.setText(displayName);

            final String nameForClick = displayName;
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onChatClick(user, nameForClick);
            });
            binding.btnChat.setOnClickListener(v -> {
                if (listener != null) listener.onChatClick(user, nameForClick);
            });
            binding.btnCall.setOnClickListener(v -> {
                if (listener != null) listener.onCallClick(user, nameForClick);
            });

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

            boolean online = user.isOnline();
            binding.viewOnline.setVisibility(online ? View.VISIBLE : View.GONE);
            if (online) {
                binding.tvStatus.setText(R.string.status_online);
                binding.tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.status_online_text));
            } else {
                binding.tvStatus.setText(PhoneUtils.formatDisplayPhone(user.getPhone()));
                binding.tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.md_on_surface_variant));
            }
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
