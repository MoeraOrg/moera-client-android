package org.moera.android.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilTest {

    @Test
    public void encodesRfc5987AttributeValue() {
        assertEquals("a%20b%2Ac%27%C3%A9.txt", Util.rfc5987Encode("a b*c'é.txt"));
    }

}
