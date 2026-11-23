package com.cocotalk.application.ui.contacts;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocotalk.application.data.model.FriendRequest;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.FriendRepo;
import com.cocotalk.application.data.repository.UserRepo;
import com.cocotalk.application.utils.Constants;

public class AddFriendViewModel extends ViewModel {

    public enum RelationState {
        NONE,               // Chưa có quan hệ -> hiện nút "Kết bạn"
        SELF,               // Tài khoản đang đăng nhập
        PENDING_SENT,       // Đã gửi lời mời (status = pending, senderUid = currentUid) -> Bấm để "Thu hồi / Hủy lời mời"
        PENDING_RECEIVED,   // Đối phương đã gửi lời mời cho mình (status = pending, senderUid = targetUid) -> Bấm để "Đồng ý"
        ACCEPTED,           // Đã là bạn bè (status = accepted)
        BLOCKED             // Đã chặn (status = blocked)
    }

    private final AuthRepo authRepo = new AuthRepo();
    private final UserRepo userRepo = new UserRepo();
    private final FriendRepo friendRepo = new FriendRepo();

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<User> foundUser = new MutableLiveData<>();
    private final MutableLiveData<Boolean> userNotFound = new MutableLiveData<>(false);
    private final MutableLiveData<RelationState> relationState = new MutableLiveData<>(RelationState.NONE);
    private final MutableLiveData<Integer> statusToastResId = new MutableLiveData<>();

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<User> getFoundUser() { return foundUser; }
    public LiveData<Boolean> getUserNotFound() { return userNotFound; }
    public LiveData<RelationState> getRelationState() { return relationState; }
    public LiveData<Integer> getStatusToastResId() { return statusToastResId; }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void clearStatusToast() {
        statusToastResId.setValue(null);
    }

    public void searchByPhone(@NonNull String rawPhone) {
        isLoading.setValue(true);
        errorMessage.setValue(null);
        userNotFound.setValue(false);
        foundUser.setValue(null);

        userRepo.findUserByPhone(rawPhone, new UserRepo.UserCallback() {
            @Override
            public void onResult(User user) {
                if (user == null) {
                    isLoading.postValue(false);
                    userNotFound.postValue(true);
                    return;
                }

                String currentUid = authRepo.getCurrentUid();
                if (currentUid != null && currentUid.equals(user.getUid())) {
                    isLoading.postValue(false);
                    relationState.postValue(RelationState.SELF);
                    foundUser.postValue(user);
                    return;
                }

                if (currentUid == null) {
                    isLoading.postValue(false);
                    relationState.postValue(RelationState.NONE);
                    foundUser.postValue(user);
                    return;
                }

                friendRepo.getFriendRelation(currentUid, user.getUid(), new FriendRepo.FriendRelationCallback() {
                    @Override
                    public void onResult(FriendRequest relation) {
                        isLoading.postValue(false);
                        relationState.postValue(resolveRelationState(currentUid, relation));
                        foundUser.postValue(user);
                    }

                    @Override
                    public void onError(String message) {
                        isLoading.postValue(false);
                        relationState.postValue(RelationState.NONE);
                        foundUser.postValue(user);
                    }
                });
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }

    private RelationState resolveRelationState(String currentUid, FriendRequest relation) {
        if (relation == null || relation.getStatus() == null) {
            return RelationState.NONE;
        }
        String status = relation.getStatus();
        if (Constants.FRIEND_STATUS_ACCEPTED.equals(status)) {
            return RelationState.ACCEPTED;
        }
        if (Constants.FRIEND_STATUS_BLOCKED.equals(status)) {
            return RelationState.BLOCKED;
        }
        if (Constants.FRIEND_STATUS_PENDING.equals(status)) {
            // Nếu senderUid là currentUid hoặc senderUid trống nhưng ở phía người gửi
            if (currentUid.equals(relation.getSenderUid())) {
                return RelationState.PENDING_SENT;
            } else {
                return RelationState.PENDING_RECEIVED;
            }
        }
        return RelationState.NONE;
    }

    /**
     * - NONE: Gửi lời mời kết bạn 2 chiều
     * - PENDING_SENT: Tự hủy / thu hồi lời mời kết bạn đã gửi xóa 2 chiều
     * - PENDING_RECEIVED: Chấp nhận lời mời kết bạn cập nhật 2 chiều sang trạng thái accepted
     */
    public void onPrimaryActionClicked(int sentMsgResId, int canceledMsgResId, int acceptedMsgResId) {
        User target = foundUser.getValue();
        String currentUid = authRepo.getCurrentUid();
        RelationState currentState = relationState.getValue();
        if (target == null || target.getUid() == null || currentUid == null || currentState == null) {
            return;
        }

        if (currentState == RelationState.NONE) {
            isLoading.setValue(true);
            friendRepo.sendFriendRequest(currentUid, target.getUid(), new FriendRepo.CompleteCallback() {
                @Override
                public void onSuccess() {
                    isLoading.postValue(false);
                    relationState.postValue(RelationState.PENDING_SENT);
                    statusToastResId.postValue(sentMsgResId);
                }

                @Override
                public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });
        } else if (currentState == RelationState.PENDING_SENT) {
            // Người gửi tự hủy lời mời kết bạn đã gửi
            isLoading.setValue(true);
            friendRepo.declineOrCancelFriendRequest(currentUid, target.getUid(), new FriendRepo.CompleteCallback() {
                @Override
                public void onSuccess() {
                    isLoading.postValue(false);
                    relationState.postValue(RelationState.NONE);
                    statusToastResId.postValue(canceledMsgResId);
                }

                @Override
                public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });
        } else if (currentState == RelationState.PENDING_RECEIVED) {
            // Đối phương đã gửi lời mời -> bấm Đồng ý kết bạn
            isLoading.setValue(true);
            friendRepo.acceptFriendRequest(currentUid, target.getUid(), new FriendRepo.CompleteCallback() {
                @Override
                public void onSuccess() {
                    isLoading.postValue(false);
                    relationState.postValue(RelationState.ACCEPTED);
                    statusToastResId.postValue(acceptedMsgResId);
                }

                @Override
                public void onError(String message) {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
            });
        }
    }
}
