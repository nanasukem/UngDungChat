package com.cocotalk.application.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocotalk.application.data.model.FriendRequest;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.utils.Constants;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendRepo {
    private final FirebaseFirestore db;

    public interface FriendRelationCallback {
        void onResult(@Nullable FriendRequest relation);
        void onError(String message);
    }

    public interface CompleteCallback {
        void onSuccess();
        void onError(String message);
    }

    public static class FriendItem {
        private final FriendRequest request;
        private final User user;

        public FriendItem(FriendRequest request, User user) {
            this.request = request;
            this.user = user;
        }

        public FriendRequest getRequest() { return request; }
        public User getUser() { return user; }
    }

    public interface FriendsListCallback {
        void onResult(List<FriendItem> incomingRequests,
                      List<FriendItem> sentRequests,
                      List<FriendItem> acceptedFriends);
        void onError(String message);
    }

    public FriendRepo() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Kiểm tra quan hệ bạn bè hiện tại giữa currentUid và targetUid
     * tại users/{currentUid}/friends/{targetUid}.
     */
    public void getFriendRelation(@NonNull String currentUid,
                                  @NonNull String targetUid,
                                  @NonNull FriendRelationCallback callback) {
        db.collection(Constants.KEY_COLLECTION_USERS)
                .document(currentUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(targetUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        FriendRequest request = snapshot.toObject(FriendRequest.class);
                        if (request != null && (request.getFriendUid() == null || request.getFriendUid().isEmpty())) {
                            request.setFriendUid(snapshot.getId());
                        }
                        callback.onResult(request);
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Realtime danh sách lời mời kết bạn và danh sách bạn bè.
     */
    public ListenerRegistration listenFriendsAndRequests(@NonNull String currentUid,
                                                         @NonNull FriendsListCallback callback) {
        return db.collection(Constants.KEY_COLLECTION_USERS)
                .document(currentUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (snapshots == null || snapshots.isEmpty()) {
                        callback.onResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                        return;
                    }

                    List<FriendRequest> rawList = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        FriendRequest req = doc.toObject(FriendRequest.class);
                        if (req != null) {
                            if (req.getFriendUid() == null || req.getFriendUid().isEmpty()) {
                                req.setFriendUid(doc.getId());
                            }
                            rawList.add(req);
                        }
                    }

                    if (rawList.isEmpty()) {
                        callback.onResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                        return;
                    }

                    List<FriendItem> incoming = new ArrayList<>();
                    List<FriendItem> sent = new ArrayList<>();
                    List<FriendItem> accepted = new ArrayList<>();
                    AtomicInteger remaining = new AtomicInteger(rawList.size());

                    for (FriendRequest req : rawList) {
                        String otherUid = req.getFriendUid();
                        db.collection(Constants.KEY_COLLECTION_USERS)
                                .document(otherUid)
                                .get()
                                .addOnCompleteListener(task -> {
                                    User user = null;
                                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                        user = task.getResult().toObject(User.class);
                                        if (user != null && (user.getUid() == null || user.getUid().isEmpty())) {
                                            user.setUid(task.getResult().getId());
                                        }
                                    }
                                    if (user == null) {
                                        user = new User(otherUid, "", otherUid, "");
                                    }

                                    FriendItem item = new FriendItem(req, user);
                                    synchronized (rawList) {
                                        if (Constants.FRIEND_STATUS_ACCEPTED.equals(req.getStatus())) {
                                            accepted.add(item);
                                        } else if (Constants.FRIEND_STATUS_PENDING.equals(req.getStatus())) {
                                            if (currentUid.equals(req.getSenderUid())) {
                                                sent.add(item);
                                            } else {
                                                incoming.add(item);
                                            }
                                        }
                                    }

                                    if (remaining.decrementAndGet() == 0) {
                                        callback.onResult(incoming, sent, accepted);
                                    }
                                });
                    }
                });
    }

    /**
     * Gửi lời mời kết bạn: Ghi 2 chiều trong cùng một WriteBatch theo đúng quy ước:
     * - users/{currentUid}/friends/{targetUid}: status = pending, senderUid = currentUid
     * - users/{targetUid}/friends/{currentUid}: status = pending, senderUid = currentUid
     */
    public void sendFriendRequest(@NonNull String currentUid,
                                  @NonNull String targetUid,
                                  @NonNull CompleteCallback callback) {
        long now = System.currentTimeMillis();

        DocumentReference myFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(currentUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(targetUid);

        DocumentReference theirFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(targetUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(currentUid);

        FriendRequest mySide = new FriendRequest(
                targetUid,
                currentUid,
                Constants.FRIEND_STATUS_PENDING,
                "",
                now
        );

        FriendRequest theirSide = new FriendRequest(
                currentUid,
                currentUid,
                Constants.FRIEND_STATUS_PENDING,
                "",
                now
        );

        WriteBatch batch = db.batch();
        batch.set(myFriendRef, mySide);
        batch.set(theirFriendRef, theirSide);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Chấp nhận lời mời kết bạn: Cập nhật 2 chiều trong cùng một WriteBatch sang status = accepted.
     */
    public void acceptFriendRequest(@NonNull String currentUid,
                                    @NonNull String friendUid,
                                    @NonNull CompleteCallback callback) {
        DocumentReference myFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(currentUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(friendUid);

        DocumentReference theirFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(friendUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(currentUid);

        WriteBatch batch = db.batch();
        batch.update(myFriendRef, "status", Constants.FRIEND_STATUS_ACCEPTED);
        batch.update(theirFriendRef, "status", Constants.FRIEND_STATUS_ACCEPTED);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Từ chối hoặc tự hủy lời mời kết bạn đã gửi: Xóa 2 chiều trong cùng một WriteBatch.
     */
    public void declineOrCancelFriendRequest(@NonNull String currentUid,
                                             @NonNull String friendUid,
                                             @NonNull CompleteCallback callback) {
        DocumentReference myFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(currentUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(friendUid);

        DocumentReference theirFriendRef = db.collection(Constants.KEY_COLLECTION_USERS)
                .document(friendUid)
                .collection(Constants.KEY_SUBCOLLECTION_FRIENDS)
                .document(currentUid);

        WriteBatch batch = db.batch();
        batch.delete(myFriendRef);
        batch.delete(theirFriendRef);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
