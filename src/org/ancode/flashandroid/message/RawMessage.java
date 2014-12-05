package org.ancode.flashandroid.message;

import com.google.gson.annotations.SerializedName;

public class RawMessage extends MessageBase {
    @SerializedName("nonce")
    public String Nonce;

    @SerializedName("body")
    public String Body;

}
