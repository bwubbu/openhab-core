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
package org.openhab.core.voice.internal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.voice.DialogRegistration;
import org.openhab.core.voice.STTException;
import org.openhab.core.voice.STTListener;
import org.openhab.core.voice.STTService;
import org.openhab.core.voice.STTServiceHandle;

/**
 * Tests for {@link VoiceManagerImpl}.
 *
 * @author Addin Suhaimi - Initial contribution
 */
@NonNullByDefault
public class VoiceManagerImplTest {

    @AfterEach
    public void clearInterruptFlag() {
        Thread.interrupted(); // clears the interrupt flag if set
    }

    @Test
    public void transcribeShouldPreserveInterruptAndAbortHandle() throws Exception {
        VoiceManagerImpl vm = createVoiceManager();

        AudioFormat fmt = new AudioFormat(AudioFormat.CONTAINER_WAVE, "PCM_SIGNED", Boolean.TRUE, 16, 16 * 44100,
                44100L);

        AudioStream audioStream = new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return fmt;
            }

            @Override
            public int read() {
                return -1;
            }
        };

        TestSTTHandle handle = new TestSTTHandle();
        STTService stt = new TestSTTService("testStt", fmt, handle, false);

        registerSTTService(vm, stt);

        // Force CompletableFuture#get(...) to throw InterruptedException immediately.
        Thread.currentThread().interrupt();

        String result = vm.transcribe(audioStream, "testStt", Locale.US);

        assertThat(result, is(""));
        assertTrue(handle.aborted.get(), "Expected STTServiceHandle.abort() to be called on interrupt");
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupted flag should be preserved (re-interrupt)");
    }

    @Test
    public void transcribeShouldReturnEmptyWhenSTTServiceThrows() throws Exception {
        VoiceManagerImpl vm = createVoiceManager();

        AudioFormat fmt = new AudioFormat(AudioFormat.CONTAINER_WAVE, "PCM_SIGNED", Boolean.TRUE, 16, 16 * 44100,
                44100L);

        AudioStream audioStream = new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return fmt;
            }

            @Override
            public int read() {
                return -1;
            }
        };

        TestSTTHandle handle = new TestSTTHandle();
        STTService stt = new TestSTTService("badStt", fmt, handle, true);

        registerSTTService(vm, stt);

        String result = vm.transcribe(audioStream, "badStt", Locale.US);

        assertThat(result, is(""));
        assertTrue(!handle.aborted.get(), "Abort should not be called if recognize() fails immediately");
    }

    private VoiceManagerImpl createVoiceManager() {
        LocaleProvider localeProvider = mock(LocaleProvider.class);
        when(localeProvider.getLocale()).thenReturn(Locale.US);

        AudioManager audioManager = mock(AudioManager.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        TranslationProvider translationProvider = mock(TranslationProvider.class);

        StorageService storageService = mock(StorageService.class);
        @SuppressWarnings("unchecked")
        Storage<DialogRegistration> storage = (Storage<DialogRegistration>) mock(Storage.class);
        when(storageService.<DialogRegistration> getStorage(anyString(), any(ClassLoader.class))).thenReturn(storage);

        return new VoiceManagerImpl(localeProvider, audioManager, eventPublisher, translationProvider, storageService);
    }

    @SuppressWarnings("unchecked")
    private void registerSTTService(VoiceManagerImpl vm, STTService stt) throws Exception {
        Field f = VoiceManagerImpl.class.getDeclaredField("sttServices");
        f.setAccessible(true);
        Map<String, STTService> map = (Map<String, STTService>) f.get(vm);
        map.put(stt.getId(), stt);
    }

    private static final class TestSTTHandle implements STTServiceHandle {
        final AtomicBoolean aborted = new AtomicBoolean(false);

        @Override
        public void abort() {
            aborted.set(true);
        }
    }

    private static final class TestSTTService implements STTService {
        private final String id;
        private final AudioFormat format;
        private final STTServiceHandle handle;
        private final boolean throwOnRecognize;

        TestSTTService(String id, AudioFormat format, STTServiceHandle handle, boolean throwOnRecognize) {
            this.id = id;
            this.format = format;
            this.handle = handle;
            this.throwOnRecognize = throwOnRecognize;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getLabel(Locale locale) {
            return "Test STT";
        }

        @Override
        public Set<AudioFormat> getSupportedFormats() {
            return Set.of(format);
        }

        @Override
        public Set<Locale> getSupportedLocales() {
            return Set.of(Locale.US);
        }

        @Override
        public STTServiceHandle recognize(STTListener listener, AudioStream audioStream, Locale locale,
                Set<String> grammars) throws STTException {
            if (throwOnRecognize) {
                throw new STTException("boom");
            }
            return handle;
        }
    }
}
