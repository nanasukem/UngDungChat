package com.cocotalk.application.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cocotalk.application.R;
import com.cocotalk.application.databinding.ActivityOtpBinding;
import com.cocotalk.application.ui.main.MainActivity;
import com.cocotalk.application.utils.LocaleHelper;
import com.cocotalk.application.utils.PhoneUtils;

public class OtpActivity extends AppCompatActivity {
    public static final String EXTRA_PHONE = "extra_phone";
    public static final String EXTRA_VERIFICATION_ID = "extra_verification_id";

    private ActivityOtpBinding binding;
    private AuthViewModel viewModel;
    private String normalizedPhone;
    private String currentVerificationId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOtpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        normalizedPhone = getIntent().getStringExtra(EXTRA_PHONE);
        currentVerificationId = getIntent().getStringExtra(EXTRA_VERIFICATION_ID);

        if (normalizedPhone == null) {
            normalizedPhone = "";
        }

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.tvSubtitle.setText(getString(
                R.string.otp_subtitle_format,
                PhoneUtils.formatDisplayPhone(normalizedPhone)
        ));

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnChangePhone.setOnClickListener(v -> finish());

        binding.btnVerifyOtp.setOnClickListener(v -> triggerVerifyOtp());

        binding.edtOtp.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                triggerVerifyOtp();
                return true;
            }
            return false;
        });

        binding.btnResendOtp.setOnClickListener(v -> {
            if (!normalizedPhone.isEmpty()) {
                viewModel.sendOtp(normalizedPhone, this, true);
                Toast.makeText(this, R.string.msg_otp_sent, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void triggerVerifyOtp() {
        String otp = binding.edtOtp.getText() != null
                ? binding.edtOtp.getText().toString().trim()
                : "";

        if (otp.length() != 6) {
            binding.tilOtp.setError(getString(R.string.error_invalid_otp));
            return;
        }
        binding.tilOtp.setError(null);

        if (currentVerificationId == null || currentVerificationId.isEmpty()) {
            return;
        }
        viewModel.verifyOtpCode(currentVerificationId, otp);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            boolean isBusy = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
            binding.btnVerifyOtp.setEnabled(!isBusy);
            binding.btnResendOtp.setEnabled(!isBusy);
            binding.edtOtp.setEnabled(!isBusy);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getVerificationIdLive().observe(this, newVerificationId -> {
            if (newVerificationId != null && !newVerificationId.isEmpty()) {
                currentVerificationId = newVerificationId;
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
