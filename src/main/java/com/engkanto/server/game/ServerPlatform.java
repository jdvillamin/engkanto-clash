/*
 * Authors:
 * Alexander John Castro III
 * Sean Caleb Romero
 * Jan Neal Isaac Villamin
 * Lab section: B-5L
 * Program description:
 * This file represents a platform on the server. It lets the server check collisions using the same arena layout as the client.
 */
package com.engkanto.server.game;

record ServerPlatform(double left, double top, double width, double height, boolean passThrough) {
    double right() {
        return left + width;
    }
}
