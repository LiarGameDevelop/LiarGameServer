package com.game.liar.security.util;

public class JwtUtil {
    private static int UUIDLength = 36;

    public static String getRoomIdFromUUID(String s) {
        if (s.length() <= UUIDLength)
            throw new IllegalArgumentException("jwt token claims should be more than uuid size");
        return s.substring(0, UUIDLength);
    }

    public static String getUserIdFromUUID(String s) {
        if (s.length() <= UUIDLength)
            throw new IllegalArgumentException("jwt token claims should be more than uuid size");
        return s.substring(UUIDLength);
    }
}
