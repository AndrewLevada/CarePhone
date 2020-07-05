package com.andrewlevada.carephone.logic;

import android.telephony.SmsManager;

import com.andrewlevada.carephone.Config;
import com.google.android.gms.common.util.Base64Utils;

import java.util.List;

public class SyncSmsSender {
    public static final String smsPrefix = "cp_";

    private StringBuilder message;

    public void addWhitelist(List<PhoneNumber> whitelist) {
        if (whitelist.size() == 0) message.append(Config.smsSyncEmptyWhitelist);
        else for (PhoneNumber phoneNumber: whitelist) message
                .append(phoneNumber.getPhone().substring(1))
                .append(phoneNumber.getLabel()).append("_");
    }

    public void addWhitelistState(Boolean state) {
        message.append(state ? "1" : "0");
    }

    public void pack(String rUid) {
        message = new StringBuilder(StringXORer.encode(message.toString(), rUid));
    }

    public void send(String phone) {
        SmsManager.getDefault().sendTextMessage(phone, null, smsPrefix + message.toString(), null, null);
    }

    public SyncSmsSender() {
        message = new StringBuilder();
    }

    public static class StringXORer {

        public static String encode(String s, String key) {
            return base64Encode(xorWithKey(s.getBytes(), key.getBytes()));
        }

        public static String decode(String s, String key) {
            return new String(xorWithKey(base64Decode(s), key.getBytes()));
        }

        private static byte[] xorWithKey(byte[] a, byte[] key) {
            byte[] out = new byte[a.length];
            for (int i = 0; i < a.length; i++) {
                out[i] = (byte) (a[i] ^ key[i%key.length]);
            }
            return out;
        }

        private static byte[] base64Decode(String s) {
            return Base64Utils.decode(s);
        }

        private static String base64Encode(byte[] bytes) {
            return Base64Utils.encode(bytes).replaceAll("\\s", "");
        }
    }
}
