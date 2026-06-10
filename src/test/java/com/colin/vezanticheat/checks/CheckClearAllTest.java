package com.colin.vezanticheat.checks;

import org.junit.Test;

public class CheckClearAllTest {

    @Test
    public void clearAllDoesNotThrow() {
        Check.clearAll();
        Check.clearAll();
    }
}
