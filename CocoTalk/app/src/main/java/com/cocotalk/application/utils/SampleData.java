package com.cocotalk.application.utils;

import com.cocotalk.application.data.model.Attachment;
import com.cocotalk.application.data.model.Conversation;
import com.cocotalk.application.data.model.Message;
import com.cocotalk.application.data.model.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu mẫu để dựng / kiểm tra giao diện danh sách chat và màn hình Chat khi CHƯA cần Firebase
 * (khớp với mockup Figma). Đổi ENABLED = true để bật, false để dùng dữ liệu thật trên Firestore.
 */
public final class SampleData {
    private SampleData() {}

    public static final boolean ENABLED = false;

    public static final String MY_UID = "sample_me";
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final long DAY = 24 * HOUR;

    public static Map<String, User> users() {
        Map<String, User> map = new HashMap<>();
        map.put("sample_anh", user("sample_anh", "Anh", true));
        map.put("sample_binh", user("sample_binh", "Bình", true));
        map.put("sample_chi", user("sample_chi", "Chi", false));
        map.put("sample_dung", user("sample_dung", "Dũng", false));
        return map;
    }

    public static User user(String uid) {
        User u = users().get(uid);
        return u != null ? u : user(uid, uid, false);
    }

    private static User user(String uid, String name, boolean online) {
        User u = new User(uid, "", name, "");
        u.setOnline(online);
        return u;
    }

    public static List<Conversation> conversations(String myUid) {
        long now = System.currentTimeMillis();
        List<Conversation> list = new ArrayList<>();

        Conversation group = conv("sample_group", Constants.CONVERSATION_TYPE_GROUP,
                Arrays.asList(myUid, "sample_anh", "sample_binh"), "Bình: mai 8h họp nhé", "sample_binh", now - 5 * MIN);
        group.setGroupName("Nhóm đồ án");
        group.getUnreadCount().put(myUid, 3);
        list.add(group);

        Conversation anh = conv(directId(myUid, "sample_anh"), Constants.CONVERSATION_TYPE_DIRECT,
                Arrays.asList(myUid, "sample_anh"), "[Hình ảnh]", "sample_anh", now - 10 * MIN);
        anh.getUnreadCount().put(myUid, 1);
        list.add(anh);

        list.add(conv(directId(myUid, "sample_binh"), Constants.CONVERSATION_TYPE_DIRECT,
                Arrays.asList(myUid, "sample_binh"), "ok nhé", myUid, now - DAY));
        list.add(conv(directId(myUid, "sample_chi"), Constants.CONVERSATION_TYPE_DIRECT,
                Arrays.asList(myUid, "sample_chi"), "[Video]", "sample_chi", now - 3 * DAY));
        list.add(conv(directId(myUid, "sample_dung"), Constants.CONVERSATION_TYPE_DIRECT,
                Arrays.asList(myUid, "sample_dung"), "Cảm ơn bạn nhiều nha!", "sample_dung", now - 15 * DAY));
        return list;
    }

    public static List<Message> messages(String conversationId, String myUid, String otherUid) {
        long now = System.currentTimeMillis();
        String other = otherUid != null ? otherUid : "sample_anh";
        List<Message> list = new ArrayList<>();
        list.add(msg("m1", conversationId, myUid, Constants.MSG_TYPE_TEXT, "Chào bạn", now - 15 * MIN));
        Message file = msg("m2", conversationId, myUid, Constants.MSG_TYPE_FILE, "", now - 13 * MIN);
        file.getAttachments().add(new Attachment("", "BaoCao_Tuan1.pdf", "application/pdf", 1_200_000));
        file.getSeenBy().add(other);
        list.add(file);
        list.add(msg("m3", conversationId, other, Constants.MSG_TYPE_TEXT,
                "Chào Thy, mình gửi bạn hình bài tập hôm nay nè", now - 11 * MIN));
        Message image = msg("m4", conversationId, other, Constants.MSG_TYPE_IMAGE, "", now - 10 * MIN);
        image.getAttachments().add(new Attachment("", "baitap.jpg", "image/jpeg", 204_800));
        list.add(image);
        return list;
    }

    private static Conversation conv(String id, String type, List<String> members,
                                     String last, String lastSender, long time) {
        Conversation c = new Conversation(id, type, new ArrayList<>(members), last, time);
        c.setLastSenderId(lastSender);
        return c;
    }

    private static Message msg(String id, String convId, String sender, String type, String text, long time) {
        Message m = new Message(id, convId, sender, type, text, time);
        m.getSeenBy().add(sender);
        return m;
    }

    private static String directId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}
