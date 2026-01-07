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
package org.openhab.core.library.types;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.Command;
import org.openhab.core.types.PrimitiveType;
import org.openhab.core.types.State;

/**
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class StringType implements PrimitiveType, State, Command {

    public static final StringType EMPTY = new StringType();

    private final String value;

    public StringType() {
        this("");
    }

    public StringType(@Nullable String value) {
        this.value = value != null ? value : "";
    }

    @Override
    public String toString() {
        return toFullString();
    }

    @Override
    public String toFullString() {
        return value;
    }

    public static StringType valueOf(@Nullable String value) {
        return new StringType(value);
    }

    @Override
    public String format(String pattern) {
        return String.format(pattern, value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        // Reflexive
        if (this == obj) {
            return true;
        }
        // Compare with another StringType
        if (obj instanceof StringType other) {
            return Objects.equals(this.value, other.value);
        }
        // Compare directly with raw String
        if (obj instanceof String) {
            return Objects.equals(this.value, obj);
        }
        // Any other type (or null) is not equal
        return false;
    }

    /**
     * Checks whether the underlying string value is empty.
     *
     * @return true if the wrapped string is empty, false otherwise
     */
    public boolean isEmpty() {
        return value.isEmpty();
    }

    /**
     * Returns the length of the wrapped string value.
     *
     * @return the number of characters in the wrapped string
     */
    public int length() {
        return value.length();
    }
}
