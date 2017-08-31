package com.graphql.example.proxy;

import com.graphql.example.util.DataLoaderInstrumentation;
import com.graphql.example.util.JsonKit;
import com.graphql.example.util.QueryParameters;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.tracing.TracingInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static java.util.Arrays.asList;

/**
 * An very simple example of serving a qraphql schema over http where it acts as a proxy to an existing REST API.
 * <p>
 * More info can be found here : http://graphql.org/learn/serving-over-http/
 */
public class IceAndFireApiProxy extends AbstractHandler {

    static Logger log = LoggerFactory.getLogger(IceAndFireApiProxy.class);


    static final int PORT = 3000;

    public static void main(String[] args) throws Exception {
        //
        // This example uses Jetty as an embedded HTTP server
        Server server = new Server(PORT);
        //
        // In Jetty, handlers are how your get called backed on a request
        server.setHandler(new IceAndFireApiProxy());
        server.start();

        server.join();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if ("/graphql".equals(target) || "/".equals(target)) {
            handleGraphql(request, response);
        }
        baseRequest.setHandled(true);
    }

    private void handleGraphql(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        log.info("Handling graphql request...");
        //
        // this builds out the parameters we need like the graphql query from the http request
        QueryParameters parameters = QueryParameters.from(httpRequest);
        if (parameters.getQuery() == null) {
            //
            // how to handle nonsensical requests is up to your application
            httpResponse.setStatus(400);
            return;
        }

        ExecutionInput.Builder executionInput = newExecutionInput()
                .query(parameters.getQuery())
                .operationName(parameters.getOperationName())
                .variables(parameters.getVariables());


        IceAndFireDataFetchers iceAndFireDataFetchers = new IceAndFireDataFetchers();

        //
        // you need a schema in order to execute queries
        GraphQLSchema schema = buildSchema(iceAndFireDataFetchers);

        //
        // we use instrumentation to intercept each level of the execution strategy and dispatch
        // the each data loader
        DataLoaderInstrumentation dataLoaderInstrumentation = new DataLoaderInstrumentation(
                iceAndFireDataFetchers.getDataLoaders()
        );

        //
        // we can combine multiple instrumentations together, for example to do tracing of
        // how long the request takes
        //
        ChainedInstrumentation chainedInstrumentation = new ChainedInstrumentation(
                asList(
                        new TracingInstrumentation(),
                        dataLoaderInstrumentation
                )
        );

        // finally you build a runtime graphql object and execute the query
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                // instrumentation is pluggable
                .instrumentation(chainedInstrumentation)
                .build();
        ExecutionResult executionResult = graphQL.execute(executionInput.build());

        returnAsJson(httpResponse, executionResult);
    }


    private void returnAsJson(HttpServletResponse response, ExecutionResult executionResult) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        JsonKit.toJson(response, executionResult.toSpecification());
    }

    static TypeDefinitionRegistry definitionRegistry;


    private GraphQLSchema buildSchema(IceAndFireDataFetchers iceAndFireDataFetchers) {

        //
        // reads a file that provides the schema types.  We cache that result so we don't reparse
        // during subsequent requests
        //
        if (definitionRegistry == null) {

            Reader streamReader = loadSchemaFile("gameOfThrones.graphqls");
            definitionRegistry = new SchemaParser().parse(streamReader);
        }

        //
        // the runtime wiring is used to provide the code that backs the
        // logical schema
        //
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("books", iceAndFireDataFetchers.books())
                        .dataFetcher("characters", iceAndFireDataFetchers.characters())
                        .dataFetcher("houses", iceAndFireDataFetchers.houses())
                )
                .type(newTypeWiring("Book")
                        .dataFetcher("characters", iceAndFireDataFetchers.urlConnection())
                        .dataFetcher("povCharacters", iceAndFireDataFetchers.urlConnection())
                )
                .type(newTypeWiring("Character")
                        .dataFetcher("father", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("mother", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("spouse", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("allegiances", iceAndFireDataFetchers.urlConnection())
                        .dataFetcher("books", iceAndFireDataFetchers.urlConnection())
                        .dataFetcher("povBooks", iceAndFireDataFetchers.urlConnection())
                )
                .type(newTypeWiring("House")
                        .dataFetcher("currentLord", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("founder", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("overload", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("heir", iceAndFireDataFetchers.urlObject())
                        .dataFetcher("cadetBranches", iceAndFireDataFetchers.urlConnection())
                        .dataFetcher("swornMembers", iceAndFireDataFetchers.urlConnection())
                )
                .build();


        // finally combine the logical schema with the physical runtime
        return new SchemaGenerator().makeExecutableSchema(definitionRegistry, wiring);
    }

    private Reader loadSchemaFile(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        return new InputStreamReader(stream);
    }
}
