package org.ancode.flashandroid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.ancode.flashandroid.json.MessageGsonBuilder;
import org.ancode.flashandroid.message.MessageBase;
import org.ancode.flashandroid.message.RawMessage;
import org.ancode.flashandroid.service.FlashService;
import org.ancode.flashandroid.util.Curve25519Helper;
import org.ancode.flashandroid.util.Curve25519KeyPair;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.neilalexander.jnacl.NaClNoPaddning;

public class MainActivity extends Activity {

    private Button mBtnPublish;
    private Button mBtnSubscribe;
    private EditText mPublishMessage;
    private EditText mServerAddress;
    private EditText mServerPort;
    private ListView mMesssageList;
    private final List<String> mMessages = new ArrayList<String>();
    private ArrayAdapter<String> mMessageAdapter = null;
    private Curve25519KeyPair mDynamicKeyPair = null;
    private Curve25519KeyPair mPublishKeyPair = null;

    private final int MSG_UPDATE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Intent intent = new Intent(this, FlashService.class);
        startService(intent);

        initviews();
    }

    private void initviews() {
        readDynamicKey();
        readPublishKey();

        mPublishMessage = (EditText) findViewById(R.id.pubMessage);
        mServerAddress = (EditText) findViewById(R.id.serverAddress);
        mServerPort = (EditText) findViewById(R.id.serverPort);

        mBtnPublish = (Button) findViewById(R.id.btnPublish);
        mBtnPublish.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(mPublishMessage.getText().toString())) {
                    Toast.makeText(getApplicationContext(),
                            "Need message input.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(mServerAddress.getText().toString())) {
                    Toast.makeText(getApplicationContext(),
                            "Need FlashServer address.", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                int port;
                try {
                    port = Integer.parseInt(mServerPort.getText().toString());
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(),
                            "Need FlashServer port.", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                saveServerSettings();
                Intent intent = new Intent(FlashService.BCAST_FLASH_COMMAND);
                intent.putExtra(FlashService.FLASH_COMMAND,
                        FlashService.START_PUBLISHER);
                intent.putExtra(FlashService.PUBLISH_MESSAGE, mPublishMessage
                        .getText().toString());
                intent.putExtra(FlashService.CHANNEL, "default");
                intent.putExtra(FlashService.SERVER_ADDRESS, mServerAddress
                        .getText().toString());
                intent.putExtra(FlashService.SERVER_PORT, port);
                LocalBroadcastManager.getInstance(getApplicationContext())
                        .sendBroadcast(intent);

            }
        });
        mBtnSubscribe = (Button) findViewById(R.id.btnSubscribe);
        mBtnSubscribe.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(mServerAddress.getText().toString())) {
                    Toast.makeText(getApplicationContext(),
                            "Need FlashServer address.", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                int port;
                try {
                    port = Integer.parseInt(mServerPort.getText().toString());
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(),
                            "Need FlashServer port.", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                saveServerSettings();
                Intent intent = new Intent(FlashService.BCAST_FLASH_COMMAND);
                intent.putExtra(FlashService.FLASH_COMMAND,
                        FlashService.STARTSTOP_SUBSCRIBER);
                intent.putExtra(FlashService.PUBLISH_IDENTITY,
                        mPublishKeyPair.PublicKey);
                intent.putExtra(FlashService.CHANNEL, "default");
                intent.putExtra(FlashService.SERVER_ADDRESS, mServerAddress
                        .getText().toString());
                intent.putExtra(FlashService.SERVER_PORT, port);
                LocalBroadcastManager.getInstance(getApplicationContext())
                        .sendBroadcast(intent);
            }
        });

        mMesssageList = (ListView) findViewById(R.id.listMessages);
        mMessageAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mMessages);
        mMesssageList.setAdapter(mMessageAdapter);

        storeServerSettings();

        final IntentFilter localfilter = new IntentFilter(
                FlashService.BCAST_FLASH_MESSAGE);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, localfilter);

    }

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context c, final Intent i) {
            final String action = i.getAction();
            if (FlashService.BCAST_FLASH_MESSAGE.equals(action)) {
                try {
                    NaClNoPaddning nacl = new NaClNoPaddning(
                            mDynamicKeyPair.PrivateKey,
                            mDynamicKeyPair.PublicKey);
                    org.ancode.flashandroid.service.Message message = i
                            .getParcelableExtra(FlashService.FLASH_MESSAGE);
                    RawMessage realMessage = null;
                    try {
                        realMessage = (RawMessage) MessageGsonBuilder.getGson()
                                .fromJson(message.Body, MessageBase.class);
                        String origMessage = new String(nacl.decrypt(
                                NaClNoPaddning.getBinary(realMessage.Body),
                                realMessage.Nonce.getBytes()));
                        Message msg = mMessageHandler.obtainMessage(MSG_UPDATE);
                        msg.obj = origMessage;
                        mMessageHandler.sendMessage(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void readDynamicKey() {
        SharedPreferences prefs = getSharedPreferences("FlashProtocol",
                Context.MODE_PRIVATE);
        try {
            String pref = prefs.getString("dynamic_key", null);
            if (!TextUtils.isEmpty(pref))
                mDynamicKeyPair = Curve25519Helper
                        .getKeyPairByPrivate(NaClNoPaddning.getBinary(pref));
            else {
                // just for demo, do not generate dynamic key for productive
                // environment
                mDynamicKeyPair = Curve25519Helper.generateKeyPair();
                prefs.edit()
                        .putString("dynamic_key", mDynamicKeyPair.PrivateKey)
                        .commit();
            }
        } catch (Exception e) {
        }
    }

    private void readPublishKey() {
        SharedPreferences prefs = getSharedPreferences("FlashProtocol",
                Context.MODE_PRIVATE);
        String pref = prefs.getString("pub_identity_key", null);
        if (!TextUtils.isEmpty(pref))
            mPublishKeyPair = Curve25519Helper
                    .getKeyPairByPrivate(NaClNoPaddning.getBinary(pref));
        else {
            mPublishKeyPair = Curve25519Helper.generateKeyPair();
            prefs.edit()
                    .putString("pub_identity_key", mPublishKeyPair.PrivateKey)
                    .commit();
        }

    }

    static class MessageHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        MessageHandler(MainActivity act) {
            mActivity = new WeakReference<MainActivity>(act);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity act = mActivity.get();
            if (act != null) {
                act.handleMessage(msg);
            }
        }
    }

    private void saveServerSettings() {
        SharedPreferences prefs = getSharedPreferences("ServerSettings",
                Context.MODE_PRIVATE);
        prefs.edit()
                .putString("serverAddress", mServerAddress.getText().toString())
                .commit();
        prefs.edit().putString("serverPort", mServerPort.getText().toString())
                .commit();
    }

    private void storeServerSettings() {
        SharedPreferences prefs = getSharedPreferences("ServerSettings",
                Context.MODE_PRIVATE);
        mServerAddress.setText(prefs.getString("serverAddress", null));
        mServerPort.setText(prefs.getString("serverPort", "8080"));
    }

    private final MessageHandler mMessageHandler = new MessageHandler(this);

    private void handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_UPDATE:
            String received = (String) msg.obj;
            mMessages.add(received);
            mMessageAdapter.notifyDataSetChanged();
            break;
        }

    }

}
