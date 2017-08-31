
# Graphql Examples

This splodge of code is mostly my personal explorations of `graphql-java` and how to use it in more real world examples.

I thought I would try and make it in "example" format so that others might derive some value from it.

I also plan to make some blog posts about this in the near future and use this code as the basis for that.

Its built with Gradle and Java, with some Spock Groovy unit testing.

## Example 1 - HTTP Proxy of an existing REST API

The first example is the idea of fronting an existing REST API with graphql.  

In this case *An API of Ice And Fire*

https://anapioficeandfire.com/Documentation

This is not an original idea, others have done it in Node.js but hey I am a Java guy.

The code is in `com.graphql.example.proxy.IceAndFireApiProxy`

I wanted to explore the following:

- Hosting a simple HTTP server of graphql - using Jetty
- A Relay shape that turns old school page=N pagination into cursors eg https://facebook.github.io/relay/docs/getting-started.html
- Using the `java-dataloader` to make an efficient REST proxy eg: https://github.com/graphql-java/java-dataloader

What I learnt was :

- `CompleteableFuture` support in graphql-java is da bomb!  Wicked good. 
- Used well, `java-dataloader` is da double bomb!  By priming the cache and using URLs as keys we cache the sheet out of everything
- Relay pagination is tricky.  Not impossible but tricky with forward only pagination.
- The Relay support in graphql-java is basic (it doesn't pretend to be more I guess) and really on works if you have a complete list 
of edges in memory 



 
