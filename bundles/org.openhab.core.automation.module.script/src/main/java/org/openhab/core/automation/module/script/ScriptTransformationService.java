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
package org.openhab.core.automation.module.script;

import static org.openhab.core.automation.module.script.profile.ScriptProfileFactory.PROFILE_CONFIG_URI_PREFIX;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.module.script.profile.ScriptProfile;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.ConfigDescriptionBuilder;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.config.core.ConfigDescriptionRegistry;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.transform.Transformation;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationRegistry;
import org.openhab.core.transform.TransformationService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ScriptTransformationService} implements a {@link TransformationService} using any available script
 * language
 *
 * @author Jan N. Klug - Initial contribution
 * @author Florian Hotze - Implement script dependency tracking
 */
@NonNullByDefault
@Component(factory = "org.openhab.core.automation.module.script.transformation.factory", service = {
        TransformationService.class, ScriptTransformationService.class, ScriptDependencyTracker.Listener.class,
        ConfigOptionProvider.class, ConfigDescriptionProvider.class })
public class ScriptTransformationService implements TransformationService, ScriptDependencyTracker.Listener,
        ConfigOptionProvider, ConfigDescriptionProvider, RegistryChangeListener<Transformation> {
    public static final String SCRIPT_TYPE_PROPERTY_NAME = "openhab.transform.script.scriptType";
    public static final String OPENHAB_TRANSFORMATION_SCRIPT = "openhab-transformation-script-";

    private static final URI CONFIG_DESCRIPTION_TEMPLATE_URI = URI.create(PROFILE_CONFIG_URI_PREFIX + "SCRIPT");

    private static final Pattern INLINE_SCRIPT_CONFIG_PATTERN = Pattern.compile("\\|(?<inlineScript>.+)");

    private static final Pattern SCRIPT_CONFIG_PATTERN = Pattern.compile("(?<scriptUid>[^?]+)(?:\\?(?<params>.*))?");

    private final Logger logger = LoggerFactory.getLogger(ScriptTransformationService.class);

    private final String scriptType;
    private final URI profileConfigUri;

    private final Map<String, ScriptRecord> scriptCache = new ConcurrentHashMap<>();

    private final TransformationRegistry transformationRegistry;
    private final ScriptEngineManager scriptEngineManager;
    private final ConfigDescriptionRegistry configDescRegistry;

    private static final int MAX_SCRIPT_LENGTH = 200_000;
    private static final int MAX_INPUT_LENGTH = 200_000;

    @Activate
    public ScriptTransformationService(@Reference TransformationRegistry transformationRegistry,
            @Reference ConfigDescriptionRegistry configDescRegistry, @Reference ScriptEngineManager scriptEngineManager,
            Map<String, Object> config) {
        String configuredScriptType = ConfigParser.valueAs(config.get(SCRIPT_TYPE_PROPERTY_NAME), String.class);
        if (configuredScriptType == null) {
            throw new IllegalStateException(
                    "'" + SCRIPT_TYPE_PROPERTY_NAME + "' must not be null in service configuration");
        }

        this.transformationRegistry = transformationRegistry;
        this.configDescRegistry = configDescRegistry;
        this.scriptEngineManager = scriptEngineManager;
        this.scriptType = configuredScriptType;
        this.profileConfigUri = URI.create(PROFILE_CONFIG_URI_PREFIX + scriptType.toUpperCase());
        transformationRegistry.addRegistryChangeListener(this);
    }

    @Deactivate
    public void deactivate() {
        transformationRegistry.removeRegistryChangeListener(this);

        // cleanup script engines
        scriptCache.values().forEach(this::disposeScriptRecord);
    }

    @Override
    public @Nullable String transform(String function, String source) throws TransformationException {
        ParsedConfig parsed = parseFunction(function);

        ScriptRecord scriptRecord = Objects
                .requireNonNull(scriptCache.computeIfAbsent(parsed.scriptUid, k -> new ScriptRecord()));

        scriptRecord.lock().lock();
        try {
            ensureScriptLoaded(parsed, scriptRecord);
            ensureScriptTypeSupported(parsed.scriptUid);
            ScriptEngineContainer container = getOrCreateEngineContainer(parsed.scriptUid, function, scriptRecord);

            try {
                return evaluateScript(parsed, function, source, scriptRecord, container);
            } catch (IllegalStateException e) {
                // ISE thrown by JS Scripting if script engine already closed
                if ("The Context is already closed.".equals(e.getMessage())) {
                    logger.warn(
                            "Script engine context {} is already closed, this should not happen. Recreating script engine.",
                            parsed.scriptUid);
                    scriptCache.remove(parsed.scriptUid);
                    return transform(function, source);
                }
                throw e;
            }
        } finally {
            scriptRecord.lock().unlock();
        }
    }

    private ParsedConfig parseFunction(String function) throws TransformationException {
        Matcher inlineMatcher = INLINE_SCRIPT_CONFIG_PATTERN.matcher(function);
        if (inlineMatcher.matches()) {
            String inlineScript = inlineMatcher.group("inlineScript");

            if (inlineScript != null && inlineScript.length() > MAX_SCRIPT_LENGTH) {
                throw new TransformationException("Inline script is too large and exceeds safe execution limits.");
            }

            // prefix with | to avoid clashing with a real filename
            String scriptUid = "|" + Integer.toString(inlineScript.hashCode());
            return new ParsedConfig(scriptUid, inlineScript, null);
        }

        Matcher configMatcher = SCRIPT_CONFIG_PATTERN.matcher(function);
        if (!configMatcher.matches()) {
            throw new TransformationException("Invalid syntax for the script transformation: '" + function + "'");
        }

        String scriptUid = configMatcher.group("scriptUid");
        String params = configMatcher.group("params");
        return new ParsedConfig(scriptUid, null, params);
    }

    private void ensureScriptLoaded(ParsedConfig parsed, ScriptRecord scriptRecord) throws TransformationException {
        if (!scriptRecord.script().isBlank()) {
            return;
        }

        String loadedScript = "";
        if (parsed.inlineScript != null) {
            loadedScript = parsed.inlineScript;
        } else {
            Transformation transformation = transformationRegistry.get(parsed.scriptUid);
            if (transformation != null) {
                loadedScript = transformation.getConfiguration().getOrDefault(Transformation.FUNCTION, "");
            }
        }

        if (loadedScript.isBlank()) {
            throw new TransformationException("Could not get script for UID '" + parsed.scriptUid + "'.");
        }

        if (loadedScript.length() > MAX_SCRIPT_LENGTH) {
            throw new TransformationException("Script size exceeds safe execution limits.");
        }

        scriptRecord.setScript(loadedScript);
        scriptCache.put(parsed.scriptUid, scriptRecord);
    }

    private void ensureScriptTypeSupported(String scriptUid) throws TransformationException {
        if (scriptEngineManager.isSupported(scriptType)) {
            return;
        }

        // language has been removed, clear container and compiled scripts if found
        clearCache(scriptUid);
        throw new TransformationException(
                "Script type '" + scriptType + "' is not supported by any available script engine.");
    }

    private ScriptEngineContainer getOrCreateEngineContainer(String scriptUid, String function, ScriptRecord scriptRecord)
            throws TransformationException {

        if (scriptRecord.scriptEngineContainer() == null) {
            scriptRecord.setScriptEngineContainer(
                    scriptEngineManager.createScriptEngine(scriptType, OPENHAB_TRANSFORMATION_SCRIPT + scriptUid));
        }

        ScriptEngineContainer container = scriptRecord.scriptEngineContainer();
        if (container == null) {
            throw new TransformationException("Failed to create script engine container for '" + function + "'.");
        }
        return container;
    }

    private @Nullable String evaluateScript(ParsedConfig parsed, String function, String source, ScriptRecord scriptRecord,
            ScriptEngineContainer scriptEngineContainer) throws TransformationException {

        try {
            CompiledScript compiledScript = scriptRecord.compiledScript();

            ScriptEngine engine = compiledScript != null ? compiledScript.getEngine()
                    : scriptEngineContainer.getScriptEngine();
            ScriptContext executionContext = engine.getContext();

            validateInputSize(source);

            // Make input available to the script engine
            executionContext.setAttribute("input", source, ScriptContext.ENGINE_SCOPE);

            ArrayList<String> injectedParams = injectParams(parsed.params, parsed.scriptUid, executionContext);

            // compile the script here _after_ setting context attributes, so that the script engine
            // can bind the attributes as variables during compilation. This primarily affects jruby.
            compiledScript = compileIfPossible(compiledScript, scriptRecord, scriptEngineContainer);

            try {
                Object result = compiledScript != null ? compiledScript.eval() : engine.eval(scriptRecord.script());
                return result == null ? null : result.toString();
            } finally {
                removeInjectedParams(injectedParams, executionContext);
            }
        } catch (ScriptException e) {
            throw new TransformationException("Failed to execute script.", e);
        }
    }

    private void validateInputSize(@Nullable String source) throws TransformationException {
        if (source != null && source.length() > MAX_INPUT_LENGTH) {
            throw new TransformationException("Input payload is too large for script transformation.");
        }
    }

    private @Nullable ArrayList<String> injectParams(@Nullable String params, String scriptUid,
            ScriptContext executionContext) {

        if (params == null) {
            return null;
        }

        ArrayList<String> injectedParams = new ArrayList<>();
        for (String param : params.split("&")) {
            String[] splitString = param.split("=");
            if (splitString.length != 2) {
                logger.warn("Parameter '{}' does not consist of two parts for configuration UID {}, skipping.", param,
                        scriptUid);
                continue;
            }

            String key = URLDecoder.decode(splitString[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(splitString[1], StandardCharsets.UTF_8);

            executionContext.setAttribute(key, value, ScriptContext.ENGINE_SCOPE);
            injectedParams.add(key);
        }
        return injectedParams;
    }

    private void removeInjectedParams(@Nullable ArrayList<String> injectedParams, ScriptContext executionContext) {
        if (injectedParams == null) {
            return;
        }
        injectedParams.forEach(param -> executionContext.removeAttribute(param, ScriptContext.ENGINE_SCOPE));
    }

    private @Nullable CompiledScript compileIfPossible(@Nullable CompiledScript compiledScript, ScriptRecord scriptRecord,
            ScriptEngineContainer scriptEngineContainer) throws ScriptException {

        if (compiledScript != null) {
            return compiledScript;
        }

        ScriptEngine engine = scriptEngineContainer.getScriptEngine();
        if (engine instanceof Compilable compilable) {
            CompiledScript newCompiled = compilable.compile(scriptRecord.script());
            scriptRecord.setCompiledScript(newCompiled);
            return newCompiled;
        }

        return null;
    }

    @Override
    public void added(Transformation element) {
        clearCache(element.getUID());
    }

    @Override
    public void removed(Transformation element) {
        clearCache(element.getUID());
    }

    @Override
    public void updated(Transformation oldElement, Transformation element) {
        clearCache(element.getUID());
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (!uri.equals(profileConfigUri)) {
            return null;
        }

        if (ScriptProfile.CONFIG_TO_HANDLER_SCRIPT.equals(param) || ScriptProfile.CONFIG_TO_ITEM_SCRIPT.equals(param)
                || ScriptProfile.CONFIG_COMMAND_FROM_ITEM_SCRIPT.equals(param)
                || ScriptProfile.CONFIG_STATE_FROM_ITEM_SCRIPT.equals(param)) {
            return transformationRegistry.getTransformations(List.of(scriptType.toLowerCase())).stream()
                    .map(c -> new ParameterOption(c.getUID(), c.getLabel())).toList();
        }
        return null;
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(@Nullable Locale locale) {
        ConfigDescription configDescription = getConfigDescription(profileConfigUri, locale);
        if (configDescription != null) {
            return List.of(configDescription);
        }

        return List.of();
    }

    @Override
    public @Nullable ConfigDescription getConfigDescription(URI uri, @Nullable Locale locale) {
        if (!uri.equals(profileConfigUri)) {
            return null;
        }

        ConfigDescription template = configDescRegistry.getConfigDescription(CONFIG_DESCRIPTION_TEMPLATE_URI, locale);
        if (template == null) {
            return null;
        }
        return ConfigDescriptionBuilder.create(uri).withParameters(template.getParameters())
                .withParameterGroups(template.getParameterGroups()).build();
    }

    @Override
    public void onDependencyChange(String scriptId) {
        String scriptUid = scriptId.substring(OPENHAB_TRANSFORMATION_SCRIPT.length());
        ScriptRecord scriptRecord = scriptCache.get(scriptUid);
        if (scriptRecord != null) {
            logger.debug("Clearing script cache for script {}", scriptUid);
            clearCache(scriptUid);
        }
    }

    private void clearCache(String uid) {
        ScriptRecord scriptRecord = scriptCache.remove(uid);
        if (scriptRecord != null) {
            disposeScriptRecord(scriptRecord);
        }
    }

    private void disposeScriptRecord(ScriptRecord scriptRecord) {
        ScriptEngineContainer scriptEngineContainer = scriptRecord.scriptEngineContainer;
        if (scriptEngineContainer != null) {
            scriptEngineManager.removeEngine(scriptEngineContainer.getIdentifier());
        }
        scriptRecord.compiledScript = null;
    }

    private static final class ParsedConfig {
        final String scriptUid;
        final @Nullable String inlineScript;
        final @Nullable String params;

        ParsedConfig(String scriptUid, @Nullable String inlineScript, @Nullable String params) {
            this.scriptUid = scriptUid;
            this.inlineScript = inlineScript;
            this.params = params;
        }
    }


    private static final class ScriptRecord {
        // SonarLint java:S1104: do not expose mutable fields publicly
        private String script = "";
        private @Nullable ScriptEngineContainer scriptEngineContainer;
        private @Nullable CompiledScript compiledScript;
        private final Lock lock = new ReentrantLock();

        String script() {
            return script;
        }

        void setScript(String script) {
            this.script = script;
        }

        @Nullable ScriptEngineContainer scriptEngineContainer() {
            return scriptEngineContainer;
        }

        void setScriptEngineContainer(@Nullable ScriptEngineContainer scriptEngineContainer) {
            this.scriptEngineContainer = scriptEngineContainer;
        }

        @Nullable CompiledScript compiledScript() {
            return compiledScript;
        }

        void setCompiledScript(@Nullable CompiledScript compiledScript) {
            this.compiledScript = compiledScript;
        }

        Lock lock() {
            return lock;
        }
    }
}
