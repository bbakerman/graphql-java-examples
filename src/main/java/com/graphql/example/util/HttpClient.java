package com.graphql.example.util;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.graphql.example.util.JsonKit.fromJson;

public class HttpClient {

    private static Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static OkHttpClient httpClient = new OkHttpClient();


    public static <T> T readResource(String resource, QueryParameters.QueryParameter... params) {
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.scheme("https").host("www.anapioficeandfire.com").addPathSegment("api").addPathSegment(resource);
        if (params != null) {
            for (QueryParameters.QueryParameter param : params) {
                urlBuilder.addQueryParameter(param.getName(), param.getValue());
            }
        }

        String url = urlBuilder.build().toString();
        //noinspection unchecked
        return (T) readResourceUrl(url);
    }

    public static Object readResourceUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        Request request = requestBuilder
                .build();

        try {
            return read(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object read(Request request) throws IOException {

        log.info("Reading {}...", request.url());
        Response response = httpClient.newCall(request).execute();
        ResponseBody body = response.body();
        long ms = response.receivedResponseAtMillis() - response.sentRequestAtMillis();
        log.info("Reading {}...", request.url());

        String jsonString = "";
        Object obj = null;
        if (body != null) {
            jsonString = body.string();
            obj = fromJson(jsonString);
        }

        log.info("  {} : {} chars in {} ms", response.code(), jsonString.length(), ms);
        return obj;
    }

}
