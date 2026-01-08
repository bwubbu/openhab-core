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
package org.openhab.core.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.voice.text.HumanLanguageInterpreter;

/**
 * Describes dialog configured services and options.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public record DialogContext(@Nullable KSService ks, @Nullable String keyword, STTService stt, TTSService tts,
        @Nullable Voice voice, List<HumanLanguageInterpreter> hlis, AudioSource source, AudioSink sink, Locale locale,
        String dialogGroup, @Nullable String locationItem, @Nullable String listeningItem,
        @Nullable String listeningMelody) {

    /**
     * Builder for {@link DialogContext}
     * Allows to describe a dialog context without requiring the involved services to be loaded
     */
    public static class Builder {
        // services
        private @Nullable AudioSource source;
        private @Nullable AudioSink sink;
        private @Nullable KSService ks;
        private @Nullable STTService stt;
        private @Nullable TTSService tts;
        private @Nullable Voice voice;
        private List<HumanLanguageInterpreter> hlis = List.of();
        // options
        private String dialogGroup = "default";
        private @Nullable String locationItem;
        private @Nullable String listeningItem;
        private @Nullable String listeningMelody;
        private String keyword;
        private Locale locale;

        /**
         * Creates a new Builder instance.
         *
         * @param keyword the keyword for keyword spotting
         * @param locale the locale for the dialog
         */
        public Builder(String keyword, Locale locale) {
            this.keyword = keyword;
            this.locale = locale;
        }

        /**
         * Sets the audio source for the dialog.
         *
         * @param source the audio source, can be null
         * @return this builder instance
         */
        public Builder withSource(@Nullable AudioSource source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the audio sink for the dialog.
         *
         * @param sink the audio sink, can be null
         * @return this builder instance
         */
        public Builder withSink(@Nullable AudioSink sink) {
            this.sink = sink;
            return this;
        }

        /**
         * Sets the keyword spotting service for the dialog.
         *
         * @param service the keyword spotting service, can be null
         * @return this builder instance
         */
        public Builder withKS(@Nullable KSService service) {
            if (service != null) {
                this.ks = service;
            }
            return this;
        }

        /**
         * Sets the speech-to-text service for the dialog.
         *
         * @param service the speech-to-text service, can be null
         * @return this builder instance
         */
        public Builder withSTT(@Nullable STTService service) {
            if (service != null) {
                this.stt = service;
            }
            return this;
        }

        /**
         * Sets the text-to-speech service for the dialog.
         *
         * @param service the text-to-speech service, can be null
         * @return this builder instance
         */
        public Builder withTTS(@Nullable TTSService service) {
            if (service != null) {
                this.tts = service;
            }
            return this;
        }

        /**
         * Sets a single human language interpreter for the dialog.
         *
         * @param service the human language interpreter, can be null
         * @return this builder instance
         */
        public Builder withHLI(@Nullable HumanLanguageInterpreter service) {
            if (service != null) {
                this.hlis = List.of(service);
            }
            return this;
        }

        /**
         * Sets the human language interpreters for the dialog from a collection.
         *
         * @param services the collection of human language interpreters
         * @return this builder instance
         */
        public Builder withHLIs(Collection<HumanLanguageInterpreter> services) {
            return withHLIs(new ArrayList<>(services));
        }

        /**
         * Sets the human language interpreters for the dialog from a list.
         *
         * @param services the list of human language interpreters
         * @return this builder instance
         */
        public Builder withHLIs(List<HumanLanguageInterpreter> services) {
            if (!services.isEmpty()) {
                this.hlis = services;
            }
            return this;
        }

        /**
         * Sets the keyword for keyword spotting.
         *
         * @param keyword the keyword, can be null or blank (will be ignored if blank)
         * @return this builder instance
         */
        public Builder withKeyword(@Nullable String keyword) {
            if (keyword != null && !keyword.isBlank()) {
                this.keyword = keyword;
            }
            return this;
        }

        /**
         * Sets the voice for text-to-speech.
         *
         * @param voice the voice, can be null
         * @return this builder instance
         */
        public Builder withVoice(@Nullable Voice voice) {
            if (voice != null) {
                this.voice = voice;
            }
            return this;
        }

        /**
         * Sets the dialog group name.
         *
         * @param dialogGroup the dialog group name, can be null
         * @return this builder instance
         */
        public Builder withDialogGroup(@Nullable String dialogGroup) {
            if (dialogGroup != null) {
                this.dialogGroup = dialogGroup;
            }
            return this;
        }

        /**
         * Sets the location item for the dialog.
         *
         * @param locationItem the location item name, can be null
         * @return this builder instance
         */
        public Builder withLocationItem(@Nullable String locationItem) {
            if (locationItem != null) {
                this.locationItem = locationItem;
            }
            return this;
        }

        /**
         * Sets the listening item for the dialog.
         *
         * @param listeningItem the listening item name, can be null
         * @return this builder instance
         */
        public Builder withListeningItem(@Nullable String listeningItem) {
            if (listeningItem != null) {
                this.listeningItem = listeningItem;
            }
            return this;
        }

        /**
         * Sets the listening melody for the dialog.
         *
         * @param listeningMelody the listening melody, can be null
         * @return this builder instance
         */
        public Builder withMelody(@Nullable String listeningMelody) {
            if (listeningMelody != null) {
                this.listeningMelody = listeningMelody;
            }
            return this;
        }

        /**
         * Sets the locale for the dialog.
         *
         * @param locale the locale, can be null
         * @return this builder instance
         */
        public Builder withLocale(@Nullable Locale locale) {
            if (locale != null) {
                this.locale = locale;
            }
            return this;
        }

        /**
         * Creates a new {@link DialogContext}
         *
         * @return a {@link DialogContext} with the configured components and options
         * @throws IllegalStateException if a required dialog component is missing
         */
        public DialogContext build() throws IllegalStateException {
            KSService ksService = ks;
            STTService sttService = stt;
            TTSService ttsService = tts;
            List<HumanLanguageInterpreter> hliServices = hlis;
            AudioSource audioSource = source;
            AudioSink audioSink = sink;
            if (sttService == null || ttsService == null || hliServices.isEmpty() || audioSource == null
                    || audioSink == null) {
                List<String> errors = new ArrayList<>();
                if (sttService == null) {
                    errors.add("missing stt service");
                }
                if (ttsService == null) {
                    errors.add("missing tts service");
                }
                if (hliServices.isEmpty()) {
                    errors.add("missing interpreters");
                }
                if (audioSource == null) {
                    errors.add("missing audio source");
                }
                if (audioSink == null) {
                    errors.add("missing audio sink");
                }
                throw new IllegalStateException("Cannot build dialog context: " + String.join(", ", errors) + ".");
            } else {
                return new DialogContext(ksService, keyword, sttService, ttsService, voice, hliServices, audioSource,
                        audioSink, locale, dialogGroup, locationItem, listeningItem, listeningMelody);
            }
        }
    }
}
