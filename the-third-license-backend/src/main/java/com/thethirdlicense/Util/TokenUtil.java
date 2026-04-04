package com.thethirdlicense.Util;
import java.security.SecureRandom;

public class TokenUtil {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static String generateSecureToken(int length) {
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return token.toString();
    }
}
