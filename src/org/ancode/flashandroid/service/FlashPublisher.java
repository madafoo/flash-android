package org.ancode.flashandroid.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.ErrorDataEncoderException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map.Entry;

import org.ancode.flashandroid.json.FlashGsonBuilder;
import org.ancode.flashandroid.util.Curve25519Helper;
import org.ancode.flashandroid.util.Curve25519KeyPair;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.neilalexander.jnacl.NaClNoPaddning;

public class FlashPublisher implements Runnable {

    private final static String TAG = FlashPublisher.class.getSimpleName();

    private final InetSocketAddress mServerAddress;

    private final Context mContext;

    private final String mHost;
    private final String mBaseUri;
    private String mChannelName;
    private final String mPublishMessage;

    private ServerPubKey mServerPubKey;
    private Curve25519KeyPair mDynamicKeyPair = null;
    private Curve25519KeyPair mIdentityKeyPair = null;

    private EventLoopGroup mBoss = null;

    private Bootstrap mBootstrap;

    public FlashPublisher(final Context context, final String host,
            final int port, final String msg) {
        mServerAddress = new InetSocketAddress(host, port);
        mHost = host;
        mContext = context;
        mBaseUri = "http://" + host + ":" + port;
        mChannelName = "default";
        mPublishMessage = msg;

        SharedPreferences prefs = context.getSharedPreferences("FlashProtocol",
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

            pref = prefs.getString("pub_identity_key", null);
            if (!TextUtils.isEmpty(pref))
                mIdentityKeyPair = Curve25519Helper
                        .getKeyPairByPrivate(NaClNoPaddning.getBinary(pref));
            else {
                mIdentityKeyPair = Curve25519Helper.generateKeyPair();
                prefs.edit()
                        .putString("pub_identity_key",
                                mIdentityKeyPair.PrivateKey).commit();
            }
        } catch (Exception e) {
        }
    }

    private Bootstrap createTCPListeningPoint() {
        if (mBoss != null) {
            mBoss.shutdownGracefully();
        }
        final Bootstrap b = new Bootstrap();
        mBoss = new NioEventLoopGroup();

        b.group(mBoss).channel(NioSocketChannel.class)
                .remoteAddress(mServerAddress)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(final NioSocketChannel ch)
                            throws Exception {
                        final ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("http_encoder",
                                new HttpRequestEncoder());
                        pipeline.addLast("http_decoder",
                                new HttpResponseDecoder());
                        pipeline.addLast("handler",
                                new FlashConnectionHandler());
                    }
                });
        return b;
    }

    @Override
    public void run() {

        try {
            mBootstrap = createTCPListeningPoint();

            // Get the server public key
            Channel channel = mBootstrap.connect()
                    .addListener(mConnectListener).sync().channel();

            channel.closeFuture().await();
            channel = mBootstrap.connect().addListener(mPublishListener).sync()
                    .channel();
            channel.closeFuture().await();
        } catch (final Exception e) {
            e.printStackTrace();
            shutdown();
        }
    }

    private class FlashConnectionHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg)
                throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                System.out.println("CONTENT_TYPE:"
                        + response.headers()
                                .get(HttpHeaders.Names.CONTENT_TYPE));
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                ByteBuf buf = content.content();
                final String json = buf
                        .toString(io.netty.util.CharsetUtil.UTF_8);
                buf.release();
                Log.d(TAG, json);

                FlashBase response = null;
                try {
                    response = FlashGsonBuilder.getGson().fromJson(json,
                            FlashBase.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (response instanceof ServerPubKey) {
                    if (mServerPubKey == null) {
                        mServerPubKey = (ServerPubKey) response;
                        Log.d(TAG, "Server public key is: "
                                + mServerPubKey.PublicKey);
                        ctx.channel().close();
                    } else {
                        Log.w(TAG, "Another server public key got, drop it.");
                    }
                } else if (response instanceof Accept) {
                    Log.d(TAG, "Server longpull result: "
                            + ((Accept) response).Accepted);
                }
            }

        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext ctx)
                throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                final Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }

    public void shutdown() {
        if (mBoss != null) {
            mBoss.shutdownGracefully();
        }
    }

    private final String getAuth(final String nonce) throws Exception {
        NaClNoPaddning nacl = new NaClNoPaddning(mIdentityKeyPair.PrivateKey,
                mServerPubKey.PublicKey);
        return NaClNoPaddning.asHex(nacl.encrypt(mChannelName.getBytes(),
                nonce.getBytes()));
    }

    private final void publishMessage(ChannelFuture future, final String msg)
            throws Exception {
        if (mDynamicKeyPair == null || mIdentityKeyPair == null) {
            shutdown();
            return;
        }

        final long current = System.currentTimeMillis();
        final String nonce = Curve25519Helper.generateNonce(current);
        final String msg_nonce = Curve25519Helper.generateNonce(current + 1);
        ContentValues params = new ContentValues();
        params.put("from", mIdentityKeyPair.PublicKey);
        params.put("channel", mChannelName);
        params.put("nonce", nonce);
        params.put("auth", getAuth(nonce));

        final NaClNoPaddning nacl = new NaClNoPaddning(
                mDynamicKeyPair.PrivateKey, mDynamicKeyPair.PublicKey);
        final String enc = NaClNoPaddning.asHex(nacl.encrypt(msg.getBytes(),
                msg_nonce.getBytes()));

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"type\" : \"RawMessage\",\n");
        sb.append("  \"nonce\": \"");
        sb.append(msg_nonce);
        sb.append("\", \n");
        sb.append("  \"body\": \"");
        sb.append(enc);
        sb.append("\"\n");
        sb.append('}');

        params.put("msg", sb.toString());
        postRequest(future, URI.create(mBaseUri + "/publish"), params);
    }

    private final void getServerPubKeyRequest(final ChannelFuture future) {
        getRequest(future, URI.create(mBaseUri + "/server/pubkey"));
    }

    private final ChannelFutureListener mConnectListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future)
                throws Exception {
            if (future.isSuccess()) {
                // Connected, then begin long pull
                getServerPubKeyRequest(future);
            } else {
                Log.e(TAG, "Could not connect with flash server: "
                        + mServerAddress);
            }
        }
    };

    private final ChannelFutureListener mPublishListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future)
                throws Exception {
            if (future.isSuccess()) {
                // Connected
                publishMessage(future, mPublishMessage);
            } else {
                Log.e(TAG, "Could not connect with flash server: "
                        + mServerAddress);
            }
        }
    };

    private void getRequest(final ChannelFuture future, URI uri) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toASCIIString());

        request.headers().set(HttpHeaders.Names.HOST, mHost);
        request.headers().set(HttpHeaders.Names.CONNECTION,
                HttpHeaders.Values.KEEP_ALIVE);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH,
                request.content().readableBytes());

        future.channel().writeAndFlush(request);
    }

    private void postRequest(final ChannelFuture future, URI uri,
            ContentValues params) {
        if (params == null || params.size() == 0) {
            Log.e(TAG, "Could not request POST without parameters.");
            return;
        }

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, uri.toASCIIString());

        request.headers().set(HttpHeaders.Names.HOST, mHost);
        request.headers().set(HttpHeaders.Names.CONNECTION,
                HttpHeaders.Values.KEEP_ALIVE);

        // setup the factory: here using a mixed memory/disk based on size
        // threshold
        HttpDataFactory factory = new DefaultHttpDataFactory(
                DefaultHttpDataFactory.MINSIZE);

        // Use the PostBody encoder
        HttpPostRequestEncoder bodyRequestEncoder = null;
        try {
            bodyRequestEncoder = new HttpPostRequestEncoder(factory, request,
                    false); // false not multipart
        } catch (NullPointerException e) {
            // should not be since args are not null
            e.printStackTrace();
        } catch (ErrorDataEncoderException e) {
            // test if getMethod is a POST getMethod
            e.printStackTrace();
        }

        // add Form attribute
        try {
            for (Entry<String, Object> value : params.valueSet()) {
                bodyRequestEncoder.addBodyAttribute(value.getKey(), value
                        .getValue().toString());
            }
        } catch (NullPointerException e) {
            // should not be since not null args
            e.printStackTrace();
        } catch (ErrorDataEncoderException e) {
            // if an encoding error occurs
            e.printStackTrace();
        }

        // finalize request
        try {
            request = bodyRequestEncoder.finalizeRequest();
        } catch (ErrorDataEncoderException e) {
            // if an encoding error occurs
            e.printStackTrace();
        }

        future.channel().writeAndFlush(request);
    }

    public void setChannelName(final String channel) {
        if (TextUtils.isEmpty(channel))
            return;
        mChannelName = channel;
    }

    public final Curve25519KeyPair getIdentityKeyPair() {
        return mIdentityKeyPair;
    }

}
