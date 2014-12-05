package org.ancode.flashandroid.json;

import org.ancode.flashandroid.message.RawMessage;
import org.ancode.flashandroid.message.MessageBase;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapter;

public class MessageGsonBuilder {
    final static private RuntimeTypeAdapter<MessageBase> messageAdapter = new RuntimeTypeAdapter<MessageBase>(
            MessageBase.class, "type");
    static {
        messageAdapter.registerSubtype(RawMessage.class);
    }

    public static Gson getGson() {
        return new GsonBuilder().registerTypeAdapter(MessageBase.class,
                messageAdapter).create();
    }

}
