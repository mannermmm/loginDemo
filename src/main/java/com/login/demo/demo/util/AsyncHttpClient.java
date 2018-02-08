package com.login.demo.demo.util;

import com.google.gson.Gson;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Http Client, 异步实现，基于 Apache HttpAsyncClient
 *
 * @author zoucj
 * @since 1.0.0 20170614
 */
@Component
public class AsyncHttpClient implements HttpClient {
    private static int maxConnections = 100;
    private static int maxConnectionsPerRoute = 20;
    private int socketTimeout = 10000;
    private int connectTimeout = 10000;
    private int maxRetry = 1;

    private static final String ENCODING = "utf-8";

    private static CloseableHttpAsyncClient httpAsyncClient = null;

    private static Logger logger = LoggerFactory.getLogger(AsyncHttpClient.class);

    public AsyncHttpClient() {
        initConnectionPool();
    }

    public AsyncHttpClient(int socketTimeout, int connectTimeout) {
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        initConnectionPool();
    }

    public AsyncHttpClient(int socketTimeout, int connectTimeout, int maxRetry, int maxConnections, int maxConnectionsPerRoute) {
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.maxRetry = maxRetry;
        this.maxConnections = maxConnections;
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        initConnectionPool();
    }

    public AsyncHttpClient(int socketTimeout, int connectTimeout, int maxConnections, int maxConnectionsPerRoute) {
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.maxConnections = maxConnections;
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        initConnectionPool();
    }

    @Override
    public String httpGetJson(String requestUrl) {
        HttpResponse response;
        try {
            response = getAsync(requestUrl);
            return getDataFromResponse(requestUrl, response);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(String.format("get operation failed in getting %s, cause: %s",
                    requestUrl, e.getMessage()));
        }
    }

    @Override
    public HttpResponse httpGetJsontest1(String requestUrl) {
        HttpResponse response;
        try {
            response = getAsync(requestUrl);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(String.format("get operation failed in getting %s, cause: %s",
                    requestUrl, e.getMessage()));
        }
    }




    @Override
    public HttpResponse httpPost1(String requestUrl, Map<String, String> data, Header[] headers) {
        HttpResponse response;
        try {
            response = postAsync1(requestUrl, data, ENCODING, headers);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(String.format("post operation failed in getting %s, cause: %s",
                    requestUrl, e.getMessage()));
        }
    }

    /**
     * 创建连接池
     */
    public void initConnectionPool() {
        try {
            logger.info("initializing connection pool for async http client");
            ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
            PoolingNHttpClientConnectionManager pool = new PoolingNHttpClientConnectionManager(ioReactor);

            pool.setMaxTotal(maxConnections);
            pool.setDefaultMaxPerRoute(maxConnectionsPerRoute);
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(socketTimeout)
                    .setConnectTimeout(connectTimeout).setRedirectsEnabled(true).setCircularRedirectsAllowed(true)
                    .build();

            httpAsyncClient = HttpAsyncClients.custom()
                    .setConnectionManager(pool)
                    .setDefaultRequestConfig(requestConfig).setRedirectStrategy(new DefaultRedirectStrategy() {
                        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                            boolean isRedirect = false;
                            try {
                                isRedirect = super.isRedirected(request, response, context);
                            } catch (ProtocolException e) {
                                e.printStackTrace();
                            }
                            if (!isRedirect) {
                                int responseCode = response.getStatusLine().getStatusCode();
                                if (responseCode == 301 || responseCode == 302) {
                                    return true;
                                }
                            }
                            return isRedirect;
                        }
                    })
                    .build();
            CookieManager cookieManager = new CookieManager();
        } catch (IOReactorException e) {
            logger.error("initializing connection pool failed, cause: {}", e.getMessage());
        }
        httpAsyncClient.start();
        logger.info("connection pool for async http client has been initialized successfully");
    }

    private HttpResponse getAsync(String requestUrl) throws ExecutionException, InterruptedException {
        final HttpGet httpGet = new HttpGet(requestUrl);
        final CountDownLatch latch = new CountDownLatch(1);
        Future<HttpResponse> response = httpAsyncClient.execute(httpGet, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse result) {
                logger.debug("api ({}) has been called successfully", requestUrl);
                latch.countDown();
            }

            @Override
            public void failed(Exception ex) {
                if (getRetryTimes() <= 0) {
                    logger.info("call api ({}) failed, no retries left", requestUrl);
                    latch.countDown();
                } else {
                    logger.info("call api ({}) failed, {} retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpGet, this);
                }
            }

            @Override
            public void cancelled() {
                latch.countDown();
            }
        });
        latch.await(socketTimeout * (1 + maxRetry) + 1000, TimeUnit.MILLISECONDS);
        return response.get();
    }

    private HttpResponse getAsync1(String requestUrl, Header[] headers) throws ExecutionException, InterruptedException {
        final HttpGet httpGet = new HttpGet(requestUrl);
        final CountDownLatch latch = new CountDownLatch(1);
        for (Header header : headers) {
            httpGet.addHeader(header);
        }
        Future<HttpResponse> response = httpAsyncClient.execute(httpGet, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse result) {
                logger.debug("api ({}) has been called successfully", requestUrl);
                latch.countDown();
            }

            @Override
            public void failed(Exception ex) {
                if (getRetryTimes() <= 0) {
                    logger.info("call api ({}) failed, no retries left", requestUrl);
                    latch.countDown();
                } else {
                    logger.info("call api ({}) failed, {} retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpGet, this);
                }
            }

            @Override
            public void cancelled() {
                latch.countDown();
            }
        });
        latch.await(socketTimeout * (1 + maxRetry) + 1000, TimeUnit.MILLISECONDS);
        return response.get();
    }

    /**
     * 批量调用 http get
     */
    private Map<String, HttpResponse> getAsync(Map<String, String> requests) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(requests.size());
        final Map<String, HttpResponse> responses = new ConcurrentHashMap<>();
        for (final Map.Entry<String, String> request : requests.entrySet()) {
            final HttpGet httpGet = new HttpGet(request.getValue());
            httpAsyncClient.execute(httpGet, new RetryableFutureCallback(maxRetry) {
                @Override
                public void completed(HttpResponse result) {
                    logger.debug("api {} has been called successfully", request.getValue());
                    responses.put(request.getKey(), result);
                    latch.countDown();
                }

                @Override
                public void failed(Exception ex) {
                    if (getRetryTimes() <= 0) {
                        logger.info("call api {} failed, no retries left", request.getValue());
                        latch.countDown();
                    } else {
                        logger.info("call api {} failed, {} retries left, will retry", request.getValue(), getRetryTimes());
                        setRetryTimes(getRetryTimes() - 1);
                        httpAsyncClient.execute(httpGet, this);
                    }
                }

                @Override
                public void cancelled() {
                    latch.countDown();
                }
            });
        }
        latch.await(socketTimeout * (1 + maxRetry) + 1000, TimeUnit.MILLISECONDS);
        return responses;
    }

    /**
     * 批量调用 http post
     */
    private Map<String, HttpResponse> postAsyncImages(String url, Set<String> originalUrls) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(originalUrls.size());
        final Map<String, HttpResponse> responses = new ConcurrentHashMap<>();
        for (String originalUrl : originalUrls) {
            HttpPost httpPost = new HttpPost(url);
            List<NameValuePair> list = new ArrayList<>();
            list.add(new BasicNameValuePair("url", originalUrl));
            final HttpEntity entity = new UrlEncodedFormEntity(list, Charset.forName(ENCODING));
            httpPost.setEntity(entity);
            httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
                @Override
                public void completed(HttpResponse result) {
                    logger.debug("api {} has been called successfully", originalUrl);
                    responses.put(originalUrl, result);
                    latch.countDown();
                }

                @Override
                public void failed(Exception ex) {
                    if (getRetryTimes() <= 0) {
                        logger.info("call api {} failed, no retries left", originalUrl);
                        latch.countDown();
                    } else {
                        logger.info("call api {} failed, {} retries left, will retry", originalUrl, getRetryTimes());
                        setRetryTimes(getRetryTimes() - 1);
                        httpAsyncClient.execute(httpPost, this);
                    }
                }

                @Override
                public void cancelled() {
                    latch.countDown();
                }
            });
        }
        latch.await(socketTimeout * (1 + maxRetry) + 1000, TimeUnit.MILLISECONDS);
        return responses;
    }

    private HttpResponse postAsync(String requestUrl, Map<String, String> data, String encoding) throws ExecutionException, InterruptedException {
        final HttpPost httpPost = new HttpPost(requestUrl);
        List<NameValuePair> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        final HttpEntity entity = new UrlEncodedFormEntity(list, Charset.forName(encoding));
        httpPost.setEntity(entity);
        Future<HttpResponse> response = httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse httpResponse) {
                logger.debug("POST api {} successfully", requestUrl);
            }

            @Override
            public void failed(Exception e) {
                if (getRetryTimes() <= 0) {
                    logger.info("POST api {} failed, no retries left", requestUrl);
                } else {
                    logger.info(" POST api {} failed, {}  retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpPost, this);
                }
            }

            @Override
            public void cancelled() {

            }
        });
        return response.get();
    }

    private HttpResponse postAsync1(String requestUrl, Map<String, String> data, String encoding, Header[] headers) throws ExecutionException, InterruptedException {
        final HttpPost httpPost = new HttpPost(requestUrl);
        List<NameValuePair> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        final HttpEntity entity = new UrlEncodedFormEntity(list, Charset.forName(encoding));
        httpPost.setEntity(entity);
        for (Header header : headers) {
            httpPost.addHeader(header);
        }
        Future<HttpResponse> response = httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse httpResponse) {
                logger.debug("POST api {} successfully", requestUrl);
            }

            @Override
            public void failed(Exception e) {
                if (getRetryTimes() <= 0) {
                    logger.info("POST api {} failed, no retries left", requestUrl);
                } else {
                    logger.info(" POST api {} failed, {}  retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpPost, this);
                }
            }

            @Override
            public void cancelled() {

            }
        });
        return response.get();
    }

    // TODO 优化
    private HttpResponse postAsyncImage(String requestUrl, Map<String, String> data, String encoding) throws ExecutionException, InterruptedException {
        final HttpPost httpPost = new HttpPost(requestUrl);
        List<NameValuePair> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        final HttpEntity entity = new UrlEncodedFormEntity(list, Charset.forName(encoding));
        httpPost.setEntity(entity);
        Future<HttpResponse> response = httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse httpResponse) {
                logger.debug("POST api {} successfully", requestUrl);
            }

            @Override
            public void failed(Exception e) {
                if (getRetryTimes() <= 0) {
                    logger.info("POST api {} failed url {}, no retries left", requestUrl, data.get("url"));
                } else {
                    logger.info(" POST api {} failed url {}, {}  retries left, will retry", requestUrl, data.get("url"), getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpPost, this);
                }
            }

            @Override
            public void cancelled() {

            }
        });
        return response.get();
    }

    private HttpResponse postAsyncWithHeaders(String requestUrl, Map<String, String> data, Map<String, String> headers, String encoding) throws ExecutionException, InterruptedException {
        final HttpPost httpPost = new HttpPost(requestUrl);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                httpPost.addHeader(header.getKey(), header.getValue());
            }
        }
        List<NameValuePair> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        final HttpEntity entity = new UrlEncodedFormEntity(list, Charset.forName(encoding));
        httpPost.setEntity(entity);
        Future<HttpResponse> response = httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse httpResponse) {
                logger.debug("POST api {} successfully", requestUrl);
            }

            @Override
            public void failed(Exception e) {
                if (getRetryTimes() <= 0) {
                    logger.info("POST api {} failed, no retries left", requestUrl);
                } else {
                    logger.info(" POST api {} failed,{}  retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpPost, this);
                }
            }

            @Override
            public void cancelled() {

            }
        });
        return response.get();
    }

    private HttpResponse postJsonAsync(String requestUrl, Map<String, String> data, String encoding) throws ExecutionException, InterruptedException {
        final HttpPost httpPost = new HttpPost(requestUrl);
        Gson gson = new Gson();
        final HttpEntity entity = new StringEntity(gson.toJson(data), Charset.forName(encoding));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(entity);
        Future<HttpResponse> response = httpAsyncClient.execute(httpPost, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse httpResponse) {
                logger.debug("POST api {} successfully", requestUrl);
            }

            @Override
            public void failed(Exception e) {
                if (getRetryTimes() <= 0) {
                    logger.info("POST api {} failed, no retries left", requestUrl);
                } else {
                    logger.info(" POST api {} failed,{}  retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpPost, this);
                }
            }

            @Override
            public void cancelled() {

            }
        });
        return response.get();
    }

    private HttpResponse deleteAsync(String requestUrl, Map<String, String> headers) throws ExecutionException, InterruptedException {
        final HttpDelete httpDelete = new HttpDelete(requestUrl);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                httpDelete.addHeader(header.getKey(), header.getValue());
            }
        }
        final CountDownLatch latch = new CountDownLatch(1);
        Future<HttpResponse> response = httpAsyncClient.execute(httpDelete, new RetryableFutureCallback(maxRetry) {
            @Override
            public void completed(HttpResponse result) {
                logger.debug("api ({}) has been called successfully", requestUrl);
                latch.countDown();
            }

            @Override
            public void failed(Exception ex) {
                if (getRetryTimes() <= 0) {
                    logger.info("call api ({}) failed, no retries left", requestUrl);
                    latch.countDown();
                } else {
                    logger.info("call api ({}) failed, {} retries left, will retry", requestUrl, getRetryTimes());
                    setRetryTimes(getRetryTimes() - 1);
                    httpAsyncClient.execute(httpDelete, this);
                }
            }

            @Override
            public void cancelled() {
                latch.countDown();
            }
        });
        latch.await(socketTimeout * (1 + maxRetry) + 1000, TimeUnit.MILLISECONDS);
        return response.get();
    }


    public abstract class RetryableFutureCallback implements FutureCallback<HttpResponse> {
        private int retryTimes;

        public RetryableFutureCallback(int retryTimes) {
            this.retryTimes = retryTimes;
        }

        public int getRetryTimes() {
            return retryTimes;
        }

        public void setRetryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
        }
    }

    /**
     * 从 Response 中截取数据
     */
    public String getDataFromResponse(String url, HttpResponse response) {
        if (response == null || response.getStatusLine() == null) {
            throw new RuntimeException();
        } else if (response.getStatusLine().getStatusCode() == (HttpStatus.SC_OK)) {
            InputStream in;
            String result;
            HttpEntity entity = response.getEntity();
            try {
                in = entity.getContent();
                result = readString(in, ENCODING);
                EntityUtils.consume(entity);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(String.format("get operation failed in getting %s, cause: %s", url, e.getMessage()));
            }
        } else {
            throw new RuntimeException("error: url=" + url);
        }
    }

    /**
     * 从InputStream读取文字，指定编码
     */
    private static String readString(InputStream in, String encoding) throws Exception {
        byte[] data = new byte[1024];
        int length = 0;
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while ((length = in.read(data)) != -1) {
            bout.write(data, 0, length);
        }
        return new String(bout.toByteArray(), encoding);
    }
}
