package utils.okhttp.request;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import utils.okhttp.OkHttpUtils;
import utils.okhttp.callback.Callback;
import utils.okhttp.utils.Objects;

@SuppressWarnings({"unchecked", "unused"})
public abstract class OkHttpRequest {
    protected String url;
    protected Map<Class<?>, Object> tags;
    protected Headers headers;
    protected Callback callback;
    protected long connTimeOut;
    protected long writeTimeOut;
    protected long readTimeOut;

    protected Call call;
    protected final Builder builder;

    protected OkHttpRequest(OkHttpBuilder builder) {
        this.url = builder.url;
        this.tags = Util.immutableMap(builder.tags);
        this.headers = builder.headers.build();
        this.callback = builder.callback;
        this.connTimeOut = builder.connTimeOut;
        this.writeTimeOut = builder.writeTimeOut;
        this.readTimeOut = builder.readTimeOut;
        this.builder = new Builder().url(url).headers(headers);
        for (Entry<Class<?>, Object> entry : tags.entrySet()) {
            this.builder.tag((Class<? super Object>) entry.getKey(), entry.getValue());
        }
    }

    /**
     * 根据当前实例创建一个 {@link OkHttpBuilder} 对象
     */
    public abstract OkHttpBuilder newBuilder();

    /**
     * 返回当前请求的谓词
     */
    public abstract String method();

    protected abstract RequestBody buildRequestBody();

    protected abstract Request buildRequest(RequestBody requestBody);

    protected RequestBody wrapRequestBody(RequestBody requestBody) {
        return requestBody;
    }

    protected Request generateRequest() {
        RequestBody requestBody = buildRequestBody();
        RequestBody wrappedRequestBody = wrapRequestBody(requestBody);
        return buildRequest(wrappedRequestBody);
    }

    protected Call buildCall() {
        OkHttpClient okHttpClient = OkHttpUtils.getInstance().getOkHttpClient();
        if (connTimeOut > 0 || writeTimeOut > 0 || readTimeOut > 0) {
            connTimeOut = connTimeOut > 0 ? connTimeOut : okHttpClient.connectTimeoutMillis();
            writeTimeOut = writeTimeOut > 0 ? writeTimeOut : okHttpClient.writeTimeoutMillis();
            readTimeOut = readTimeOut > 0 ? readTimeOut : okHttpClient.readTimeoutMillis();
            return okHttpClient.newBuilder()
                    .connectTimeout(connTimeOut, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeOut, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeOut, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(generateRequest());
        }
        return okHttpClient.newCall(generateRequest());
    }

    /**
     * 返回当前实例的 URL 地址
     */
    public String url() {
        return url;
    }

    /**
     * 返回当前实例的 {@code tag}
     */
    public Object tag() {
        return call().request().tag();
    }

    /**
     * 返回当前实例的 {@code tag}
     */
    public <T> T tag(Class<? extends T> type) {
        return call().request().tag(type);
    }

    /**
     * 返回当前实例的 {@code headers}
     */
    public Headers headers() {
        return headers;
    }

    /**
     * 返回当前实例的 {@code callback}
     */
    public Callback callback() {
        return callback;
    }

    /**
     * 返回当前实例的连接超时，单位 ms
     */
    public long connTimeOut() {
        return connTimeOut;
    }

    /**
     * 返回当前实例的写入超时，单位 ms
     */
    public long writeTimeOut() {
        return writeTimeOut;
    }

    /**
     * 返回当前实例的读取超时，单位 ms
     */
    public long readTimeOut() {
        return readTimeOut;
    }

    /**
     * 返回当前实例的 {@link Call} 对象
     */
    public synchronized Call call() {
        if (Objects.nonNull(call)) {
            return call;
        }
        return (call = buildCall());
    }

    /**
     * 返回当前实例的 {@link Request} 对象
     */
    public Request request() {
        return call().request();
    }

    /**
     * 执行同步网络请求，并返回 {@link Response} 对象
     *
     * @throws IOException 由 {@link Call#execute()} 抛出的异常
     */
    public Response response() throws IOException {
        return call().execute();
    }

    /**
     * 执行异步网络请求，期间会调用 {@code callback} 的相关方法
     */
    public void execute() {
        if (call().isExecuted()) {
            throw new IllegalStateException("Already Executed");
        }
        callback.onBefore(request());
        execute(call(), callback);
    }

    /**
     * 取消本次请求
     */
    public void cancel() {
        call().cancel();
    }

    private static void sendFailResultCallback(final Call call, final Exception e, final Callback callback) {
        OkHttpUtils.getInstance().getThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(call, e);
                callback.onAfter();
            }
        });
    }

    private static <T> void sendSuccessResultCallback(final T object, final Callback<T> callback) {
        OkHttpUtils.getInstance().getThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    callback.onResponse(object);
                    callback.onAfter();
                } catch (Exception e) {
                    sendFailResultCallback(null, e, callback);
                }
            }
        });
    }

    private static void execute(Call call, final Callback callback) {
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                sendFailResultCallback(call, e, callback);
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                try {
                    if (call.isCanceled()) {
                        sendFailResultCallback(call, new IOException("Canceled"), callback);
                        return;
                    }
                    if (!response.isSuccessful()) {
                        sendFailResultCallback(call, new RuntimeException(response.message()), callback);
                        return;
                    }
                    sendSuccessResultCallback(callback.parseNetworkResponse(response), callback);
                } catch (Exception e) {
                    sendFailResultCallback(call, e, callback);
                } finally {
                    Util.closeQuietly(response.body());
                }
            }
        });
    }
}
