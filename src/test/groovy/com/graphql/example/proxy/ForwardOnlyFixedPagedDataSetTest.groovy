package com.graphql.example.proxy

import com.graphql.example.proxy.relay.ForwardOnlyFixedPagedDataSet
import com.graphql.example.proxy.relay.PagedResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentBuilder
import spock.lang.Specification

class ForwardOnlyFixedPagedDataSetTest extends Specification {

    int mkListCount = 0

    def "basic_first_n"() {

        when:
        DataFetchingEnvironment env = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
                .arguments([first: 20]).build()

        def connection = ForwardOnlyFixedPagedDataSet.getConnection(env, 10, {
            page -> mkList(5)
        },)

        then:
        connection.getEdges().size() == 20
        connection.getPageInfo() != null
        mkListCount == 4
    }

    def "basic_first_20_but_only_15_available"() {

        when:
        DataFetchingEnvironment env = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
                .arguments([first: 20]).build()

        def connection = ForwardOnlyFixedPagedDataSet.getConnection(env, 10, {
            page -> (page == 2) ? mkList(5, false) : mkList(5)
        },)

        then:
        connection.getEdges().size() == 15
        !connection.getPageInfo().isHasNextPage()
        mkListCount == 3
    }

    def "basic_first_n_after_cursor"() {

        when:
        DataFetchingEnvironment env = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
                .arguments([first: 20, "after": mkCursor(10, 55)]).build()

        def connection = ForwardOnlyFixedPagedDataSet.getConnection(env, 10, {
            page -> mkList(5)
        },)

        then:
        connection.getEdges().size() == 20
        connection.getPageInfo() != null
        mkListCount == 16 // 11 to get going and another 5 after that to get a full 20 because of after cursor is exclusive
    }

    def "cursor_is_after_total_set_and_hence_zero_results"() {

        when:
        DataFetchingEnvironment env = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
                .arguments([first: 20, "after": mkCursor(0, 55)]).build()

        def connection = ForwardOnlyFixedPagedDataSet.getConnection(env, 10, {
            page -> (page == 2) ? mkList(5, false) : mkList(5)
        },)

        then:
        connection.getEdges().size() == 0
        !connection.getPageInfo().isHasNextPage()
        mkListCount == 3
    }

    def mkCursor(int page, int offset) {
        return new ForwardOnlyFixedPagedDataSet.PageAndOffset(page, offset).toConnectionCursor().toString()
    }

    def mkList(int count, boolean hasNextPage = true) {
        mkListCount++
        def l = new ArrayList<String>()
        for (int i = 0; i < count; i++) {
            l.add("item" + i)
        }
        return new PagedResult(l, hasNextPage)
    }

}
