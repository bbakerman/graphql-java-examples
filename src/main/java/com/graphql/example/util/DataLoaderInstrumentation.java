package com.graphql.example.util;


import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.NoOpInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DataLoaderInstrumentation extends NoOpInstrumentation {

    private final List<DataLoader<?, ?>> dataLoaders;

    public DataLoaderInstrumentation(List<DataLoader<?, ?>> dataLoaders) {
        this.dataLoaders = dataLoaders;
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return (result, t) -> dataLoaders.forEach(DataLoader::dispatch);
    }
}
