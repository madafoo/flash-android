package org.ancode.flashandroid.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

public class FlashService extends Service {

    private static final String TAG = FlashService.class.getSimpleName();

    public static final String BCAST_FLASH_MESSAGE = "org.ancode.flash.message";
    public static final String BCAST_FLASH_COMMAND = "org.ancode.flash.command";

    public static final String FLASH_MESSAGE = "message";
    public static final String FLASH_COMMAND = "command";

    public static final String SERVER_ADDRESS = "server_address";
    public static final String SERVER_PORT = "server_port";
    public static final String START_PUBLISHER = "start_publisher";
    public static final String PUBLISH_MESSAGE = "pub_msg";
    public static final String PUBLISH_IDENTITY = "pub_identity";
    public static final String CHANNEL = "channel";
    public static final String STARTSTOP_SUBSCRIBER = "startstop_subscriber";

    private final Map<String, Pair<FlashSubscriber, Thread>> mFlashSubscribers = new HashMap<String, Pair<FlashSubscriber, Thread>>();

    @Override
    public void onCreate() {
        final IntentFilter localfilter = new IntentFilter(BCAST_FLASH_COMMAND);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mCommandReceiver, localfilter);

        super.onCreate();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
            final int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                mCommandReceiver);

        for (Entry<String, Pair<FlashSubscriber, Thread>> item : mFlashSubscribers
                .entrySet()) {
            Pair<FlashSubscriber, Thread> subpair = item.getValue();
            subpair.first.shutdown();
            try {
                subpair.second.join(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context c, final Intent i) {
            final String action = i.getAction();
            if (BCAST_FLASH_COMMAND.equals(action)) {
                final String command = i.getStringExtra(FLASH_COMMAND);
                if (START_PUBLISHER.equals(command)) {
                    final String message = i.getStringExtra(PUBLISH_MESSAGE);
                    final String channel = i.getStringExtra(CHANNEL);
                    final String server_addr = i.getStringExtra(SERVER_ADDRESS);
                    final int server_port = i.getIntExtra(SERVER_PORT, 8080);
                    if (!TextUtils.isEmpty(message)
                            && !TextUtils.isEmpty(channel)
                            && !TextUtils.isEmpty(server_addr)) {
                        FlashPublisher pub = new FlashPublisher(
                                getApplicationContext(), server_addr,
                                server_port, message);
                        pub.setChannelName(channel);

                        final Thread publisherThread = new Thread(pub);
                        publisherThread.start();
                    } else {
                        Log.w(TAG, "channel: " + channel + " message: "
                                + message);
                    }
                } else if (STARTSTOP_SUBSCRIBER.equals(command)) {
                    final String channel = i.getStringExtra(CHANNEL);
                    final String pub_identity = i
                            .getStringExtra(PUBLISH_IDENTITY);
                    final String server_addr = i.getStringExtra(SERVER_ADDRESS);
                    final int server_port = i.getIntExtra(SERVER_PORT, 8080);
                    if (!TextUtils.isEmpty(channel)
                            && !TextUtils.isEmpty(pub_identity)
                            && !TextUtils.isEmpty(server_addr)) {
                        Pair<FlashSubscriber, Thread> subpair = mFlashSubscribers
                                .get(channel);
                        if (subpair == null || !subpair.second.isAlive()) {
                            FlashSubscriber sub = new FlashSubscriber(
                                    getApplicationContext(), server_addr,
                                    server_port);
                            sub.setPublisherPubkey(pub_identity);
                            sub.setChannelName(channel);
                            Thread subThread = new Thread(sub);
                            subThread.start();
                            Pair<FlashSubscriber, Thread> pair = new Pair<FlashSubscriber, Thread>(
                                    sub, subThread);
                            mFlashSubscribers.put(pub_identity, pair);
                        }
                    } else {
                        Log.w(TAG, "channel: " + channel + " id: "
                                + pub_identity);
                    }
                }
            }
        }
    };

}
