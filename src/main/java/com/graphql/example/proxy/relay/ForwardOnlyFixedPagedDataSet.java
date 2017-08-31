package com.graphql.example.proxy.relay;

import graphql.relay.Connection;
import graphql.relay.ConnectionCursor;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

//
// the ice and fire API uses page=n&pageSize=n pagination and we cant know the
// total set of possible results up front.  Therefore we cant use SimpleListConnection
// to compute the Relay connection and cursor as it requires the total number of items.
//
// So this is a hand build implementation based on the idea of having a fixed number of items
// per page read (say 100 at a time but every time) so that cursors stay stable over
// between reads.
//

public class ForwardOnlyFixedPagedDataSet {


    /**
     * Called to get a realy {@link graphql.relay.Connection} of edges where the underlying dataset
     * is a set of fixed size pages of data that can ONLY be read in a forward only manner
     *
     * @param env                 the data fetching environment
     * @param defaultFirstN       the default number for the 'first argument
     * @param pageOfDataRetriever the function to retrieve data
     *
     * @return a connection according to the 'after' and 'first' arguments
     */
    public static <T> Connection<T> getConnection(DataFetchingEnvironment env, int defaultFirstN, Function<Integer, PagedResult<T>> pageOfDataRetriever) {

        int firstN = getArg(env, "first", defaultFirstN);
        if (firstN < 0) {
            throw new IllegalArgumentException("You must provide a positive value for 'first'");
        }
        boolean afterPresent = env.getArgument("after") != null;
        String zeroZeroDefault = new CursorPageAndOffset(0, 0).toConnectionCursor().toString();
        String afterCursor = getArg(env, "after", zeroZeroDefault);

        CursorPageAndOffset desiredPageAndOffset = CursorPageAndOffset.fromCursor(afterCursor);
        int startPage = desiredPageAndOffset.getPage();

        List<Edge<T>> edges = new ArrayList<>();
        boolean addToEdges = false;
        boolean hasNextPage = true;
        int fullOffset = 0;
        int howManyNeeded = firstN + (afterPresent ? 1 : 0); // if after is present we slice it away later
        while (edges.size() < howManyNeeded) {

            PagedResult<T> pagedResult = pageOfDataRetriever.apply(startPage);
            for (T obj : pagedResult.getResults()) {
                ConnectionCursor edgeCursor = new CursorPageAndOffset(startPage, fullOffset).toConnectionCursor();
                if (fullOffset == desiredPageAndOffset.getOffset()) {
                    addToEdges = true;
                }
                if (addToEdges) {
                    edges.add(new DefaultEdge<>(obj, edgeCursor));
                }
                fullOffset++;
            }
            startPage++;
            if (!pagedResult.hasNextPage()) {
                hasNextPage = false;
                break;
            }
        }
        if (edges.isEmpty()) {
            return emptyConnection();
        }

        // 'after' cursors are exclusive so we skip the edge that equals 'after' but only if its
        // present
        int sliceIndex = 0;
        if (afterPresent) {
            sliceIndex = 1;
        }
        List<Edge<T>> slicedEdges = edges.subList(sliceIndex, Math.min(edges.size(), sliceIndex + firstN));
        if (slicedEdges.isEmpty()) {
            return emptyConnection();
        }

        final boolean nextPage = hasNextPage;
        return new DefaultConnection<>(slicedEdges, new DefaultPageInfo(
                slicedEdges.get(0).getCursor(),
                slicedEdges.get(slicedEdges.size() - 1).getCursor(),
                false,
                nextPage
        ));
    }

    private static <T> Connection<T> emptyConnection() {
        PageInfo pageInfo = new DefaultPageInfo(null, null, false, false);
        return new DefaultConnection<>(Collections.emptyList(), pageInfo);
    }

    private static <T> T getArg(DataFetchingEnvironment env, String name, T defaultValue) {
        return env.getArgument(name) == null ? defaultValue : env.getArgument(name);
    }

}
