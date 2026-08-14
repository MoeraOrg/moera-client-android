package org.moera.android.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

public class WebClientCapabilitiesTest {

    @Test
    public void enablesNativeSelectionOnlyForExplicitVersion() throws JSONException {
        WebClientCapabilities capabilities = new WebClientCapabilities();

        capabilities.set("{\"nativeMediaUpload\":1,\"clientId\":\"browser-id\"}");

        assertTrue(capabilities.isNativeMediaUploadEnabled());
        assertEquals("browser-id", capabilities.getClientId());

        capabilities.set("{\"nativeMediaUpload\":2,\"clientId\":\"other\"}");

        assertFalse(capabilities.isNativeMediaUploadEnabled());
        assertNull(capabilities.getClientId());
    }

    @Test
    public void resetRequiresEveryDocumentToOptInAgain() throws JSONException {
        WebClientCapabilities capabilities = new WebClientCapabilities();
        capabilities.set("{\"nativeMediaUpload\":1,\"clientId\":\"browser-id\"}");
        long previousGeneration = capabilities.getWebClientGeneration();

        capabilities.reset();

        assertFalse(capabilities.isNativeMediaUploadEnabled());
        assertFalse(capabilities.isCurrentNativeDocument(previousGeneration));
        assertNull(capabilities.getClientId());
    }

}
