package com.cocotalk.application.ui.contacts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.cocotalk.application.R;
import com.cocotalk.application.data.model.User;
import com.cocotalk.application.databinding.ActivityAddFriendBinding;
import com.cocotalk.application.utils.LocaleHelper;
import com.cocotalk.application.utils.PhoneUtils;

public class AddFriendActivity extends AppCompatActivity {
    private ActivityAddFriendBinding binding;
    private AddFriendViewModel viewModel;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddFriendBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AddFriendViewModel.class);

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnSearch.setOnClickListener(v -> triggerSearch());

        binding.edtPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                triggerSearch();
                return true;
            }
            return false;
        });

        binding.btnAction.setOnClickListener(v -> viewModel.onPrimaryActionClicked(
                R.string.msg_friend_request_sent,
                R.string.msg_friend_request_canceled,
                R.string.msg_friend_request_accepted
        ));
    }

    private void triggerSearch() {
        String rawPhone = binding.edtPhone.getText() != null
                ? binding.edtPhone.getText().toString().trim()
                : "";

        if (!PhoneUtils.isValidPhone(rawPhone)) {
            binding.tilPhone.setError(getString(R.string.error_invalid_phone));
            return;
        }
        binding.tilPhone.setError(null);

        viewModel.searchByPhone(rawPhone);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isBusy = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
            binding.btnSearch.setEnabled(!isBusy);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getUserNotFound().observe(this, notFound -> {
            binding.tvNotFound.setVisibility(Boolean.TRUE.equals(notFound) ? View.VISIBLE : View.GONE);
        });

        viewModel.getFoundUser().observe(this, user -> {
            if (user == null) {
                binding.cardUserResult.setVisibility(View.GONE);
                return;
            }
            binding.cardUserResult.setVisibility(View.VISIBLE);
            bindUserCard(user);
        });

        viewModel.getRelationState().observe(this, this::updateActionButton);

        viewModel.getStatusToastResId().observe(this, resId -> {
            if (resId != null && resId != 0) {
                Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
                viewModel.clearStatusToast();
            }
        });
    }

    private void bindUserCard(User user) {
        String displayName = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                ? user.getDisplayName()
                : user.getPhone();
        binding.tvName.setText(displayName);
        binding.tvPhone.setText(PhoneUtils.formatDisplayPhone(user.getPhone()));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            binding.imgAvatar.setVisibility(View.VISIBLE);
            binding.tvAvatarInitial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(user.getAvatarUrl())
                    .into(binding.imgAvatar);
        } else {
            binding.imgAvatar.setVisibility(View.GONE);
            binding.tvAvatarInitial.setVisibility(View.VISIBLE);
            binding.tvAvatarInitial.setText(extractInitial(displayName));
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

    private void updateActionButton(AddFriendViewModel.RelationState state) {
        if (state == null) {
            state = AddFriendViewModel.RelationState.NONE;
        }
        int primaryColor = ContextCompat.getColor(this, R.color.md_primary);
        int onPrimaryColor = ContextCompat.getColor(this, R.color.md_on_primary);
        int errorContainerColor = ContextCompat.getColor(this, R.color.md_error_container);
        int onErrorContainerColor = ContextCompat.getColor(this, R.color.md_on_error_container);
        int surfaceHighColor = ContextCompat.getColor(this, R.color.md_surface_container_high);
        int onSurfaceVariantColor = ContextCompat.getColor(this, R.color.md_on_surface_variant);

        switch (state) {
            case NONE:
                binding.btnAction.setEnabled(true);
                binding.btnAction.setText(R.string.add_friend_btn_send_request);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
                binding.btnAction.setTextColor(onPrimaryColor);
                break;
            case PENDING_SENT:
                // Cho phép người gửi thu hồi lời mời kết bạn
                binding.btnAction.setEnabled(true);
                binding.btnAction.setText(R.string.add_friend_btn_cancel_request);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(errorContainerColor));
                binding.btnAction.setTextColor(onErrorContainerColor);
                break;
            case PENDING_RECEIVED:
                // Cho phép bấm Đồng ý kết bạn ngay
                binding.btnAction.setEnabled(true);
                binding.btnAction.setText(R.string.btn_accept);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
                binding.btnAction.setTextColor(onPrimaryColor);
                break;
            case ACCEPTED:
                binding.btnAction.setEnabled(false);
                binding.btnAction.setText(R.string.add_friend_status_already);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(surfaceHighColor));
                binding.btnAction.setTextColor(onSurfaceVariantColor);
                break;
            case SELF:
                binding.btnAction.setEnabled(false);
                binding.btnAction.setText(R.string.add_friend_status_self);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(surfaceHighColor));
                binding.btnAction.setTextColor(onSurfaceVariantColor);
                break;
            case BLOCKED:
                binding.btnAction.setEnabled(false);
                binding.btnAction.setText(R.string.add_friend_status_blocked);
                binding.btnAction.setBackgroundTintList(ColorStateList.valueOf(surfaceHighColor));
                binding.btnAction.setTextColor(onSurfaceVariantColor);
                break;
        }
    }
}
