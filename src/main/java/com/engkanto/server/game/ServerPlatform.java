package com.engkanto.server.game;

record ServerPlatform(double left, double top, double width, double height, boolean passThrough) {
    double right() {
        return left + width;
    }
}
