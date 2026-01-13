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
package org.openhab.core.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AddonParameter}.
 *
 * @author Addin Suhaimi - Initial contribution
 */
@NonNullByDefault
class AddonParameterTest {

    @Test
    void constructorShouldStoreNameAndValue() {
        @NonNull
        AddonParameter parameter = new AddonParameter("param1", "value1");

        // Test that the constructor stores the values correctly
        assertEquals("param1", parameter.getName());
        assertEquals("value1", parameter.getValue());
    }

    @Test
    void constructorShouldTrimWhitespace() {
        @NonNull
        AddonParameter parameter = new AddonParameter("  param2  ", "  someValue  ");

        // Test that whitespace is trimmed
        assertEquals("param2", parameter.getName());
        assertEquals("someValue", parameter.getValue());
    }

    @Test
    void constructorShouldThrowExceptionForBlankName() {
        // Blank name should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new AddonParameter("   ", "value"));
    }

    @Test
    void constructorShouldThrowExceptionForBlankValue() {
        // Blank value should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new AddonParameter("param", "   "));
    }

    @Test
    void equalsAndHashCodeShouldWork() {
        @NonNull
        AddonParameter p1 = new AddonParameter("paramA", "valueA");
        @NonNull
        AddonParameter p2 = new AddonParameter("paramA", "valueA");
        @NonNull
        AddonParameter p3 = new AddonParameter("paramB", "valueB");

        // Same values -> equal
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());

        // Different values -> not equal
        assertTrue(!p1.equals(p3));
    }

    @Test
    void toStringShouldContainNameAndValue() {
        @NonNull
        AddonParameter parameter = new AddonParameter("paramX", "valX");
        @NonNull
        String str = parameter.toString();

        assertTrue(str.contains("paramX"));
        assertTrue(str.contains("valX"));
    }

    @Test
    void valueIsNeverNull() {
        @NonNull
        AddonParameter parameter = new AddonParameter("paramY", "valueY");
        assertNotNull(parameter.getValue());
    }
}
