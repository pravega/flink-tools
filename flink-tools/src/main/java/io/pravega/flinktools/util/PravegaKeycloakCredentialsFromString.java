/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package io.pravega.flinktools.util;

import io.pravega.client.stream.impl.Credentials;
import io.pravega.keycloak.client.KeycloakAuthzClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static io.pravega.auth.AuthConstants.BEARER;

/**
 * Keycloak implementation of the Pravega Credentials interface, based on
 * Keycloak JWT tokens.
 * This accepts the Keycloak JWT token as a String containing JSON.
 */
public class PravegaKeycloakCredentialsFromString implements Credentials {
    private static final long serialVersionUID = 1L;

    private final String keycloakConfig;

    // The actual keycloak client won't be serialized.
    private transient KeycloakAuthzClient kc = null;

    /**
     * @param keycloakConfig The Keycloak JWT token as a String containing JSON.
     */
    public PravegaKeycloakCredentialsFromString(final String keycloakConfig) {
        this.keycloakConfig = keycloakConfig;
        init();
    }

    @Override
    public String getAuthenticationType() {
        return BEARER;
    }

    @Override
    public String getAuthenticationToken() {
        init();
        return kc.getRPT();
    }

    private void init() {
        // KeycloakAuthzClient requires a file. We write the authentication credentials to
        // a secure file and delete it immediately after initialization.
        if (kc == null) {
            try {
                final Path tempPath = Files.createTempFile("keycloak-", ".json",
                        // Only user has permissions to the file.
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                try {
                    try (PrintWriter out = new PrintWriter(tempPath.toFile())) {
                        out.println(keycloakConfig);
                    }
                    kc = KeycloakAuthzClient.builder().withConfigFile(tempPath.toString()).build();
                } finally {
                    Files.delete(tempPath);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
