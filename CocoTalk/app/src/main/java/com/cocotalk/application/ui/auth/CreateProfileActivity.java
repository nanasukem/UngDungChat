package com.cocotalk.application.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cocotalk.application.R;
import com.cocotalk.application.data.repository.AuthRepo;
import com.cocotalk.application.databinding.ActivityCreateProfileBinding;
import com.cocotalk.application.ui.main.MainActivity;
import com.cocotalk.application.utils.LocaleHelper;
import com.cocotalk.application.utils.PhoneUtils;
import com.google.firebase.auth.FirebaseUser;

public class CreateProfileActivity extends AppCompatActivity {
    private ActivityCreateProfileBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        AuthRepo authRepo = new AuthRepo();
        FirebaseUser currentUser = authRepo.getCurrentUser();
        if (currentUser != null && currentUser.getPhoneNumber() != null) {
            String normalized = PhoneUtils.normalizePhone(currentUser.getPhoneNumber());
            binding.edtPhone.setText(normalized);
        }

        updateAvatarInitial("");

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.edtName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAvatarInitial(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSaveProfile.setOnClickListener(v -> triggerSaveProfile());

        binding.edtName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                triggerSaveProfile();
                return true;
            }
            return false;
        });
    }

    private void updateAvatarInitial(String name) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) {
            binding.tvAvatarInitial.setText("?");
        } else {
            String[] parts = trimmed.split("\\s+");
            String lastWord = parts[parts.length - 1];
            binding.tvAvatarInitial.setText(String.valueOf(Character.toUpperCase(lastWord.charAt(0))));
        }
    }

    private void triggerSaveProfile() {
        String displayName = binding.edtName.getText() != null
                ? binding.edtName.getText().toString().trim()
                : "";

        if (displayName.isEmpty()) {
            binding.tilName.setError(getString(R.string.error_empty_name));
            return;
        }
        binding.tilName.setError(null);

        String lang = LocaleHelper.getLanguage(this);
        viewModel.createOrUpdateProfile(displayName, lang);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isBusy = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
            binding.btnSaveProfile.setEnabled(!isBusy);
            binding.edtName.setEnabled(!isBusy);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getProfileSaved().observe(this, saved -> {
            if (Boolean.TRUE.equals(saved)) {
                Toast.makeText(this, R.string.msg_profile_saved, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}
