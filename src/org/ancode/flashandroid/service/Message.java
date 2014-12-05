package org.ancode.flashandroid.service;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class Message extends FlashBase implements Parcelable {

    @SerializedName("body")
    public String Body;

    public Message(Parcel in) {
        Body = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(Body);
    }

}
