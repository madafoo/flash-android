package org.ancode.flashandroid.service;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class Accept extends FlashBase implements Parcelable {

    @SerializedName("value")
    public boolean Accepted;

    public Accept(Parcel in) {
        Accepted = in.readInt() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(Accepted ? 1 : 0);
    }

}
