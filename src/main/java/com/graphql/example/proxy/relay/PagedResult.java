package com.graphql.example.proxy.relay;

import java.util.List;

/**
 * The results that come back from the page retrieval function need to tell us
 * the list of results and the whether their is a next page or not
 */
public class PagedResult<T> {
    private final List<T> results;
    private final boolean hasNextPage;

    public PagedResult(List<T> results, boolean hasNextPage) {
        this.results = results;
        this.hasNextPage = hasNextPage;
    }

    public List<T> getResults() {
        return results;
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }
}
