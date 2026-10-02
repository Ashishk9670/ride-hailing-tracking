package com.example.ridehailing.security;

import java.util.Set;

public final class Roles {
    public static final String RIDER = "RIDER";
    public static final String DRIVER = "DRIVER";
    public static final String ADMIN = "ADMIN";
    public static final Set<String> ALL = Set.of(RIDER, DRIVER, ADMIN);

    private Roles() {}
}
