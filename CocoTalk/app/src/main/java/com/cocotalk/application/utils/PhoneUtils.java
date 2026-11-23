package com.cocotalk.application.utils;

public class PhoneUtils {

    /**
     * Chuẩn hóa số điện thoại về định dạng +84...
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }
        // Xóa khoảng trắng, dấu gạch ngang, dấu chấm, ngoặc đơn
        String cleaned = rawPhone.replaceAll("[\\s\\-\\.\\(\\)]", "");
        if (cleaned.isEmpty()) {
            return "";
        }
        if (cleaned.startsWith("+84")) {
            // Trường hợp nhập +84090... thì bỏ số 0 sau +84
            if (cleaned.startsWith("+840") && cleaned.length() > 4) {
                return "+84" + cleaned.substring(4);
            }
            return cleaned;
        }
        if (cleaned.startsWith("0")) {
            return "+84" + cleaned.substring(1);
        }
        if (cleaned.startsWith("84") && cleaned.length() >= 10) {
            return "+" + cleaned;
        }
        if (!cleaned.startsWith("+")) {
            return "+84" + cleaned;
        }
        return cleaned;
    }

    /**
     * Kiểm tra số điện thoại hợp lệ sau khi chuẩn hóa
     */
    public static boolean isValidPhone(String rawPhone) {
        String normalized = normalizePhone(rawPhone);
        return normalized.matches("^\\+84\\d{8,10}$");
    }

    /**
     * Định dạng số điện thoại
     */
    public static String formatDisplayPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        String local = phone;
        if (local.startsWith("+84")) {
            local = "0" + local.substring(3);
        }
        if (local.length() == 10) {
            return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7);
        }
        return phone;
    }
}
