package com.cocotalk.application.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocotalk.application.R;
import com.cocotalk.application.data.model.Attachment;
import com.cocotalk.application.data.model.Conversation;
import com.cocotalk.application.data.model.Message;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.utils.Constants;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mọi thao tác Firestore liên quan đến cuộc trò chuyện và tin nhắn (Người 2 – Chat).
 *
 * Lưu ý: model của nhóm dùng kiểu long cho thời gian, nhưng trên Firestore thời gian
 * được lưu bằng Timestamp (serverTimestamp). Vì vậy repo này đọc dữ liệu thủ công
 * (parseConversation / parseMessage) thay vì dùng toObject() để tránh lỗi chuyển kiểu.
 */
public class ChatRepo {
    private final FirebaseFirestore db;

    public interface ConversationsCallback {
        void onResult(@NonNull List<Conversation> conversations);
        void onError(String message);
    }

    public interface MessagesCallback {
        void onResult(@NonNull List<Message> messages);
        void onError(String message);
    }

    public interface CompleteCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface MetaListener {
        /**
         * @param deliveredAt uid → thời điểm (ms) máy của người đó đã nhận tin mới nhất
         * @param typing      uid → thời điểm (ms) người đó báo "đang nhập" gần nhất
         */
        void onMeta(@NonNull Map<String, Long> deliveredAt, @NonNull Map<String, Long> typing);
    }

    /** Coi là "đang nhập" nếu lần báo gần nhất cách đây chưa quá 10 giây (phòng khi app bị tắt đột ngột). */
    public static final long TYPING_TIMEOUT_MS = 10_000L;

    public interface UserListener {
        void onUser(@Nullable User user);
    }

    public ChatRepo() {
        this.db = FirebaseFirestore.getInstance();
    }

    /** Id chat 1-1: ghép 2 uid theo thứ tự chữ cái để A nhắn B hay B nhắn A đều ra cùng 1 id. */
    public static String directConversationId(@NonNull String uid1, @NonNull String uid2) {
        return uid1.compareTo(uid2) < 0 ? uid1 + "_" + uid2 : uid2 + "_" + uid1;
    }

    private DocumentReference conversationRef(@NonNull String conversationId) {
        return db.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
    }

    // ===================== DANH SÁCH CUỘC TRÒ CHUYỆN =====================

    /**
     * Lắng nghe realtime các cuộc trò chuyện có mình tham gia, mới nhất lên đầu.
     * Cần index ghép: conversations (members array-contains + lastMessageTime desc) – đã tạo trên Firebase.
     */
    public ListenerRegistration listenConversations(@NonNull String myUid,
                                                    @NonNull ConversationsCallback callback) {
        return db.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereArrayContains("members", myUid)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    List<Conversation> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            markDelivered(doc, myUid);
                            Conversation c = parseConversation(doc);
                            // Ẩn cuộc trò chuyện chưa có tin nhắn nào
                            if (c.getLastMessage() != null && !c.getLastMessage().isEmpty()) {
                                list.add(c);
                            }
                        }
                    }
                    callback.onResult(list);
                });
    }

    // ===================== TIN NHẮN =====================

    /** Lắng nghe realtime tin nhắn của một cuộc trò chuyện, cũ trước – mới sau. */
    public ListenerRegistration listenMessages(@NonNull String conversationId,
                                               @NonNull MessagesCallback callback) {
        return conversationRef(conversationId)
                .collection(Constants.KEY_SUBCOLLECTION_MESSAGES)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                // INCLUDE: báo cả lúc tin chuyển từ "đang gửi" sang "đã lưu lên server"
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    List<Message> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            list.add(parseMessage(doc, conversationId));
                        }
                    }
                    // Tin vừa gửi (chưa có giờ server) dùng giờ ước tính; sắp xếp lại cho chắc chắn đúng thứ tự
                    Collections.sort(list, (a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
                    callback.onResult(list);
                });
    }

    /**
     * Gửi một tin nhắn (dùng chung cho mọi loại tin: chữ, ảnh, video, file, cuộc gọi...).
     * Trong CÙNG MỘT lần ghi (batch):
     *  1. Thêm tin vào conversations/{id}/messages
     *  2. Cập nhật bản tóm tắt: lastMessage, lastSenderId, lastMessageTime
     *  3. Tăng unreadCount của người nhận lên 1, của mình về 0
     * Nếu cuộc trò chuyện chưa tồn tại (chat 1-1 lần đầu) thì tự tạo luôn.
     *
     * @param conversationType Constants.CONVERSATION_TYPE_DIRECT hoặc _GROUP
     * @param members          danh sách thành viên (bắt buộc với chat 1-1 để tạo mới)
     * @param preview          chữ hiện ở danh sách chat, VD "Chào bạn" hoặc "[Hình ảnh]" – lấy từ previewFor()
     */
    public void sendMessage(@NonNull String conversationId,
                            @NonNull String conversationType,
                            @NonNull List<String> members,
                            @NonNull String myUid,
                            @NonNull Message message,
                            @NonNull String preview,
                            @Nullable CompleteCallback callback) {
        DocumentReference convRef = conversationRef(conversationId);
        DocumentReference msgRef = convRef.collection(Constants.KEY_SUBCOLLECTION_MESSAGES).document();
        message.setMessageId(msgRef.getId());
        message.setConversationId(conversationId);

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", myUid);
        msg.put("type", message.getType() != null ? message.getType() : Constants.MSG_TYPE_TEXT);
        msg.put("text", message.getText() != null ? message.getText() : "");
        msg.put("attachments", attachmentsToMaps(message.getAttachments()));
        msg.put("seenBy", Collections.singletonList(myUid));
        msg.put("isRecalled", false);
        msg.put("isPinned", false);
        msg.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> unread = new HashMap<>();
        for (String uid : members) {
            unread.put(uid, uid.equals(myUid) ? 0 : FieldValue.increment(1));
        }

        Map<String, Object> conv = new HashMap<>();
        conv.put("type", conversationType);
        if (Constants.CONVERSATION_TYPE_DIRECT.equals(conversationType)) {
            conv.put("members", members); // chat 1-1: đảm bảo có danh sách thành viên khi tạo mới
        }
        conv.put("lastMessage", preview);
        conv.put("lastSenderId", myUid);
        conv.put("lastMessageTime", FieldValue.serverTimestamp());
        conv.put("unreadCount", unread);

        WriteBatch batch = db.batch();
        // Lưu ý khi viết Security Rules: tin nhắn và cuộc trò chuyện mới được tạo trong cùng batch,
        // nên rule của messages phải dùng getAfter(...) thay vì get(...) để đọc danh sách members.
        batch.set(convRef, conv, SetOptions.merge());
        batch.set(msgRef, msg);
        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /** Đặt số tin chưa đọc của mình về 0 khi mở cuộc trò chuyện. */
    public void markAsRead(@NonNull String conversationId, @NonNull String myUid) {
        conversationRef(conversationId)
                .update("unreadCount." + myUid, 0)
                .addOnFailureListener(e -> { /* cuộc trò chuyện chưa tồn tại – bỏ qua */ });
    }

    /**
     * Đánh dấu "đã nhận": khi danh sách chat của mình nhận được tin mới do người khác gửi,
     * ghi deliveredAt.{myUid} = giờ server vào cuộc trò chuyện. Người gửi so sánh với giờ gửi tin
     * để hiện "Đã nhận". Chỉ ghi khi cần (deliveredAt cũ hơn tin mới nhất) để không lặp vô hạn.
     */
    private void markDelivered(@NonNull DocumentSnapshot doc, @NonNull String myUid) {
        if (doc.getMetadata().hasPendingWrites()) return;
        String lastSender = doc.getString("lastSenderId");
        if (lastSender == null || lastSender.equals(myUid)) return;
        long lastTime = toMillis(doc.get("lastMessageTime"));
        long delivered = toMillis(doc.get("deliveredAt." + myUid));
        if (lastTime > 0 && delivered < lastTime) {
            doc.getReference()
                    .update("deliveredAt." + myUid, FieldValue.serverTimestamp())
                    .addOnFailureListener(e -> { /* bỏ qua, lần sau ghi lại */ });
        }
    }

    /**
     * Lắng nghe deliveredAt + typing của một cuộc trò chuyện –
     * màn hình Chat dùng để hiện "Đã nhận" và bong bóng "đang nhập".
     */
    public ListenerRegistration listenMeta(@NonNull String conversationId,
                                           @NonNull MetaListener listener) {
        return conversationRef(conversationId).addSnapshotListener((doc, error) -> {
            if (error == null && doc != null && doc.exists()) {
                listener.onMeta(timeMap(doc, "deliveredAt"), timeMap(doc, "typing"));
            } else {
                listener.onMeta(new HashMap<>(), new HashMap<>());
            }
        });
    }

    /**
     * Báo "đang nhập" (typing = true) hoặc đã ngừng (false) vào conversations/{id}.typing.{uid}.
     * Dùng update nên nếu cuộc trò chuyện chưa có tin nào (chưa tồn tại) thì bỏ qua.
     */
    public void setTyping(@NonNull String conversationId, @NonNull String myUid, boolean typing) {
        conversationRef(conversationId)
                .update("typing." + myUid, typing ? FieldValue.serverTimestamp() : FieldValue.delete())
                .addOnFailureListener(e -> { /* cuộc trò chuyện chưa tồn tại – bỏ qua */ });
    }

    /** true nếu có người khác (không phải mình) đang nhập. */
    public static boolean someoneElseTyping(@Nullable Map<String, Long> typing, @Nullable String myUid) {
        if (typing == null) return false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : typing.entrySet()) {
            if (e.getKey().equals(myUid) || e.getValue() == null) continue;
            if (now - e.getValue() < TYPING_TIMEOUT_MS) return true;
        }
        return false;
    }

    /** Đọc một map uid → Timestamp trong document thành uid → mili-giây. */
    private static Map<String, Long> timeMap(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Map<String, Long> out = new HashMap<>();
        Object raw = doc.get(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                if (e.getKey() != null) out.put(e.getKey().toString(), toMillis(e.getValue()));
            }
        }
        return out;
    }

    /**
     * Đánh dấu "đã xem": thêm uid của mình vào seenBy của các tin người khác gửi mà mình chưa xem.
     * Người gửi sẽ thấy "· Đã xem" dưới tin của họ (MessageAdapter kiểm tra seenBy.size() > 1).
     */
    public void markSeen(@NonNull String conversationId, @NonNull String myUid,
                         @Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) return;
        WriteBatch batch = db.batch();
        int count = 0;
        for (Message m : messages) {
            if (m.getMessageId() == null || myUid.equals(m.getSenderId())) continue;
            if (m.getSeenBy() != null && m.getSeenBy().contains(myUid)) continue;
            batch.update(conversationRef(conversationId)
                            .collection(Constants.KEY_SUBCOLLECTION_MESSAGES)
                            .document(m.getMessageId()),
                    "seenBy", FieldValue.arrayUnion(myUid));
            if (++count >= 450) break; // batch tối đa 500 thao tác
        }
        if (count > 0) {
            batch.commit().addOnFailureListener(e -> { /* lỗi mạng – lần mở sau sẽ đánh dấu lại */ });
        }
    }

    /** Lắng nghe realtime thông tin người kia (tên, avatar, online) cho thanh tiêu đề màn hình Chat. */
    public ListenerRegistration listenUser(@NonNull String uid, @NonNull UserListener listener) {
        return db.collection(Constants.KEY_COLLECTION_USERS)
                .document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) {
                        listener.onUser(null);
                        return;
                    }
                    User user = new User();
                    user.setUid(doc.getId());
                    String name = doc.getString("displayName");
                    if (name != null) user.setDisplayName(name);
                    user.setPhone(doc.getString("phone"));
                    user.setAvatarUrl(doc.getString("avatarUrl"));
                    Boolean online = doc.getBoolean("isOnline");
                    user.setOnline(online != null && online);
                    user.setLastSeen(toMillis(doc.get("lastSeen")));
                    listener.onUser(user);
                });
    }

    // ===================== TIỆN ÍCH =====================

    /** Chữ hiển thị ở danh sách chat cho từng loại tin. */
    public static String previewFor(@NonNull Context context, @NonNull Message m) {
        String type = m.getType() != null ? m.getType() : Constants.MSG_TYPE_TEXT;
        switch (type) {
            case Constants.MSG_TYPE_IMAGE:
                return context.getString(R.string.preview_image);
            case Constants.MSG_TYPE_VIDEO:
                return context.getString(R.string.preview_video);
            case Constants.MSG_TYPE_FILE: {
                String name = "";
                if (m.getAttachments() != null && !m.getAttachments().isEmpty()
                        && m.getAttachments().get(0).getFileName() != null) {
                    name = m.getAttachments().get(0).getFileName();
                }
                return context.getString(R.string.preview_file, name).trim();
            }
            case Constants.MSG_TYPE_AUDIO:
                return context.getString(R.string.preview_audio);
            case Constants.MSG_TYPE_CALL:
                return Constants.CALL_TYPE_VIDEO.equals(m.getText())
                        ? context.getString(R.string.preview_call_video)
                        : context.getString(R.string.preview_call_voice);
            case Constants.MSG_TYPE_LOCATION:
                return context.getString(R.string.preview_location);
            default:
                return m.getText() != null ? m.getText() : "";
        }
    }

    /** Đổi Timestamp / Long / Date của Firestore sang mili-giây. */
    public static long toMillis(@Nullable Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }

    public static Conversation parseConversation(@NonNull DocumentSnapshot doc) {
        Conversation c = new Conversation();
        c.setId(doc.getId());
        String type = doc.getString("type");
        c.setType(type != null ? type : Constants.CONVERSATION_TYPE_DIRECT);
        c.setMembers(toStringList(doc.get("members")));
        c.setAdmins(toStringList(doc.get("admins")));
        c.setPinnedBy(toStringList(doc.get("pinnedBy")));
        c.setGroupName(doc.getString("groupName"));
        c.setGroupAvatar(doc.getString("groupAvatar"));
        c.setLastMessage(doc.getString("lastMessage"));
        c.setLastSenderId(doc.getString("lastSenderId"));
        c.setLastMessageTime(toMillis(doc.get("lastMessageTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)));
        Map<String, Integer> unread = new HashMap<>();
        Object raw = doc.get("unreadCount");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Number) {
                    unread.put(e.getKey().toString(), ((Number) e.getValue()).intValue());
                }
            }
        }
        c.setUnreadCount(unread);
        c.setTyping(timeMap(doc, "typing"));
        return c;
    }

    public static Message parseMessage(@NonNull DocumentSnapshot doc, @NonNull String conversationId) {
        Message m = new Message();
        m.setMessageId(doc.getId());
        m.setConversationId(conversationId);
        m.setSenderId(doc.getString("senderId"));
        String type = doc.getString("type");
        m.setType(type != null ? type : Constants.MSG_TYPE_TEXT);
        m.setText(doc.getString("text"));
        long created = toMillis(doc.get("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE));
        m.setCreatedAt(created > 0 ? created : System.currentTimeMillis());
        Boolean recalled = doc.getBoolean("isRecalled");
        if (recalled == null) recalled = doc.getBoolean("recalled");
        m.setRecalled(recalled != null && recalled);
        Boolean pinned = doc.getBoolean("isPinned");
        m.setPinned(pinned != null && pinned);
        m.setSeenBy(toStringList(doc.get("seenBy")));
        m.setPending(doc.getMetadata().hasPendingWrites());

        List<Attachment> attachments = new ArrayList<>();
        Object rawAtt = doc.get("attachments");
        if (rawAtt instanceof List) {
            for (Object o : (List<?>) rawAtt) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> a = (Map<?, ?>) o;
                Attachment att = new Attachment();
                att.setUrl(str(a.get("url")));
                att.setFileName(str(a.get("fileName") != null ? a.get("fileName") : a.get("name")));
                att.setFileType(str(a.get("fileType") != null ? a.get("fileType") : a.get("mimeType")));
                Object size = a.get("fileSize") != null ? a.get("fileSize") : a.get("size");
                att.setFileSize(size instanceof Number ? ((Number) size).longValue() : 0L);
                Object duration = a.get("duration");
                att.setDuration(duration instanceof Number ? ((Number) duration).longValue() : 0L);
                attachments.add(att);
            }
        }
        m.setAttachments(attachments);
        return m;
    }

    private static List<Map<String, Object>> attachmentsToMaps(@Nullable List<Attachment> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (list == null) return out;
        for (Attachment a : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("url", a.getUrl() != null ? a.getUrl() : "");
            map.put("fileName", a.getFileName() != null ? a.getFileName() : "");
            map.put("fileType", a.getFileType() != null ? a.getFileType() : "");
            map.put("fileSize", a.getFileSize());
            map.put("duration", a.getDuration());
            out.add(map);
        }
        return out;
    }

    private static List<String> toStringList(@Nullable Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) out.add(o.toString());
            }
        }
        return out;
    }

    @Nullable
    private static String str(@Nullable Object o) {
        return o != null ? o.toString() : null;
    }
}
