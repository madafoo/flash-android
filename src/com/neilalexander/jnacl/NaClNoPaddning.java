package com.neilalexander.jnacl;

public class NaClNoPaddning extends NaCl {

    public NaClNoPaddning(byte[] privatekey, byte[] publickey) throws Exception {
        super(privatekey, publickey);
    }

    public NaClNoPaddning(String privateKey, String publicKey) throws Exception {
        super(privateKey, publicKey);
    }

    private byte[] strip(byte[] paddedinput) {
        byte[] output = new byte[paddedinput.length
                - crypto_secretbox_BOXZEROBYTES];
        System.arraycopy(paddedinput, crypto_secretbox_BOXZEROBYTES, output, 0,
                paddedinput.length - crypto_secretbox_BOXZEROBYTES);
        return output;
    }

    private byte[] padding(byte[] input) {
        byte[] paddedout = new byte[input.length
                + crypto_secretbox_BOXZEROBYTES];
        for (int i = 0; i < crypto_secretbox_BOXZEROBYTES; i++)
            paddedout[i] = 0;
        System.arraycopy(input, 0, paddedout, crypto_secretbox_BOXZEROBYTES,
                input.length);
        return paddedout;
    }

    @Override
    public byte[] encrypt(byte[] input, byte[] nonce) {
        return strip(super.encrypt(input, nonce));
    }

    @Override
    public byte[] encrypt(byte[] input, int inputlength, byte[] nonce) {
        return strip(super.encrypt(input, inputlength, nonce));
    }

    @Override
    public byte[] decrypt(byte[] input, byte[] nonce) {
        return super.decrypt(padding(input), nonce);
    }

    @Override
    public byte[] decrypt(byte[] input, int inputlength, byte[] nonce) {
        return super.decrypt(padding(input), inputlength
                + crypto_secretbox_BOXZEROBYTES, nonce);
    }

}
