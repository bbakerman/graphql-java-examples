package com.graphql.example.proxy;

import com.graphql.example.util.HttpClient;
import com.graphql.example.util.QueryParameters.QueryParameter;
import com.graphql.example.util.RelayUtils;
import graphql.relay.Connection;
import graphql.relay.Relay;
import graphql.relay.SimpleListConnection;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.impl.PromisedValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.graphql.example.util.QueryParameters.QueryParameter.qp;
import static java.util.stream.Collectors.toList;

class IceAndFireDataFetchers {

    private BatchLoader<String, Object> urlBatchLoader = urls -> {

        // The backing API does not have an API to get multiple resources
        // in one batch.  We just have a series of resource URLS instead.
        List<CompletionStage<Object>> resources = new ArrayList<>();

        // but we can get them in parallel though via supplyAsync say
        for (String url : urls) {
            resources.add(CompletableFuture.supplyAsync(() -> HttpClient.readResourceUrl(url)));
        }

        // wait for all of the values to complete via this PromisedValues helper
        // which comes from the java-dataloader library
        return PromisedValues.allOf(resources).toCompletableFuture();
    };

    private DataLoader<String, Object> resourceDataLoader = new DataLoader<>(urlBatchLoader);

    List<DataLoader<?, ?>> getDataLoaders() {
        return Collections.singletonList(resourceDataLoader);
    }


    /**
     * The API has many lists of strings that are full URLS to objects eg:
     *
     * "characters": [
     * "   https://www.anapioficeandfire.com/api/characters/2",
     * "   https://www.anapioficeandfire.com/api/characters/3",
     * ...
     * ],
     *
     * This data fetcher will follow the URLs and return them as a fetched object.
     *
     * @return a data fetcher that follows URLs
     */
    DataFetcher urlConnection() {
        return env -> {
            Map<String, Object> source = env.getSource();
            String fieldName = env.getFieldDefinition().getName();
            List<String> allUrls = mapGet(source, fieldName);


            //
            // SimpleListConnection.get() allows us to apply "pagination arguments"
            // such as "first:N" and "after:xxx" so we get a smaller set of results
            // before we go off to the data loader and actually make HTTP calls
            // for that data.  There is no point getting ALL the resources if we only
            // want a small page of them
            //
            Connection<String> urlConnection = new SimpleListConnection<>(allUrls).get(env);

            List<String> pagedUrls = RelayUtils.getEdgeNodes(urlConnection);

            CompletableFuture<List<Object>> resourceLoadsPromise = resourceDataLoader.loadMany(pagedUrls);

            return resourceLoadsPromise.thenApply(resourceList -> {
                resourceList = resourceList.stream().map(this::addGlobalIds).collect(toList());
                SimpleListConnection<Object> relayConnection = new SimpleListConnection<>(resourceList);
                //
                // Now make that list back into relay connection as expected but this time
                // with a full object (read from REST) behind it
                //
                return relayConnection.get(env);
            });
        };
    }

    private <R> R addGlobalIds(R resource) {
        resource = addGlobalIdFromKey(resource, "url");
        resource = addGlobalIdFromKey(resource, "name");
        return resource;
    }

    private <R> R addGlobalIdFromKey(R resource, String srcKey) {
        if (resource instanceof Map) {
            Map resourceMap = (Map) resource;
            if (!resourceMap.containsKey("id")) {
                Object value = resourceMap.get(srcKey);
                if (value != null) {
                    //noinspection unchecked
                    resourceMap.put("id", new Relay().toGlobalId(srcKey, value.toString()));
                }
            }
        }
        return resource;
    }

    DataFetcher urlObject() {
        return env -> {
            Map<String, Object> source = env.getSource();
            String fieldName = env.getFieldDefinition().getName();
            String url = mapGet(source, fieldName);

            return resourceDataLoader.load(url);
        };
    }

    DataFetcher books() {
        return env -> {
            QueryParameter pageSize = computePageSize(env);
            List<Object> books = HttpClient.readResource("books", pageSize);
            SimpleListConnection<Object> relayConnection = new SimpleListConnection<>(books);
            return relayConnection.get(env);
        };
    }

    DataFetcher characters() {
        return env -> {
            QueryParameter pageSize = computePageSize(env);
            List<Object> books = HttpClient.readResource("characters", pageSize);
            SimpleListConnection<Object> relayConnection = new SimpleListConnection<>(books);
            return relayConnection.get(env);
        };
    }

    private QueryParameter computePageSize(DataFetchingEnvironment env) {
        int first = Optional.ofNullable((Integer) env.getArgument("first")).orElse(100);
        return qp("pageSize", "" + first);
    }

    private static <T> T mapGet(Map<String, Object> source, String fieldName) {
        //noinspection unchecked
        return (T) source.get(fieldName);
    }


}
