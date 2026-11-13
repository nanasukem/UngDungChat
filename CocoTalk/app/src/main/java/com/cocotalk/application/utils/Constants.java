package com.cocotalk.application.utils;

public class Constants {
    // Collections Firestore
    public static final String KEY_COLLECTION_USERS = "users";
    public static final String KEY_SUBCOLLECTION_FRIENDS = "friends"; // users/{uid}/friends
    public static final String KEY_COLLECTION_CONVERSATIONS = "conversations";
    public static final String KEY_SUBCOLLECTION_MESSAGES = "messages"; // conversations/{id}/messages
    public static final String KEY_COLLECTION_CALLS = "calls";

    // Trạng thái bạn bè
    public static final String FRIEND_STATUS_PENDING = "pending";
    public static final String FRIEND_STATUS_ACCEPTED = "accepted";
    public static final String FRIEND_STATUS_BLOCKED = "blocked";

    // Loại chat
    public static final String CONVERSATION_TYPE_DIRECT = "direct";
    public static final String CONVERSATION_TYPE_GROUP = "group";

    // Loại tin nhắn
    public static final String MSG_TYPE_TEXT = "text";
    public static final String MSG_TYPE_IMAGE = "image";
    public static final String MSG_TYPE_VIDEO = "video";
    public static final String MSG_TYPE_FILE = "file";
    public static final String MSG_TYPE_AUDIO = "audio";
    public static final String MSG_TYPE_CALL = "call";
    public static final String MSG_TYPE_LOCATION = "location";

    // Loại và trạng thái cuộc gọi
    public static final String CALL_TYPE_VOICE = "voice";
    public static final String CALL_TYPE_VIDEO = "video";
    public static final String CALL_STATUS_RINGING = "ringing";
    public static final String CALL_STATUS_ACCEPTED = "accepted";
    public static final String CALL_STATUS_ENDED = "ended";
    public static final String CALL_STATUS_MISSED = "missed";

    // SharedPreferences
    public static final String PREF_NAME = "CocoTalkPrefs";
    public static final String KEY_LANGUAGE = "language";
}