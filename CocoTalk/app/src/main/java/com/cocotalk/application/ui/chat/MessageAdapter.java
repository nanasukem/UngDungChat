package com.cocotalk.application.ui.chat;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cocotalk.application.R;
import com.cocotalk.application.data.model.Message;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.ChatRepo;
import com.cocotalk.application.databinding.ItemMessageTextReceivedBinding;
import com.cocotalk.application.databinding.ItemMessageTextSentBinding;
import com.cocotalk.application.utils.AvatarUtils;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter tin nhắn trong màn hình Chat.
 *
 * QUY ƯỚC VỚI NGƯỜI 3 (Media & Gọi điện):
 *  - Mỗi loại tin có viewType riêng (hằng số VT_... bên dưới). Số lẻ = tin mình gửi, số chẵn = tin nhận.
 *  - Mọi ViewHolder kế thừa BaseHolder và cài đặt bind(message, isMine, showAvatar).
 *  - Người 3 thêm holder cho ảnh / video / file / audio / cuộc gọi vào onCreateViewHolder (chỗ đánh dấu TODO).
 *  - Trước khi Người 3 làm xong, các loại tin đó tạm hiện bằng bong bóng chữ, VD "[Hình ảnh]".
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.BaseHolder> {

    public static final int VT_TEXT_SENT = 1;
    public static final int VT_TEXT_RECEIVED = 2;
    public static final int VT_IMAGE_SENT = 11;
    public static final int VT_IMAGE_RECEIVED = 12;
    public static final int VT_VIDEO_SENT = 21;
    public static final int VT_VIDEO_RECEIVED = 22;
    public static final int VT_FILE_SENT = 31;
    public static final int VT_FILE_RECEIVED = 32;
    public static final int VT_AUDIO_SENT = 41;
    public static final int VT_AUDIO_RECEIVED = 42;
    public static final int VT_CALL_SENT = 51;
    public static final int VT_CALL_RECEIVED = 52;

    /** Lớp cha của mọi ViewHolder tin nhắn. */
    public abstract static class BaseHolder extends RecyclerView.ViewHolder {
        public BaseHolder(@NonNull View itemView) { super(itemView); }

        /**
         * @param isMine     tin do mình gửi
         * @param showAvatar chỉ true với tin nhận cuối cùng trong một chuỗi tin liên tiếp của cùng người
         */
        public abstract void bind(@NonNull Message message, boolean isMine, boolean showAvatar);
    }

    private final List<Message> messages = new ArrayList<>();
    private final String myUid;
    private User otherUser;

    public MessageAdapter(@NonNull String myUid) {
        this.myUid = myUid;
    }

    public void submitList(@Nullable List<Message> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        notifyDataSetChanged();
    }

    /** Người kia trong chat 1-1 – dùng để vẽ avatar nhỏ cạnh tin nhận. */
    public void setOtherUser(@Nullable User user) {
        this.otherUser = user;
        notifyDataSetChanged();
    }

    private boolean isMine(Message m) {
        return myUid.equals(m.getSenderId());
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        boolean mine = isMine(m);
        String type = m.getType() != null ? m.getType() : Constants.MSG_TYPE_TEXT;
        switch (type) {
            case Constants.MSG_TYPE_IMAGE: return mine ? VT_IMAGE_SENT : VT_IMAGE_RECEIVED;
            case Constants.MSG_TYPE_VIDEO: return mine ? VT_VIDEO_SENT : VT_VIDEO_RECEIVED;
            case Constants.MSG_TYPE_FILE:  return mine ? VT_FILE_SENT : VT_FILE_RECEIVED;
            case Constants.MSG_TYPE_AUDIO: return mine ? VT_AUDIO_SENT : VT_AUDIO_RECEIVED;
            case Constants.MSG_TYPE_CALL:  return mine ? VT_CALL_SENT : VT_CALL_RECEIVED;
            default:                       return mine ? VT_TEXT_SENT : VT_TEXT_RECEIVED;
        }
    }

    @NonNull
    @Override
    public BaseHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VT_TEXT_SENT:
                return new SentTextHolder(ItemMessageTextSentBinding.inflate(inflater, parent, false));
            case VT_TEXT_RECEIVED:
                return new ReceivedTextHolder(ItemMessageTextReceivedBinding.inflate(inflater, parent, false));

            // TODO Người 3: thêm holder riêng cho từng loại tin, VD:
            // case VT_IMAGE_SENT:
            // case VT_IMAGE_RECEIVED:
            //     return new ImageMessageHolder(ItemMessageImageBinding.inflate(inflater, parent, false));

            default:
                // Tạm thời: loại tin chưa có giao diện riêng → hiện dạng bong bóng chữ "[Hình ảnh]", "[Tệp] ..."
                boolean sent = viewType % 2 == 1;
                return sent
                        ? new SentTextHolder(ItemMessageTextSentBinding.inflate(inflater, parent, false))
                        : new ReceivedTextHolder(ItemMessageTextReceivedBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseHolder holder, int position) {
        Message m = messages.get(position);
        boolean mine = isMine(m);
        boolean showAvatar = false;
        if (!mine) {
            boolean isLast = position == messages.size() - 1;
            showAvatar = isLast || isMine(messages.get(position + 1))
                    || !safeEquals(m.getSenderId(), messages.get(position + 1).getSenderId());
        }
        holder.bind(m, mine, showAvatar);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private static boolean safeEquals(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Nội dung chữ của tin: tin chữ → text; loại khác → "[Hình ảnh]"...; tin đã thu hồi → thông báo. */
    private static CharSequence contentOf(@NonNull View view, @NonNull Message m) {
        if (m.isRecalled()) return view.getContext().getString(R.string.message_recalled);
        return ChatRepo.previewFor(view.getContext(), m);
    }

    // ===================== TIN CHỮ – MÌNH GỬI =====================
    class SentTextHolder extends BaseHolder {
        private final ItemMessageTextSentBinding b;

        SentTextHolder(ItemMessageTextSentBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        @Override
        public void bind(@NonNull Message m, boolean isMine, boolean showAvatar) {
            b.tvMessage.setText(contentOf(itemView, m));
            boolean special = m.isRecalled() || !Constants.MSG_TYPE_TEXT.equals(m.getType());
            b.tvMessage.setTypeface(null, special ? Typeface.ITALIC : Typeface.NORMAL);

            String time = TimeUtils.formatMessageTime(m.getCreatedAt());
            boolean seenByOther = m.getSeenBy() != null && m.getSeenBy().size() > 1;
            b.tvTime.setText(seenByOther
                    ? time + " · " + itemView.getContext().getString(R.string.message_seen)
                    : time);
        }
    }

    // ===================== TIN CHỮ – NHẬN =====================
    class ReceivedTextHolder extends BaseHolder {
        private final ItemMessageTextReceivedBinding b;

        ReceivedTextHolder(ItemMessageTextReceivedBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        @Override
        public void bind(@NonNull Message m, boolean isMine, boolean showAvatar) {
            b.tvMessage.setText(contentOf(itemView, m));
            boolean special = m.isRecalled() || !Constants.MSG_TYPE_TEXT.equals(m.getType());
            b.tvMessage.setTypeface(null, special ? Typeface.ITALIC : Typeface.NORMAL);
            b.tvTime.setText(TimeUtils.formatMessageTime(m.getCreatedAt()));

            // Avatar nhỏ chỉ hiện ở tin cuối của mỗi chuỗi; INVISIBLE (không phải GONE) để bong bóng thẳng hàng
            b.avatarContainer.setVisibility(showAvatar ? View.VISIBLE : View.INVISIBLE);
            if (showAvatar) {
                String name = otherUser != null ? otherUser.getDisplayName() : null;
                String url = otherUser != null ? otherUser.getAvatarUrl() : null;
                AvatarUtils.bind(b.tvAvatarInitial, b.imgAvatar, name, url);
            }
            // Tên người gửi – dùng cho chat nhóm (tuần 3)
            b.tvSenderName.setVisibility(View.GONE);
        }
    }
}
