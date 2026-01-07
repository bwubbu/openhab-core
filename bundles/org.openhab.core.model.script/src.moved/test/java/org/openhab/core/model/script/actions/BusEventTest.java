package org.openhab.core.model.script.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openhab.core.items.Item;
import org.openhab.core.types.State;

class BusEventTest {

    @Test
    void sendIfChanged_skipsWhenStateAlreadyMatches() {
        Item item = mock(Item.class);
        State state = mock(State.class);

        when(item.getName()).thenReturn("TestItem");
        when(item.getState()).thenReturn(state);
        when(state.toString()).thenReturn("ON");

        try (MockedStatic<BusEvent> mocked = mockStatic(BusEvent.class, CALLS_REAL_METHODS)) {
            // We only want to observe sendCommand; keep real methods for sendIfChanged
            mocked.when(() -> BusEvent.sendCommand(any(Item.class), anyString())).thenReturn("SENT");

            Object result = BusEvent.sendIfChanged(item, "ON");

            assertNull(result);
            mocked.verify(() -> BusEvent.sendCommand(any(Item.class), anyString()), never());
        }
    }

    @Test
    void sendIfChanged_delegatesWhenStateDiffers() {
        Item item = mock(Item.class);
        State state = mock(State.class);

        when(item.getName()).thenReturn("TestItem");
        when(item.getState()).thenReturn(state);
        when(state.toString()).thenReturn("OFF");

        try (MockedStatic<BusEvent> mocked = mockStatic(BusEvent.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> BusEvent.sendCommand(eq(item), eq("ON"))).thenReturn("SENT");

            Object result = BusEvent.sendIfChanged(item, "ON");

            assertEquals("SENT", result);

            mocked.verify(() -> BusEvent.sendCommand(eq(item), eq("ON")), times(1));
        }
    }
}
