package com.cocotalk.application.utils;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.cocotalk.application.R;

/** Hiển thị avatar: ảnh thật nếu có, nếu không thì chữ cái đầu trên nền màu (màu cố định theo tên). */
public final class AvatarUtils {
    private AvatarUtils() {}

    private static final int[] BG = {
            R.color.avatar_bg_1, R.color.avatar_bg_2, R.color.avatar_bg_3,
            R.color.avatar_bg_4, R.color.avatar_bg_5
    };
    private static final int[] FG = {
            R.color.avatar_fg_1, R.color.avatar_fg_2, R.color.avatar_fg_3,
            R.color.avatar_fg_4, R.color.avatar_fg_5
    };

    public static void bind(TextView tvInitial, ImageView imgAvatar,
                            @Nullable String name, @Nullable String avatarUrl) {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            imgAvatar.setVisibility(View.VISIBLE);
            tvInitial.setVisibility(View.GONE);
            Glide.with(imgAvatar.getContext()).load(avatarUrl).into(imgAvatar);
            return;
        }
        imgAvatar.setVisibility(View.GONE);
        tvInitial.setVisibility(View.VISIBLE);
        int i = Math.abs(name != null ? name.hashCode() : 0) % BG.length;
        tvInitial.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(tvInitial.getContext(), BG[i])));
        tvInitial.setTextColor(ContextCompat.getColor(tvInitial.getContext(), FG[i]));
        tvInitial.setText(initialOf(name));
    }

    /** Lấy chữ cái đầu của TÊN (từ cuối cùng), VD "Phan Minh Thy" -> "T". */
    public static String initialOf(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        String last = parts[parts.length - 1];
        return String.valueOf(Character.toUpperCase(last.charAt(0)));
    }
}
