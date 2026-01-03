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
package org.openhab.core.types;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to parse strings into openHAB types (State or Command).
 * <p>
 * This class uses reflection to call the valueOf(String) method on the corresponding type class.
 * Refactored to reduce duplicate code, improve logging, and improve maintainability.
 * </p>
 * 
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public final class TypeParser {

    private final Logger logger = LoggerFactory.getLogger(TypeParser.class);

    private static final String CORE_LIBRARY_PACKAGE = "org.openhab.core.library.types.";
    private static final String VALUE_OF = "valueOf";

    /** Private constructor to prevent instantiation */
    private TypeParser() {
    }

    /**
     * Parses a string into a type instance.
     * 
     * @param typeName fully qualified type name or simple name like "StringType"
     * @param input string input to parse
     * @return Parsed type or null if parsing fails
     */
    public static @Nullable Type parseType(String typeName, String input) {
        TypeParser parser = new TypeParser(); 
        try {
            Class<?> stateClass = Class.forName(CORE_LIBRARY_PACKAGE + typeName);
            return parser.invokeValueOf(stateClass, input, Type.class);
        } catch (ClassNotFoundException e) {
            parser.logger.debug("Type class not found: {}", typeName, e);
        }
        return null;
    }

    /**
     * Parses a string into a State instance using a list of possible state types.
     * 
     * @param types List of possible State classes
     * @param s Input string
     * @return First matching State instance or null
     */
    public static @Nullable State parseState(List<Class<? extends State>> types, String s) {
        return parseGeneric(types, s, State.class);
    }

    /**
     * Parses a string into a Command instance using a list of possible command types.
     * 
     * @param types List of possible Command classes
     * @param s Input string
     * @return First matching Command instance or null
     */
    public static @Nullable Command parseCommand(List<Class<? extends Command>> types, String s) {
        return parseGeneric(types, s, Command.class);
    }

    /**
     * Generic parser that iterates through a list of classes and invokes valueOf(String).
     * Returns the first successfully parsed instance of type T.
     */
    private static <T> @Nullable T parseGeneric(List<Class<? extends T>> types, String s, Class<T> expectedClassType) {
        TypeParser parser = new TypeParser();
        for (Class<? extends T> type : types) {
            T value = parser.invokeValueOf(type, s, expectedClassType);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Invokes the static valueOf(String) method on the given class.
     * 
     * @param <T> Expected return type
     * @param typeClass Class to invoke valueOf on
     * @param input Input string
     * @param expectedClassType Expected class type
     * @return Parsed instance or null if parsing failed
     */
    private <T> @Nullable T invokeValueOf(Class<?> typeClass, String input, Class<T> expectedClassType) {
        try {
            Method valueOfMethod = typeClass.getMethod(VALUE_OF, String.class);
            Object result = valueOfMethod.invoke((@Nullable Object) null, input);
            // Ensures the result actually is a Type
            if (expectedClassType.isInstance(result)) {
                return expectedClassType.cast(result);
            }
        } catch (NoSuchMethodException e) {
            logger.debug("No valueOf(String) method in class: {}", typeClass.getName(), e);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
            logger.debug("Failed to invoke valueOf on class {} with input '{}'", typeClass.getName(), input, e);
        }
        return null;
    }
}
