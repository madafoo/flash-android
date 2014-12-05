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
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.net.URI;

import org.ancode.flashandroid.json.FlashGsonBuilder;
import org.ancode.flashandroid.util.Curve25519Helper;
import org.ancode.flashandroid.util.Curve25519KeyPair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.neilalexander.jnacl.NaClNoPaddning;

public class FlashSubscriber implements Runnable {

    private final static String TAG = FlashSubscriber.class.getSimpleName();

    private final InetSocketAddress mServerAddress;

    private final Context mContext;

    private final String mHost;
    private final String mBaseUri;
    private String mChannelName;

    private ServerPubKey mServerPubKey;
    private Curve25519KeyPair mDynamicKeyPair = null;
    private Curve25519KeyPair mIdentityKeyPair = null;
    private String mPublishPubKey;

    private EventLoopGroup mBoss = null;

    private Bootstrap mBootstrap;

    public FlashSubscriber(final Context context, final String host,
            final int port) {
        mServerAddress = new InetSocketAddress(host, port);
        mHost = host;
        mContext = context;
        mBaseUri = "http://" + host + ":" + port;
        mChannelName = "default";

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

            pref = prefs.getString("sub_identity_key", null);
            if (!TextUtils.isEmpty(pref))
                mIdentityKeyPair = Curve25519Helper
                        .getKeyPairByPrivate(NaClNoPaddning.getBinary(pref));
            else {
                mIdentityKeyPair = Curve25519Helper.generateKeyPair();
                prefs.edit()
                        .putString("sub_identity_key",
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
        if (mDynamicKeyPair == null || mIdentityKeyPair == null
                || TextUtils.isEmpty(mPublishPubKey))
            return;

        try {
            mBootstrap = createTCPListeningPoint();
            Channel channel = mBootstrap.connect()
                    .addListener(mConnectListener).sync().channel();
            channel.closeFuture().await();
            channel = mBootstrap.connect().addListener(mSubscribeListener)
                    .sync().channel();
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
                    } else {
                        Log.w(TAG, "Another server public key got, drop it.");
                    }
                    ctx.channel().close();
                } else if (response instanceof Accept) {
                    Log.d(TAG, "Server longpull result: "
                            + ((Accept) response).Accepted);
                } else if (response instanceof Message) {
                    Message message = (Message) response;
                    Log.d(TAG, "Got flash message: " + message.Body);
                    sendBroadcast(message);
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

    private final void longpoll(final ChannelFuture future) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(mBaseUri);
        sb.append("/subscribe");
        // publish public key
        sb.append('/');
        sb.append(mPublishPubKey);
        // subscribe identity key
        sb.append('/');
        sb.append(mIdentityKeyPair.PublicKey);
        // channel want to poll
        sb.append('/');
        sb.append(mChannelName);
        // current time as NONCE
        sb.append('/');
        final String nonce = Curve25519Helper.generateNonce(System
                .currentTimeMillis());
        sb.append(nonce);
        // compute authentication
        sb.append('/');
        sb.append(getAuth(nonce));
        getRequest(future, URI.create(sb.toString()));
    }

    private final void getServerPubKeyRequest(final ChannelFuture future) {
        getRequest(future, URI.create(mBaseUri + "/server/pubkey"));
    }

    private final ChannelFutureListener mConnectListener = new ChannelFutureListener() {

        @Override
        public void operationComplete(final ChannelFuture future)
                throws Exception {
            if (future.isSuccess()) {
                // Connected, then update the server public key
                getServerPubKeyRequest(future);
            } else {
                Log.e(TAG, "Could not connect with flash server: "
                        + mServerAddress);
            }
        }
    };

    private final ChannelFutureListener mSubscribeListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future)
                throws Exception {
            if (future.isSuccess()) {
                // Connected, then begin long poll
                longpoll(future);
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

    public void setChannelName(final String channel) {
        if (TextUtils.isEmpty(channel))
            return;
        mChannelName = channel;

    }

    public void setPublisherPubkey(final String pubPubkey) {
        if (TextUtils.isEmpty(pubPubkey))
            return;
        mPublishPubKey = pubPubkey;
    }

    public final Curve25519KeyPair getIdentityKeyPair() {
        return mIdentityKeyPair;
    }

    private final void sendBroadcast(FlashBase msg) {
        Intent intent = new Intent(FlashService.BCAST_FLASH_MESSAGE);
        intent.putExtra(FlashService.FLASH_MESSAGE, msg);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

}
