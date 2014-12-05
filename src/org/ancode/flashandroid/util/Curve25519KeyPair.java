package org.ancode.flashandroid.util;

import com.neilalexander.jnacl.NaCl;

public final class Curve25519KeyPair {
    public String PublicKey;
    public String PrivateKey;

    public byte[] getPrivateKey() {
        if (PrivateKey != null) {
            return NaCl.getBinary(PrivateKey);
        } else
            return null;
    }

    public byte[] getPublicKey() {
        if (PublicKey != null) {
            return NaCl.getBinary(PublicKey);
        } else
            return null;
    }
}
