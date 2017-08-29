package com.graphql.example.util;

import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultEdge;
import graphql.relay.Edge;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class RelayUtils {

    /**
     * Maps a {@link graphql.relay.Connection} of type T into a connection of type R by replacing the
     * list of edges and otherwise leaving the {@link graphql.relay.PageInfo}
     * information in place.
     *
     * @param connection the connection to map
     * @param edgeMapper the function to map each edge object
     * @param <T>        the type to map from
     * @param <R>        the type to map to
     *
     * @return a new connection mapped from edges of U to edges of T
     */
    public static <T, R> Connection<R> mapEdges(Connection<T> connection, Function<Edge<T>, Edge<R>> edgeMapper) {
        List<Edge<R>> mappedEdges = connection.getEdges().stream().map(edgeMapper).collect(toList());
        return new DefaultConnection<>(mappedEdges, connection.getPageInfo());
    }

    /**
     * Maps a {@link graphql.relay.Connection} of type T into a connection of type R by replacing the
     * nodes inside each edges to a new mapped value leaving the {@link graphql.relay.PageInfo} and {@link graphql.relay.ConnectionCursor}
     * information in place.
     *
     * @param connection the connection to map
     * @param edgeMapper the function to map each edge node object
     * @param <T>        the type to map from
     * @param <R>        the type to map to
     *
     * @return a new connection mapped from edges of T to edges of R
     */
    public static <T, R> Connection<R> mapEdgeNodes(Connection<T> connection, Function<T, R> edgeMapper) {
        List<Edge<R>> mappedEdges = connection.getEdges().stream().map(uEdge -> {
            R mappedObj = edgeMapper.apply(uEdge.getNode());
            return new DefaultEdge<>(mappedObj, uEdge.getCursor());
        }).collect(toList());
        return new DefaultConnection<>(mappedEdges, connection.getPageInfo());
    }

    public static <T> List<T> getEdgeNodes(Connection<T> connection) {
        return connection.getEdges().stream().map(Edge::getNode).collect(Collectors.toList());
    }
}
