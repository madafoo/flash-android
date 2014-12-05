package org.ancode.flashandroid.util;

import java.util.Locale;
import java.util.Random;

import com.neilalexander.jnacl.NaCl;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

public class Curve25519Helper {

    static public final Curve25519KeyPair generateKeyPair() {
        byte[] pk = new byte[32], sk = new byte[32];
        Random rand = new Random();
        rand.nextBytes(sk);

        curve25519xsalsa20poly1305.crypto_box_getpublickey(pk, sk);

        Curve25519KeyPair key = new Curve25519KeyPair();
        key.PublicKey = NaCl.asHex(pk);
        key.PrivateKey = NaCl.asHex(sk);

        return key;
    }

    static public final Curve25519KeyPair getKeyPairByPrivate(byte[] privKey) {
        if (privKey == null || privKey.length != 32)
            return null;

        byte[] pk = new byte[32];
        curve25519xsalsa20poly1305.crypto_box_getpublickey(pk, privKey);
        Curve25519KeyPair key = new Curve25519KeyPair();
        key.PublicKey = NaCl.asHex(pk);
        key.PrivateKey = NaCl.asHex(privKey);

        return key;
    }

    static public final String generateNonce(long value) {
        return String.format(Locale.getDefault(), "%024d", value);
    }

}
