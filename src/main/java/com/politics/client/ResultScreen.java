package com.politics.client;

/** A screen that waits for the server's answer after sending a form. */
public interface ResultScreen {
    void onResult(boolean ok, String message);
}
