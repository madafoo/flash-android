package org.ancode.flashandroid.service;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class ServerPubKey extends FlashBase implements Parcelable {

    @SerializedName("serverpk")
    public String PublicKey;

    @Override
    public int describeContents() {
        return 0;
    }

    public ServerPubKey(Parcel in) {
        PublicKey = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeString(PublicKey);
    }
}
