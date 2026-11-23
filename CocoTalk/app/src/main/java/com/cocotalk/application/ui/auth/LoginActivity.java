package com.cocotalk.application.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cocotalk.application.R;
import com.cocotalk.application.databinding.ActivityLoginBinding;
import com.cocotalk.application.ui.main.MainActivity;
import com.cocotalk.application.utils.LocaleHelper;
import com.cocotalk.application.utils.PhoneUtils;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private String currentNormalizedPhone = "";
    private boolean isPhoneStepVisible = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupListeners();
        setupBackNavigation();
        observeViewModel();
    }

    private void setupListeners() {
        // Nút đổi ngôn ngữ hỗ trợ
        binding.btnLanguage.setOnClickListener(v -> showLanguagePicker());

        // Bấm "Đăng nhập"
        binding.btnWelcomeLogin.setOnClickListener(v -> showPhoneInputStep(false));

        // Bấm "Tạo tài khoản mới"
        binding.btnWelcomeRegister.setOnClickListener(v -> showPhoneInputStep(true));

        // Nút quay lại
        binding.toolbarPhone.setNavigationOnClickListener(v -> showWelcomeStep());

        binding.btnSendOtp.setOnClickListener(v -> triggerSendOtp());

        binding.edtPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                triggerSendOtp();
                return true;
            }
            return false;
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPhoneStepVisible) {
                    showWelcomeStep();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void showPhoneInputStep(boolean isRegisterMode) {
        isPhoneStepVisible = true;
        binding.layoutWelcome.setVisibility(View.GONE);
        binding.layoutPhoneStep.setVisibility(View.VISIBLE);

        binding.toolbarPhone.setTitle(isRegisterMode
                ? R.string.register_toolbar_title
                : R.string.login_toolbar_title);
        binding.tvTopBanner.setText(isRegisterMode
                ? R.string.register_banner_text
                : R.string.login_banner_text);

        binding.edtPhone.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.edtPhone, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void showWelcomeStep() {
        isPhoneStepVisible = false;
        binding.tilPhone.setError(null);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        binding.layoutPhoneStep.setVisibility(View.GONE);
        binding.layoutWelcome.setVisibility(View.VISIBLE);
    }

    private void showLanguagePicker() {
        String[] languages = {"Tiếng Việt", "English"};
        String currentLang = LocaleHelper.getLanguage(this);
        int checkedItem = "en".equals(currentLang) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setItems(languages, (dialog, which) -> {
                    String selectedCode = (which == 1) ? "en" : "vi";
                    if (!selectedCode.equals(currentLang)) {
                        LocaleHelper.setLocale(this, selectedCode);
                        recreate();
                    }
                })
                .show();
    }

    private void triggerSendOtp() {
        String rawPhone = binding.edtPhone.getText() != null
                ? binding.edtPhone.getText().toString().trim()
                : "";

        if (!PhoneUtils.isValidPhone(rawPhone)) {
            binding.tilPhone.setError(getString(R.string.error_invalid_phone));
            return;
        }
        binding.tilPhone.setError(null);

        currentNormalizedPhone = PhoneUtils.normalizePhone(rawPhone);
        viewModel.sendOtp(currentNormalizedPhone, this, false);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isBusy = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
            binding.btnSendOtp.setEnabled(!isBusy);
            binding.edtPhone.setEnabled(!isBusy);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getVerificationIdLive().observe(this, verificationId -> {
            if (verificationId != null && !verificationId.isEmpty()) {
                viewModel.clearVerificationId();
                Intent intent = new Intent(this, OtpActivity.class);
                intent.putExtra(OtpActivity.EXTRA_PHONE, currentNormalizedPhone);
                intent.putExtra(OtpActivity.EXTRA_VERIFICATION_ID, verificationId);
                startActivity(intent);
            }
        });

        viewModel.getHasExistingProfile().observe(this, hasProfile -> {
            if (hasProfile == null) return;
            Intent intent = new Intent(this, hasProfile ? MainActivity.class : CreateProfileActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
