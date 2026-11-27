package com.cocotalk.application.utils;

import android.content.Context;

import com.cocotalk.application.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Định dạng thời gian hiển thị trong danh sách chat và màn hình Chat. */
public final class TimeUtils {
    private TimeUtils() {}

    /** Danh sách chat: hôm nay "13:05", hôm qua "Hôm qua", cùng năm "12/11", khác năm "12/11/25". */
    public static String formatConversationTime(Context context, long millis) {
        if (millis <= 0) return "";
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(millis);

        if (isSameDay(now, t)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        }
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, t)) {
            return context.getString(R.string.time_yesterday);
        }
        String pattern = now.get(Calendar.YEAR) == t.get(Calendar.YEAR) ? "dd/MM" : "dd/MM/yy";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    /** Giờ dưới mỗi bong bóng tin nhắn: "13:05". */
    public static String formatMessageTime(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
