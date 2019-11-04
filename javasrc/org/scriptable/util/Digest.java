package org.scriptable.util;

import java.security.MessageDigest;
import java.math.BigInteger;

import java.security.NoSuchAlgorithmException;

public class Digest
{
    public static String md5Digest(String data) throws NoSuchAlgorithmException {
        return md5Digest(data.getBytes());
    }

    public static String md5Digest(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest m = MessageDigest.getInstance("MD5");
        byte[] digest = m.digest(data);
        return new BigInteger(1, digest).toString(10);
    }
}

