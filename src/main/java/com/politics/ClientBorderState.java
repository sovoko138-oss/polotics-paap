package com.politics;

import java.util.Collections;
import java.util.Set;

/** What the client knows about its own country's territory. Only filled in for presidents. */
public class ClientBorderState {
    public static volatile boolean active = false;
    public static volatile String dimension = "";
    public static volatile int color = 0xFFFFFF;
    public static volatile Set<Long> chunks = Collections.emptySet();
}
