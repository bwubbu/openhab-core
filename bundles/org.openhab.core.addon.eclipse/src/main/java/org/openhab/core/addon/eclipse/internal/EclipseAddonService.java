/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.addon.eclipse.internal;

import static java.util.Map.entry;
import static org.openhab.core.addon.AddonType.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonInfo;
import org.openhab.core.addon.AddonInfoRegistry;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.AddonType;
import org.openhab.core.config.core.ConfigurableService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an implementation of an {@link AddonService} that can be used when debugging in Eclipse.
 *
 * @author Wouter Born - Initial contribution
 */
@Component(name = "org.openhab.addons")
@NonNullByDefault
@ConfigurableService(category = "system", label = "Add-on Management", description_uri = EclipseAddonService.CONFIG_URI)
public class EclipseAddonService implements AddonService {

    public static final String CONFIG_URI = "system:addons";

    private static final Logger logger = LoggerFactory.getLogger(EclipseAddonService.class);

    private static final String SERVICE_ID = "eclipse";
    private static final String ADDON_ID_PREFIX = SERVICE_ID + ":";

    private static final String ADDONS_CONTENT_TYPE = "application/vnd.openhab.bundle";
    private static final String ADDONS_AUTHOR = "openHAB";
    private static final String BUNDLE_SYMBOLIC_NAME_PREFIX = "org.openhab.";

    private static final String REPO_URI_PROPERTY = "openhab.eclipse.addon.repoUri";
    private static final URI DEFAULT_REPO_URI = URI.create("https://www.openhab.org/");

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 1500;

    private static final Map<String, String> ADDON_BUNDLE_TYPE_MAP = Map.ofEntries(
            entry(AUTOMATION.getId(), "automation"), //
            entry(BINDING.getId(), "binding"), //
            entry(MISC.getId(), "io"), //
            entry(PERSISTENCE.getId(), "persistence"), //
            entry(TRANSFORMATION.getId(), "transform"), //
            entry(UI.getId(), "ui"), //
            entry(VOICE.getId(), "voice"));

    private static final Map<String, String> BUNDLE_ADDON_TYPE_MAP = ADDON_BUNDLE_TYPE_MAP.entrySet().stream()
            .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

    private static final String DOCUMENTATION_URL_PREFIX = "https://www.openhab.org/addons/";

    private static final Map<String, String> DOCUMENTATION_URL_FORMATS = Map.ofEntries(
            entry(AUTOMATION.getId(), DOCUMENTATION_URL_PREFIX + "automation/%s/"), //
            entry(BINDING.getId(), DOCUMENTATION_URL_PREFIX + "bindings/%s/"), //
            entry(MISC.getId(), DOCUMENTATION_URL_PREFIX + "integrations/%s/"), //
            entry(PERSISTENCE.getId(), DOCUMENTATION_URL_PREFIX + "persistence/%s/"), //
            entry(TRANSFORMATION.getId(), DOCUMENTATION_URL_PREFIX + "transformations/%s/"), //
            entry(UI.getId(), DOCUMENTATION_URL_PREFIX + "ui/%s/"), //
            entry(VOICE.getId(), DOCUMENTATION_URL_PREFIX + "voice/%s/"));

    private final BundleContext bundleContext;
    private final AddonInfoRegistry addonInfoRegistry;

    @Activate
    public EclipseAddonService(BundleContext bundleContext, @Reference AddonInfoRegistry addonInfoRegistry) {
        this.bundleContext = bundleContext;
        this.addonInfoRegistry = addonInfoRegistry;
    }

    @Deactivate
    protected void deactivate() {
        throw new UnsupportedOperationException("Not supported by EclipseAddonService");
    }

    @Override
    public String getId() {
        return SERVICE_ID;
    }

    @Override
    public String getName() {
        return "Eclipse Add-on Service";
    }

    @Override
    public void refreshSource() {
        URI repoUri = readRepoUri();
        try {
            ensureRepoReachable(repoUri);
            logger.debug("refreshSource: repo reachable: {}", repoUri);
        } catch (IOException e) {
            // Clear message for offline/proxy cases
            logger.warn("refreshSource: repository unreachable (offline/proxy/firewall?): {}. {}", repoUri,
                    e.getMessage());
            throw new IllegalStateException("Cannot refresh add-on source because repository is unreachable: " + repoUri
                    + ". Check network/proxy/firewall settings.", e);
        }
    }

    @Override
    public void install(String id) {
        throw new UnsupportedOperationException(getName() + " does not support installing add-ons");
    }

    @Override
    public void uninstall(String id) {
        throw new UnsupportedOperationException(getName() + " does not support uninstalling add-ons");
    }

    private boolean isAddon(Bundle bundle) {
        String symbolicName = bundle.getSymbolicName();
        String[] segments = symbolicName.split("\\.");
        return symbolicName.startsWith(BUNDLE_SYMBOLIC_NAME_PREFIX) && bundle.getState() == Bundle.ACTIVE
                && segments.length >= 4 && ADDON_BUNDLE_TYPE_MAP.containsValue(segments[2]);
    }

    private Addon getAddon(Bundle bundle, @Nullable Locale locale) {
        String symbolicName = bundle.getSymbolicName();
        String[] segments = symbolicName.split("\\.");

        String type = Objects.requireNonNull(BUNDLE_ADDON_TYPE_MAP.get(segments[2]));
        String name = segments[3];

        String uid = type + Addon.ADDON_SEPARATOR + name;

        Addon.Builder addon = Addon.create(ADDON_ID_PREFIX + uid).withType(type).withId(name)
                .withContentType(ADDONS_CONTENT_TYPE).withVersion(bundle.getVersion().toString())
                .withAuthor(ADDONS_AUTHOR, true).withInstalled(true);

        AddonInfo addonInfo = addonInfoRegistry.getAddonInfo(uid, locale);

        if (addonInfo != null) {
            // only enrich if this add-on is installed, otherwise wrong data might be added
            addon = addon.withLabel(addonInfo.getName()).withDescription(addonInfo.getDescription())
                    .withConnection(addonInfo.getConnection()).withCountries(addonInfo.getCountries())
                    .withLink(getDefaultDocumentationLink(type, name))
                    .withConfigDescriptionURI(addonInfo.getConfigDescriptionURI());
        } else {
            addon = addon.withLabel(name).withLink(getDefaultDocumentationLink(type, name));
        }

        addon.withLoggerPackages(List.of(symbolicName));

        return addon.build();
    }

    @Override
    public List<Addon> getAddons(@Nullable Locale locale) {
        return Arrays.stream(bundleContext.getBundles()) //
                .filter(this::isAddon) //
                .map(bundle -> getAddon(bundle, locale)) //
                .sorted(Comparator.comparing(Addon::getLabel)) //
                .toList();
    }

    private @Nullable String getDefaultDocumentationLink(String type, String name) {
        String format = DOCUMENTATION_URL_FORMATS.get(type);
        return format == null ? null : String.format(format, name);
    }

    @Override
    public @Nullable Addon getAddon(String uid, @Nullable Locale locale) {
        String id = uid.replaceFirst(ADDON_ID_PREFIX, "");
        String[] segments = id.split(Addon.ADDON_SEPARATOR);
        String symbolicName = BUNDLE_SYMBOLIC_NAME_PREFIX + ADDON_BUNDLE_TYPE_MAP.get(segments[0]) + "." + segments[1];
        return Arrays.stream(bundleContext.getBundles()) //
                .filter(bundle -> bundle.getSymbolicName().equals(symbolicName)) //
                .filter(this::isAddon) //
                .map(bundle -> getAddon(bundle, locale)) //
                .findFirst().orElse(null);
    }

    @Override
    public List<AddonType> getTypes(@Nullable Locale locale) {
        return AddonType.DEFAULT_TYPES;
    }

    @Override
    public @Nullable String getAddonId(URI extensionURI) {
        return null;
    }

    /**
     * Reads the repo URI from a system property to allow adaptation to different environments.
     * If not provided, a safe default is used.
     */
    protected URI readRepoUri() {
        String configured = System.getProperty(REPO_URI_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_REPO_URI;
        }
        try {
            return URI.create(configured.trim());
        } catch (IllegalArgumentException e) {
            // If misconfigured, fall back but log clearly.
            logger.warn("Invalid repo URI configured in -D{}='{}'. Falling back to default '{}'.", REPO_URI_PROPERTY,
                    configured, DEFAULT_REPO_URI);
            return DEFAULT_REPO_URI;
        }
    }

    /**
     * Lightweight reachability check using a HEAD request. Works with proxies configured at JVM/OS level.
     * Accepts any HTTP 2xx-4xx response as "reachable" (4xx still proves network path works).
     */
    private void ensureRepoReachable(URI repoUri) throws IOException {
        URL url = repoUri.toURL();

        List<Proxy> proxies = ProxySelector.getDefault() != null ? ProxySelector.getDefault().select(repoUri)
                : List.of(Proxy.NO_PROXY);
        if (proxies == null || proxies.isEmpty()) {
            proxies = List.of(Proxy.NO_PROXY);
        }

        IOException last = null;

        for (Proxy proxy : proxies) {
            try {
                HttpURLConnection conn = openHeadConnection(url, proxy);
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                int code = conn.getResponseCode();

                // 2xx-4xx => we can reach the server (401/403/404 still means reachable)
                if (code >= 200 && code < 500) {
                    return;
                }

                last = new IOException("Repository returned HTTP " + code);
            } catch (UnknownHostException e) {
                last = new IOException("Host cannot be resolved (DNS). Likely offline or blocked by DNS.", e);
            } catch (SocketTimeoutException e) {
                last = new IOException("Connection timed out. Possible proxy/offline/firewall.", e);
            } catch (ConnectException e) {
                last = new IOException("Connection refused. Server down or blocked by network/proxy.", e);
            } catch (SSLException e) {
                last = new IOException("SSL/TLS handshake failed (certificate or proxy interception issue).", e);
            } catch (IOException e) {
                last = e;
            }
        }

        if (last != null) {
            throw last;
        }
        throw new IOException("Repository is unreachable.");
    }

    protected HttpURLConnection openHeadConnection(URL url, Proxy proxy) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setRequestMethod("HEAD");
        return conn;
    }
}
