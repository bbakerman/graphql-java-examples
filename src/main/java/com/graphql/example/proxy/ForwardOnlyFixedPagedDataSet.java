package com.graphql.example.proxy;

import graphql.relay.Connection;
import graphql.relay.ConnectionCursor;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
import graphql.schema.DataFetchingEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * This uses an encoding of page # plus full offset from the page forward
     */
    static class PageAndOffset {
        private static final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
        private static final java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
        private final static Pattern pagePattern = Pattern.compile("^page=([0-9]*)");
        private final static Pattern offsetPattern = Pattern.compile(".*;offset=([0-9]*)");

        int page;
        int offset;

        PageAndOffset(int page, int offset) {
            this.page = page;
            this.offset = offset;
        }

        int getPage() {
            return page;
        }

        int getOffset() {
            return offset;
        }

        public static PageAndOffset fromCursor(String cursor) {
            String s = decode(cursor);
            Matcher matcher = pagePattern.matcher(s);
            if (!matcher.find()) {
                throwInvalidCursor(s);
            }
            String page = matcher.group(1);

            matcher = offsetPattern.matcher(s);
            if (!matcher.find()) {
                throwInvalidCursor(s);
            }
            String offset = matcher.group(1);
            return new PageAndOffset(Integer.parseInt(page), Integer.parseInt(offset));
        }

        ConnectionCursor toConnectionCursor() {
            return new DefaultConnectionCursor(encode("page=" + page + ";offset=" + offset));
        }

        private String encode(String s) {
            return encoder.encodeToString(s.getBytes(StandardCharsets.UTF_8));
        }

        static private String decode(String s) {
            return new String(decoder.decode(s), StandardCharsets.UTF_8);
        }

        private static void throwInvalidCursor(String cursor) {
            throw new IllegalArgumentException("Invalid paged cursor provided : " + cursor);
        }
    }

    /**
     * The results that come back from the page retrieval function need to tell us
     * the list of results and the whether their is a next page or not
     */
    public static class PagedResult<T> {
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
    public static Connection<Object> getConnection(DataFetchingEnvironment env, int defaultFirstN, Function<Integer, PagedResult> pageOfDataRetriever) {

        int firstN = getArg(env, "first", defaultFirstN);
        if (firstN < 0) {
            throw new IllegalArgumentException("You must provide a positive value for 'first'");
        }
        boolean afterPresent = env.getArgument("after") != null;
        String zeroZeroDefault = new PageAndOffset(0, 0).toConnectionCursor().toString();
        String afterCursor = getArg(env, "after", zeroZeroDefault);

        PageAndOffset desiredPageAndOffset = PageAndOffset.fromCursor(afterCursor);
        int startPage = desiredPageAndOffset.getPage();

        List<Edge<Object>> edges = new ArrayList<>();
        boolean addToEdges = false;
        boolean hasNextPage = true;
        int fullOffset = 0;
        int howManyNeeded = firstN + (afterPresent ? 1 : 0); // if after is present we slice it away later
        while (edges.size() < howManyNeeded) {

            PagedResult pagedResult = pageOfDataRetriever.apply(startPage);
            for (Object obj : pagedResult.getResults()) {
                ConnectionCursor edgeCursor = new PageAndOffset(startPage, fullOffset).toConnectionCursor();
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
        List<Edge<Object>> slicedEdges = edges.subList(sliceIndex, Math.min(edges.size(), sliceIndex + firstN));
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
