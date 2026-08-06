/*
 * Copyright (c) 2026, Entgra (Pvt) Ltd. (http://www.entgra.io) All Rights Reserved.
 *
 * Entgra (Pvt) Ltd. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */

package io.entgra.device.mgt.plugins.emqx.exhook;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

/**
 * Credentials used by a trusted internal MQTT client to identify itself to EMQX.
 *
 * <p>The credentials are read from the optional {@code [emqx.trusted_client]} section in
 * {@code repository/conf/deployment.toml}. Both values must be configured; a partially configured
 * identity is deliberately disabled. This keeps the shared EMQX extension neutral: a deployment may
 * designate its AMI service, or another internal MQTT service, as the trusted client.</p>
 */
public final class TrustedMqttCredentials {
    private static final Log logger = LogFactory.getLog(TrustedMqttCredentials.class);
    private static final String SECTION = "emqx.trusted_client";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private final String username;
    private final String password;

    private TrustedMqttCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static TrustedMqttCredentials load() {
        Path deploymentToml = getDeploymentTomlPath();
        if (deploymentToml == null || !Files.isRegularFile(deploymentToml)) {
            logger.warn("Trusted MQTT client credentials are disabled: deployment.toml was not found");
            return new TrustedMqttCredentials(null, null);
        }

        String username = null;
        String password = null;
        boolean inAmiNodeSection = false;
        try {
            List<String> lines = Files.readAllLines(deploymentToml, StandardCharsets.UTF_8);
            for (String line : lines) {
                String value = line.trim();
                if (value.startsWith("[") && value.endsWith("]")) {
                    inAmiNodeSection = SECTION.equals(value.substring(1, value.length() - 1).trim());
                    continue;
                }
                if (!inAmiNodeSection || value.startsWith("#")) {
                    continue;
                }

                int separator = value.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = value.substring(0, separator).trim();
                String propertyValue = resolveEnvironmentReference(
                        unquote(value.substring(separator + 1).trim()));
                if (USERNAME.equals(key)) {
                    username = propertyValue;
                } else if (PASSWORD.equals(key)) {
                    password = propertyValue;
                }
            }
        } catch (IOException e) {
            logger.error("Unable to read trusted MQTT client credentials from " + deploymentToml, e);
            return new TrustedMqttCredentials(null, null);
        }

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            logger.warn("Trusted MQTT client credentials are disabled: both emqx.trusted_client.username and "
                    + "emqx.trusted_client.password must be configured");
            return new TrustedMqttCredentials(null, null);
        }
        return new TrustedMqttCredentials(username, password);
    }

    public boolean matches(String candidateUsername, String candidatePassword) {
        return isConfigured()
                && MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(candidateUsername).getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(candidatePassword).getBytes(StandardCharsets.UTF_8));
    }

    public boolean hasUsername(String candidateUsername) {
        return isConfigured() && MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(candidateUsername).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isConfigured() {
        return StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password);
    }

    private static Path getDeploymentTomlPath() {
        String configDirectory = System.getProperty("carbon.config.dir");
        if (StringUtils.isEmpty(configDirectory)) {
            configDirectory = System.getProperty("wso2.carbon.config.dir");
        }
        if (StringUtils.isNotEmpty(configDirectory)) {
            return Paths.get(configDirectory, "deployment.toml");
        }

        String carbonHome = System.getProperty("carbon.home");
        return StringUtils.isEmpty(carbonHome) ? null
                : Paths.get(carbonHome, "repository", "conf", "deployment.toml");
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String resolveEnvironmentReference(String value) {
        if (value.startsWith("$env{") && value.endsWith("}")) {
            return System.getenv(value.substring(5, value.length() - 1));
        }
        return value;
    }
}
