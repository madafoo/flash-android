package org.ancode.flashandroid.service;

import android.os.Parcel;
import android.os.Parcelable;

public class Time extends FlashBase implements Parcelable {

    public long ServerTime;

    public Time(Parcel in) {
        ServerTime = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(ServerTime);
    }

}
