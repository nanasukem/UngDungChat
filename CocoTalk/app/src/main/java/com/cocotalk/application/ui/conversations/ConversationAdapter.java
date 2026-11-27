package com.cocotalk.application.ui.conversations;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocotalk.application.R;
import com.cocotalk.application.data.model.Conversation;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.databinding.ItemConversationBinding;
import com.cocotalk.application.utils.AvatarUtils;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.PhoneUtils;
import com.cocotalk.application.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Danh sách cuộc trò chuyện (màn hình 1 – theo mockup "01 – Danh sách chat"). */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(@NonNull Conversation conversation,
                                 @NonNull String title,
                                 @Nullable String otherUid,
                                 @Nullable String avatarUrl);
    }

    private final List<Conversation> items = new ArrayList<>();
    private final Map<String, User> users = new HashMap<>();
    private final String myUid;
    private final OnConversationClickListener listener;

    public ConversationAdapter(@Nullable String myUid, @NonNull OnConversationClickListener listener) {
        this.myUid = myUid;
        this.listener = listener;
    }

    public void submitList(@Nullable List<Conversation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setUsers(@Nullable Map<String, User> map) {
        users.clear();
        if (map != null) users.putAll(map);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding b;

        ViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Conversation c) {
            boolean isGroup = Constants.CONVERSATION_TYPE_GROUP.equals(c.getType());
            String otherUid = isGroup ? null : ConversationsViewModel.otherMember(c, myUid);
            User other = otherUid != null ? users.get(otherUid) : null;

            // Tên + avatar
            String title;
            String avatarUrl;
            if (isGroup) {
                title = c.getGroupName() != null && !c.getGroupName().isEmpty()
                        ? c.getGroupName() : itemView.getContext().getString(R.string.action_new_group);
                avatarUrl = c.getGroupAvatar();
            } else if (other != null) {
                title = other.getDisplayName() != null && !other.getDisplayName().isEmpty()
                        ? other.getDisplayName() : PhoneUtils.formatDisplayPhone(other.getPhone());
                avatarUrl = other.getAvatarUrl();
            } else {
                title = itemView.getContext().getString(R.string.unknown_user);
                avatarUrl = null;
            }
            b.tvName.setText(title);
            AvatarUtils.bind(b.tvAvatarInitial, b.imgAvatar, title, avatarUrl);

            // Tin cuối: thêm "Bạn: " nếu mình là người gửi
            String last = c.getLastMessage() != null ? c.getLastMessage() : "";
            if (myUid != null && myUid.equals(c.getLastSenderId())) {
                last = itemView.getContext().getString(R.string.prefix_you, last);
            }
            b.tvLastMessage.setText(last);
            b.tvTime.setText(TimeUtils.formatConversationTime(itemView.getContext(), c.getLastMessageTime()));

            // Số tin chưa đọc
            Integer unread = (c.getUnreadCount() != null && myUid != null) ? c.getUnreadCount().get(myUid) : null;
            boolean hasUnread = unread != null && unread > 0;
            b.tvUnread.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            if (hasUnread) b.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));

            b.tvName.setTypeface(null, hasUnread ? Typeface.BOLD : Typeface.NORMAL);
            b.tvLastMessage.setTypeface(null, hasUnread ? Typeface.BOLD : Typeface.NORMAL);
            b.tvLastMessage.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    hasUnread ? R.color.md_on_surface : R.color.md_on_surface_variant));
            b.tvTime.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    hasUnread ? R.color.md_primary : R.color.md_on_surface_variant));

            final String finalTitle = title;
            final String finalAvatar = avatarUrl;
            itemView.setOnClickListener(v -> listener.onConversationClick(c, finalTitle, otherUid, finalAvatar));
        }
    }
}
