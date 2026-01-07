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
package org.openhab.core.karaf.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.events.EventPublisher;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Unit tests for {@link FeatureInstaller} focusing on thread interruption handling.
 *
 * @author Test Contributor
 */
@ExtendWith(MockitoExtension.class)
@NonNullByDefault
class FeatureInstallerTest {

    private @Mock @NonNullByDefault({}) ConfigurationAdmin configurationAdmin;
    private @Mock @NonNullByDefault({}) org.apache.karaf.features.FeaturesService featuresService;
    private @Mock @NonNullByDefault({}) org.apache.karaf.kar.KarService karService;
    private @Mock @NonNullByDefault({}) EventPublisher eventPublisher;

    private @NonNullByDefault({}) FeatureInstaller featureInstaller;

    @BeforeEach
    void setUp() {
        Map<String, Object> config = new HashMap<>();
        featureInstaller = new FeatureInstaller(configurationAdmin, featuresService, karService, eventPublisher,
                config);
    }

    @Test
    void waitForConfigUpdateEventShouldRestoreInterruptFlagWhenInterrupted() throws Exception {
        // Use reflection to access the private method
        Method waitMethod = FeatureInstaller.class.getDeclaredMethod("waitForConfigUpdateEvent");
        waitMethod.setAccessible(true);

        // Set paxCfgUpdated to false to ensure the loop runs
        java.lang.reflect.Field paxCfgUpdatedField = FeatureInstaller.class.getDeclaredField("paxCfgUpdated");
        paxCfgUpdatedField.setAccessible(true);
        paxCfgUpdatedField.setBoolean(featureInstaller, false);

        // Create a thread that will interrupt itself during the wait
        AtomicBoolean interruptFlagRestored = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread testThread = new Thread(() -> {
            try {
                // Invoke the wait method
                waitMethod.invoke(featureInstaller);
            } catch (Exception e) {
                fail("Exception during method invocation: " + e.getMessage());
            } finally {
                // Check if interrupt flag was restored
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                latch.countDown();
            }
        });

        testThread.start();

        // Wait a bit to ensure the thread enters the sleep
        Thread.sleep(150);

        // Interrupt the thread
        testThread.interrupt();

        // Wait for the thread to complete
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Thread should complete within 2 seconds");

        // Verify the interrupt flag was restored
        assertTrue(interruptFlagRestored.get(),
                "Interrupt flag should be restored after InterruptedException is caught");
    }

    @Test
    void waitForConfigUpdateEventShouldReturnEarlyWhenInterrupted() throws Exception {
        // Use reflection to access the private method
        Method waitMethod = FeatureInstaller.class.getDeclaredMethod("waitForConfigUpdateEvent");
        waitMethod.setAccessible(true);

        // Set paxCfgUpdated to false to ensure the loop runs
        java.lang.reflect.Field paxCfgUpdatedField = FeatureInstaller.class.getDeclaredField("paxCfgUpdated");
        paxCfgUpdatedField.setAccessible(true);
        paxCfgUpdatedField.setBoolean(featureInstaller, false);

        // Create a thread that will interrupt itself during the wait
        AtomicBoolean methodReturned = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();

        Thread testThread = new Thread(() -> {
            try {
                // Invoke the wait method
                waitMethod.invoke(featureInstaller);
                methodReturned.set(true);
            } catch (Exception e) {
                fail("Exception during method invocation: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        testThread.start();

        // Wait a bit to ensure the thread enters the sleep
        Thread.sleep(150);

        // Interrupt the thread
        testThread.interrupt();

        // Wait for the thread to complete
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Thread should complete within 2 seconds");

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Verify the method returned early (should be much less than 5 seconds)
        assertTrue(methodReturned.get(), "Method should return when interrupted");
        assertTrue(elapsedTime < 3000, "Method should return quickly after interruption, not wait full 5 seconds");
    }

    @Test
    void waitForConfigUpdateEventShouldCompleteNormallyWhenNotInterrupted() throws Exception {
        // Use reflection to access the private method
        Method waitMethod = FeatureInstaller.class.getDeclaredMethod("waitForConfigUpdateEvent");
        waitMethod.setAccessible(true);

        // Set paxCfgUpdated to true so the loop exits immediately
        java.lang.reflect.Field paxCfgUpdatedField = FeatureInstaller.class.getDeclaredField("paxCfgUpdated");
        paxCfgUpdatedField.setAccessible(true);
        paxCfgUpdatedField.setBoolean(featureInstaller, true);

        // Invoke the method - it should return immediately since paxCfgUpdated is true
        long startTime = System.currentTimeMillis();
        waitMethod.invoke(featureInstaller);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Verify it completed quickly (should not wait)
        assertTrue(elapsedTime < 100, "Method should return immediately when paxCfgUpdated is true");
    }
}
