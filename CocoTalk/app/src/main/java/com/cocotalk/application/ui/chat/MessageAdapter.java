package com.cocotalk.application.ui.chat;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocotalk.application.R;
import com.cocotalk.application.data.model.Message;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.ChatRepo;
import com.cocotalk.application.databinding.ItemMessageTextReceivedBinding;
import com.cocotalk.application.databinding.ItemMessageTextSentBinding;
import com.cocotalk.application.databinding.ItemMessageTypingBinding;
import com.cocotalk.application.utils.AvatarUtils;
import com.cocotalk.application.utils.Constants;
import com.cocotalk.application.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter tin nhắn trong màn hình Chat – giao diện theo kiểu Messenger:
 *  - Tin liên tiếp của cùng một người được gom nhóm, bo góc thay đổi theo vị trí (đầu / giữa / cuối nhóm).
 *  - Giờ hiện ở giữa màn hình khi cách tin trước > 15 phút; bấm vào bong bóng để xem giờ của tin đó.
 *  - Tin cuối của mình hiện "Đang gửi / Đã gửi / Đã nhận"; khi người kia đã xem → avatar nhỏ của họ.
 *  - Tin chỉ có 1–3 emoji hiện to, không có bong bóng.
 *  - Khi người kia đang nhập → bong bóng 3 chấm nhảy ở cuối danh sách.
 *  - Nhấn giữ bong bóng để sao chép.
 *
 * QUY ƯỚC VỚI NGƯỜI 3 (Media & Gọi điện):
 *  - Mỗi loại tin có viewType riêng (hằng số VT_... bên dưới). Số lẻ = tin mình gửi, số chẵn = tin nhận.
 *  - Mọi ViewHolder kế thừa BaseHolder và cài đặt bind(message, isMine, showAvatar).
 *    Trước khi bind, adapter đã điền sẵn groupPosition / showTimeHeader – holder dùng
 *    bubbleBackground(...), bindTimeHeader(...), statusFor(...) để có cùng kiểu Messenger.
 *  - Người 3 thêm holder cho ảnh / video / file / audio / cuộc gọi vào onCreateViewHolder (chỗ đánh dấu TODO).
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    public static final int VT_TYPING = 99;

    /** Vị trí của tin trong một nhóm tin liên tiếp cùng người gửi. */
    public static final int GROUP_SINGLE = 0;
    public static final int GROUP_FIRST = 1;
    public static final int GROUP_MIDDLE = 2;
    public static final int GROUP_LAST = 3;

    /** Cách nhau hơn 15 phút → hiện giờ ở giữa màn hình và bắt đầu nhóm mới (giống Messenger). */
    private static final long TIME_HEADER_GAP_MS = 15 * 60 * 1000L;

    /** Lớp cha của mọi ViewHolder tin nhắn. */
    public abstract static class BaseHolder extends RecyclerView.ViewHolder {
        /** GROUP_SINGLE / FIRST / MIDDLE / LAST – adapter điền trước khi gọi bind. */
        protected int groupPosition = GROUP_SINGLE;
        /** true nếu cần hiện giờ ở giữa màn hình phía trên tin này. */
        protected boolean showTimeHeader;

        public BaseHolder(@NonNull View itemView) { super(itemView); }

        /**
         * @param isMine     tin do mình gửi
         * @param showAvatar chỉ true với tin nhận cuối cùng trong một nhóm tin liên tiếp của cùng người
         */
        public abstract void bind(@NonNull Message message, boolean isMine, boolean showAvatar);
    }

    private final List<Message> messages = new ArrayList<>();
    private final String myUid;
    private User otherUser;
    /** Tin cuối cùng do mình gửi – chỉ tin này hiện trạng thái. */
    private Message lastMine;
    private Map<String, Long> deliveredAt = new HashMap<>();
    /** Các tin đang được bấm để xem giờ. */
    private final Set<String> expanded = new HashSet<>();
    private boolean otherTyping;

    public MessageAdapter(@NonNull String myUid) {
        this.myUid = myUid;
    }

    public void submitList(@Nullable List<Message> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        lastMine = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isMine(messages.get(i))) { lastMine = messages.get(i); break; }
        }
        notifyDataSetChanged();
    }

    /** Người kia trong chat 1-1 – dùng để vẽ avatar nhỏ cạnh tin nhận, avatar "đã xem" và bong bóng đang nhập. */
    public void setOtherUser(@Nullable User user) {
        this.otherUser = user;
        notifyDataSetChanged();
    }

    /** uid → thời điểm máy người đó nhận tin (từ conversations/{id}.deliveredAt). */
    public void setDeliveredAt(@Nullable Map<String, Long> map) {
        this.deliveredAt = map != null ? map : new HashMap<>();
        if (lastMine != null) notifyDataSetChanged();
    }

    /** Hiện / ẩn bong bóng "đang nhập" ở cuối danh sách. */
    public void setOtherTyping(boolean typing) {
        if (otherTyping == typing) return;
        otherTyping = typing;
        if (typing) notifyItemInserted(messages.size());
        else notifyItemRemoved(messages.size());
    }

    public boolean isOtherTyping() { return otherTyping; }

    /** Số tin nhắn (không tính bong bóng đang nhập). */
    public int getMessageCount() { return messages.size(); }

    private boolean isMine(Message m) {
        return myUid.equals(m.getSenderId());
    }

    // ===================== TRẠNG THÁI TIN =====================

    /** true nếu tin đã được người khác xem. */
    public boolean isSeen(@NonNull Message m) {
        if (m.getSeenBy() == null) return false;
        for (String uid : m.getSeenBy()) {
            if (!myUid.equals(uid)) return true;
        }
        return false;
    }

    /**
     * Trạng thái chữ của tin mình gửi: chỉ trả về cho tin cuối cùng của mình và khi CHƯA được xem
     * (đã xem thì hiện avatar nhỏ thay cho chữ, giống Messenger). Các trường hợp khác trả về null.
     */
    @Nullable
    public String statusFor(@NonNull View view, @NonNull Message m) {
        if (m != lastMine || isSeen(m)) return null;
        if (m.isPending()) return view.getContext().getString(R.string.message_sending);
        for (Map.Entry<String, Long> e : deliveredAt.entrySet()) {
            if (!myUid.equals(e.getKey()) && e.getValue() != null && e.getValue() >= m.getCreatedAt()) {
                return view.getContext().getString(R.string.message_delivered);
            }
        }
        return view.getContext().getString(R.string.message_sent);
    }

    /** true nếu cần hiện avatar "đã xem" dưới tin này (tin cuối của mình và đã được xem). */
    public boolean showSeenAvatar(@NonNull Message m) {
        return m == lastMine && isSeen(m);
    }

    // ===================== VIEW TYPE =====================

    @Override
    public int getItemViewType(int position) {
        if (position >= messages.size()) return VT_TYPING;
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VT_TYPING:
                return new TypingHolder(ItemMessageTypingBinding.inflate(inflater, parent, false));
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
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TypingHolder) {
            ((TypingHolder) holder).bind();
            return;
        }
        BaseHolder h = (BaseHolder) holder;
        Message m = messages.get(position);
        boolean mine = isMine(m);

        boolean header = needsTimeHeader(position);
        boolean joinPrev = !header && position > 0 && sameSender(messages.get(position - 1), m);
        boolean joinNext = position + 1 < messages.size()
                && !needsTimeHeader(position + 1)
                && sameSender(m, messages.get(position + 1));
        if (joinPrev && joinNext) h.groupPosition = GROUP_MIDDLE;
        else if (joinPrev) h.groupPosition = GROUP_LAST;
        else if (joinNext) h.groupPosition = GROUP_FIRST;
        else h.groupPosition = GROUP_SINGLE;
        h.showTimeHeader = header;

        // Avatar nhỏ chỉ ở tin cuối của nhóm tin nhận; nếu người kia đang nhập ngay sau
        // tin cuối cùng thì avatar chuyển xuống bong bóng "đang nhập".
        boolean showAvatar = !mine && !joinNext
                && !(otherTyping && position == messages.size() - 1);
        h.bind(m, mine, showAvatar);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof TypingHolder) ((TypingHolder) holder).stop();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return messages.size() + (otherTyping ? 1 : 0);
    }

    private boolean needsTimeHeader(int position) {
        if (position == 0) return true;
        return messages.get(position).getCreatedAt() - messages.get(position - 1).getCreatedAt()
                > TIME_HEADER_GAP_MS;
    }

    private static boolean sameSender(@NonNull Message a, @NonNull Message b) {
        return a.getSenderId() != null && a.getSenderId().equals(b.getSenderId());
    }

    // ===================== TIỆN ÍCH CHO HOLDER =====================

    /** Nội dung chữ của tin: tin chữ → text; loại khác → "[Hình ảnh]"...; tin đã thu hồi → thông báo. */
    private static CharSequence contentOf(@NonNull View view, @NonNull Message m) {
        if (m.isRecalled()) return view.getContext().getString(R.string.message_recalled);
        return ChatRepo.previewFor(view.getContext(), m);
    }

    private static float dp(@NonNull Context ctx, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics());
    }

    /**
     * Nền bong bóng bo góc kiểu Messenger: góc phía người gửi nhỏ lại khi nối với tin trên / dưới.
     * Người 3 dùng lại cho bong bóng ảnh / file.
     */
    public static GradientDrawable bubbleBackground(@NonNull Context ctx, boolean mine, int groupPosition) {
        float big = dp(ctx, 18), small = dp(ctx, 4);
        boolean topSmall = groupPosition == GROUP_MIDDLE || groupPosition == GROUP_LAST;
        boolean bottomSmall = groupPosition == GROUP_MIDDLE || groupPosition == GROUP_FIRST;
        float tl = big, tr = big, br = big, bl = big;
        if (mine) {
            if (topSmall) tr = small;
            if (bottomSmall) br = small;
        } else {
            if (topSmall) tl = small;
            if (bottomSmall) bl = small;
        }
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        d.setColor(ContextCompat.getColor(ctx, mine ? R.color.md_primary : R.color.md_surface_container_high));
        return d;
    }

    /** Khoảng cách: 1dp giữa các tin cùng nhóm, 8dp giữa các nhóm. */
    private static void applyGroupSpacing(@NonNull View root, int groupPosition) {
        Context ctx = root.getContext();
        boolean firstOfGroup = groupPosition == GROUP_SINGLE || groupPosition == GROUP_FIRST;
        root.setPadding(root.getPaddingLeft(), (int) dp(ctx, firstOfGroup ? 8 : 1),
                root.getPaddingRight(), (int) dp(ctx, 1));
    }

    /** Giờ ở giữa màn hình phía trên tin. */
    public static void bindTimeHeader(@NonNull TextView tvHeader, boolean show, long createdAt) {
        tvHeader.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) tvHeader.setText(TimeUtils.formatHeaderTime(tvHeader.getContext(), createdAt));
    }

    /** Tin chỉ gồm 1–3 emoji (không có chữ) → hiện to như Messenger. */
    static boolean isEmojiOnly(@Nullable String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 24) return false;
        int emoji = 0;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0x200D || cp == 0xFE0F || (cp >= 0x1F3FB && cp <= 0x1F3FF)) continue; // ký tự nối / tông da
            boolean isEmoji = (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                    || (cp >= 0x2B00 && cp <= 0x2BFF) || cp == 0x2764;
            if (!isEmoji) return false;
            emoji++;
        }
        return emoji >= 1 && emoji <= 3;
    }

    /** Chữ + kiểu chữ + nền cho bong bóng tin chữ. */
    private void bindBubble(@NonNull TextView tv, @NonNull Message m, boolean mine, int groupPosition) {
        CharSequence content = contentOf(tv, m);
        tv.setText(content);
        boolean text = Constants.MSG_TYPE_TEXT.equals(m.getType()) && !m.isRecalled();
        if (text && isEmojiOnly(m.getText())) {
            tv.setBackground(null);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            tv.setTypeface(null, Typeface.NORMAL);
            return;
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(null, text ? Typeface.NORMAL : Typeface.ITALIC);
        tv.setBackground(bubbleBackground(tv.getContext(), mine, groupPosition));
    }

    /** Bấm bong bóng: hiện / ẩn giờ của tin. Nhấn giữ: sao chép nội dung. */
    private void bindClicks(@NonNull TextView bubble, @NonNull Message m, @NonNull RecyclerView.ViewHolder holder) {
        bubble.setOnClickListener(v -> {
            String id = m.getMessageId();
            if (id == null) return;
            if (!expanded.remove(id)) expanded.add(id);
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
        });
        bubble.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("message", contentOf(v, m)));
                Toast.makeText(v.getContext(), R.string.message_copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private boolean isExpanded(@NonNull Message m) {
        return m.getMessageId() != null && expanded.contains(m.getMessageId());
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
            applyGroupSpacing(itemView, groupPosition);
            bindTimeHeader(b.tvTimeHeader, showTimeHeader, m.getCreatedAt());
            bindBubble(b.tvMessage, m, true, groupPosition);
            bindClicks(b.tvMessage, m, this);

            // Dòng dưới bong bóng: giờ (khi bấm) và/hoặc trạng thái (tin cuối của mình)
            String status = statusFor(itemView, m);
            String time = isExpanded(m) ? TimeUtils.formatMessageTime(m.getCreatedAt()) : null;
            String line = time != null && status != null ? time + " · " + status
                    : time != null ? time : status;
            b.tvTime.setVisibility(line != null ? View.VISIBLE : View.GONE);
            if (line != null) b.tvTime.setText(line);

            // Đã xem → avatar nhỏ của người kia (giống Messenger)
            boolean seen = showSeenAvatar(m);
            b.seenAvatarContainer.setVisibility(seen ? View.VISIBLE : View.GONE);
            if (seen) {
                String name = otherUser != null ? otherUser.getDisplayName() : null;
                String url = otherUser != null ? otherUser.getAvatarUrl() : null;
                AvatarUtils.bind(b.tvSeenInitial, b.imgSeenAvatar, name, url);
            }
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
            applyGroupSpacing(itemView, groupPosition);
            bindTimeHeader(b.tvTimeHeader, showTimeHeader, m.getCreatedAt());
            bindBubble(b.tvMessage, m, false, groupPosition);
            bindClicks(b.tvMessage, m, this);

            boolean expandedTime = isExpanded(m);
            b.tvTime.setVisibility(expandedTime ? View.VISIBLE : View.GONE);
            if (expandedTime) b.tvTime.setText(TimeUtils.formatMessageTime(m.getCreatedAt()));

            // Avatar nhỏ chỉ hiện ở tin cuối của mỗi nhóm; INVISIBLE (không phải GONE) để bong bóng thẳng hàng
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

    // ===================== ĐANG NHẬP (3 CHẤM NHẢY) =====================
    class TypingHolder extends RecyclerView.ViewHolder {
        private final ItemMessageTypingBinding b;
        private final List<ObjectAnimator> animators = new ArrayList<>();

        TypingHolder(ItemMessageTypingBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind() {
            String name = otherUser != null ? otherUser.getDisplayName() : null;
            String url = otherUser != null ? otherUser.getAvatarUrl() : null;
            AvatarUtils.bind(b.tvAvatarInitial, b.imgAvatar, name, url);
            b.typingBubble.setBackground(bubbleBackground(itemView.getContext(), false, GROUP_SINGLE));
            start();
        }

        void start() {
            stop();
            View[] dots = {b.dot1, b.dot2, b.dot3};
            float lift = -dp(itemView.getContext(), 4);
            for (int i = 0; i < dots.length; i++) {
                ObjectAnimator a = ObjectAnimator.ofFloat(dots[i], View.TRANSLATION_Y, 0f, lift, 0f);
                a.setDuration(600);
                a.setStartDelay(i * 150L);
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.start();
                animators.add(a);
            }
        }

        void stop() {
            for (ObjectAnimator a : animators) a.cancel();
            animators.clear();
        }
    }
}
