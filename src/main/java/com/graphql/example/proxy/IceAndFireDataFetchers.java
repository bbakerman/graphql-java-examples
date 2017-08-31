package com.graphql.example.proxy;

import com.graphql.example.util.HttpClient;
import com.graphql.example.util.RelayUtils;
import graphql.relay.Connection;
import graphql.relay.Relay;
import graphql.relay.SimpleListConnection;
import graphql.schema.DataFetcher;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.impl.PromisedValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.graphql.example.util.HttpQueryParameter.qp;
import static java.util.stream.Collectors.toList;

class IceAndFireDataFetchers {

    private static final Logger log = LoggerFactory.getLogger(IceAndFireDataFetchers.class);

    public static final int PAGE_SIZE = 50; // this is what they allow

    private BatchLoader<String, Object> urlBatchLoader = urls -> {

        // The backing API does not have an API to get multiple resources
        // in one batch.  We just have a series of resource URLS instead.
        List<CompletionStage<Object>> resources = new ArrayList<>();

        // but we can get them in parallel though via supplyAsync say
        for (String url : urls) {
            resources.add(CompletableFuture.supplyAsync(() -> HttpClient.readResourceUrl(url).getData()));
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
            // We can use SimpleListConnection in this case because the total set of possible edges
            // is known from the field and we can get a subset of them
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
        return env ->
                CompletableFuture.supplyAsync(() ->
                        ForwardOnlyFixedPagedDataSet.getConnection(env, PAGE_SIZE,
                                pageNumber -> readPagedObjects("books", pageNumber)));
    }

    DataFetcher houses() {
        return env ->
                CompletableFuture.supplyAsync(() ->
                        ForwardOnlyFixedPagedDataSet.getConnection(env, PAGE_SIZE,
                                pageNumber -> readPagedObjects("houses", pageNumber)));
    }

    DataFetcher characters() {
        return env ->
                CompletableFuture.supplyAsync(() ->
                        ForwardOnlyFixedPagedDataSet.getConnection(env, PAGE_SIZE,
                                pageNumber -> readPagedObjects("characters", pageNumber)));
    }

    private ForwardOnlyFixedPagedDataSet.PagedResult<Map<String, Object>> readPagedObjects(String resource, int pageNumber) {
        log.info("Fetching {} page: {}", resource, pageNumber);
        ForwardOnlyFixedPagedDataSet.PagedResult<Map<String, Object>> pagedResult =
                HttpClient.readResource(resource, qp("pageNumber", pageNumber), qp("pageSize", PAGE_SIZE));

        log.info("\tread {} {}", pagedResult.getResults().size(), resource);

        pagedResult.getResults().forEach(resourceObj -> {
            //
            // this is mutative since relay requires ids yet the REST API does not have them
            addGlobalIds(resourceObj);
            String url = (String) resourceObj.get("url");
            //
            // prime the dataloader with each entry so caching should work when     we ask for it again
            resourceDataLoader.prime(url, resourceObj);
        });
        return pagedResult;
    }

    private static <T> T mapGet(Map<String, Object> source, String fieldName) {
        //noinspection unchecked
        return (T) source.get(fieldName);
    }


}
