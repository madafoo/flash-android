package org.ancode.flashandroid.json;

import org.ancode.flashandroid.service.Accept;
import org.ancode.flashandroid.service.FlashBase;
import org.ancode.flashandroid.service.Message;
import org.ancode.flashandroid.service.ServerPubKey;
import org.ancode.flashandroid.service.Time;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapter;

public class FlashGsonBuilder {

    final static private RuntimeTypeAdapter<FlashBase> flashAdapter = new RuntimeTypeAdapter<FlashBase>(
            FlashBase.class, "type");
    static {
        flashAdapter.registerSubtype(ServerPubKey.class);
        flashAdapter.registerSubtype(Accept.class);
        flashAdapter.registerSubtype(Time.class);
        flashAdapter.registerSubtype(Message.class);
    }

    public static Gson getGson() {
        return new GsonBuilder().registerTypeAdapter(FlashBase.class,
                flashAdapter).create();
    }

}
