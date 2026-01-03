package org.openhab.core.addon.eclipse.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openhab.core.addon.AddonInfoRegistry;
import org.osgi.framework.BundleContext;

class EclipseAddonServiceTest {

    @Test
    void refreshSource_repoUnreachable_shouldThrowClearMessage() throws Exception {
        BundleContext ctx = Mockito.mock(BundleContext.class);
        AddonInfoRegistry registry = Mockito.mock(AddonInfoRegistry.class);

        EclipseAddonService svc = new EclipseAddonService(ctx, registry) {
            @Override
            protected HttpURLConnection openHeadConnection(URL url, Proxy proxy) throws IOException {
                // Simulate offline/DNS issue (common in proxy/offline cases)
                throw new UnknownHostException("DNS failure");
            }

            @Override
            protected URI readRepoUri() {
                // Provide a test URI (doesn't matter because we never connect)
                return URI.create("https://repo.example.invalid/");
            }
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, svc::refreshSource);
        String msg = String.valueOf(ex.getMessage()).toLowerCase();
        assertTrue(msg.contains("unreachable"));
        assertTrue(msg.toLowerCase().contains("network") || msg.contains("proxy") || msg.contains("firewall"));
    }

    @Test
    void refreshSource_repoReachable_http200_shouldNotThrow() throws Exception {
        BundleContext ctx = Mockito.mock(BundleContext.class);
        AddonInfoRegistry registry = Mockito.mock(AddonInfoRegistry.class);

        HttpURLConnection conn = Mockito.mock(HttpURLConnection.class);
        Mockito.when(conn.getResponseCode()).thenReturn(200);

        EclipseAddonService svc = new EclipseAddonService(ctx, registry) {
            @Override
            protected HttpURLConnection openHeadConnection(URL url, Proxy proxy) {
                return conn;
            }

            @Override
            protected URI readRepoUri() {
                return URI.create("https://example.com/");
            }
        };

        assertDoesNotThrow(svc::refreshSource);
    }

    @Test
    void refreshSource_repoReachable_http404_shouldNotThrow() throws Exception {
        // 404 still proves we can reach the server, so your code should treat it as "reachable"
        BundleContext ctx = Mockito.mock(BundleContext.class);
        AddonInfoRegistry registry = Mockito.mock(AddonInfoRegistry.class);

        HttpURLConnection conn = Mockito.mock(HttpURLConnection.class);
        Mockito.when(conn.getResponseCode()).thenReturn(404);

        EclipseAddonService svc = new EclipseAddonService(ctx, registry) {
            @Override
            protected HttpURLConnection openHeadConnection(URL url, Proxy proxy) {
                return conn;
            }

            @Override
            protected URI readRepoUri() {
                return URI.create("https://example.com/");
            }
        };

        assertDoesNotThrow(svc::refreshSource);
    }
}
