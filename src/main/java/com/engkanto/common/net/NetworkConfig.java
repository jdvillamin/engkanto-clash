/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file stores shared networking constants. Both the client and server use it for the default port and maximum player count.
 */
package com.engkanto.common.net;

public final class NetworkConfig {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 50_137;
    public static final int MAX_PLAYERS = 4;

    private NetworkConfig() {
    }
}
