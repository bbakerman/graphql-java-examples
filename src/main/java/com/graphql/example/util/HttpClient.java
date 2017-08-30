package com.graphql.example.util;

import com.graphql.example.proxy.ForwardOnlyFixedPagedDataSet;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.graphql.example.util.JsonKit.fromJson;

public class HttpClient {

    private static Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static OkHttpClient httpClient = new OkHttpClient();

    public static class DataAndResponse {
        private final Response response;
        private final Object data;

        public DataAndResponse(Response response, Object data) {
            this.response = response;
            this.data = data;
        }

        public Response getResponse() {
            return response;
        }

        public Object getData() {
            return data;
        }
    }

    public static <T> ForwardOnlyFixedPagedDataSet.PagedResult<T> readResource(String resource, QueryParameters.QueryParameter... params) {
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.scheme("https").host("www.anapioficeandfire.com").addPathSegment("api").addPathSegment(resource);
        if (params != null) {
            for (QueryParameters.QueryParameter param : params) {
                urlBuilder.addQueryParameter(param.getName(), param.getValue());
            }
        }

        String url = urlBuilder.build().toString();
        DataAndResponse dataAndResponse = readResourceUrl(url);
        //noinspection unchecked
        List<T> data = (List<T>) dataAndResponse.getData();
        return new ForwardOnlyFixedPagedDataSet.PagedResult<>(data, hasNext(dataAndResponse.getResponse()));
    }

    private static boolean hasNext(Response response) {
        String linkHeader = response.header("Link");
        return false;
    }

    public static DataAndResponse readResourceUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new DataAndResponse(null,null);
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

    private static DataAndResponse read(Request request) throws IOException {

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
        return new DataAndResponse(response, obj);
    }

}
