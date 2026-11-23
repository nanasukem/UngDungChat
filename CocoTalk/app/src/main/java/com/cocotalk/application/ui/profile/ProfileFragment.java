package com.cocotalk.application.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.data.repository.UserRepo;
import com.cocotalk.application.databinding.FragmentProfileBinding;
import com.cocotalk.application.ui.auth.LoginActivity;
import com.cocotalk.application.utils.PhoneUtils;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private AuthRepo authRepo;
    private UserRepo userRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepo = new AuthRepo();
        userRepo = new UserRepo();

        loadCurrentUserProfile();

        binding.btnLogout.setOnClickListener(v -> performLogout());
    }

    private void loadCurrentUserProfile() {
        FirebaseUser currentUser = authRepo.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        if (currentUser.getPhoneNumber() != null) {
            binding.tvPhone.setText(PhoneUtils.formatDisplayPhone(currentUser.getPhoneNumber()));
        }

        userRepo.getOrMigrateUserForAuth(currentUser.getUid(), currentUser.getPhoneNumber(), new UserRepo.UserCallback() {
            @Override
            public void onResult(@Nullable User user) {
                if (binding == null || user == null) return;
                String name = user.getDisplayName() != null ? user.getDisplayName() : "";
                binding.tvDisplayName.setText(name);
                if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                    binding.tvPhone.setText(PhoneUtils.formatDisplayPhone(user.getPhone()));
                }
                if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty() && getContext() != null) {
                    binding.imgAvatar.setVisibility(View.VISIBLE);
                    binding.tvAvatarInitial.setVisibility(View.GONE);
                    Glide.with(getContext())
                            .load(user.getAvatarUrl())
                            .into(binding.imgAvatar);
                } else {
                    binding.imgAvatar.setVisibility(View.GONE);
                    binding.tvAvatarInitial.setVisibility(View.VISIBLE);
                    binding.tvAvatarInitial.setText(extractInitial(name));
                }
            }

            @Override
            public void onError(String message) {
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

    private void performLogout() {
        String uid = authRepo.getCurrentUid();
        if (uid != null) {
            userRepo.updateOnlineStatus(uid, false);
        }
        authRepo.signOut();

        if (getActivity() != null) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
