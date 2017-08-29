/**
 * The purpose of this code is to show an example of serving as graphql interface over top of
 * an existing a REST API.
 *
 * The Game of Thrones inspired https://www.anapioficeandfire.com was used as the backing API.
 *
 * The data model here is one of Books, which contain Characters which belong to Houses.
 *
 * One of the challenges that REST APIs have is that pure REST linking means you often make
 * N+1 calls... That is to get  list of Characters in a Book you have to make a call for
 * each character id listed.
 *
 * graphql makes this better by allowing multiple domain objects to be queried in one
 * HTTP call but it is still a challenge to efficiently server this under the covers.
 *
 * A naive attempt will do this be making N+1 calls under the covers.
 */
package com.graphql.example.proxy;