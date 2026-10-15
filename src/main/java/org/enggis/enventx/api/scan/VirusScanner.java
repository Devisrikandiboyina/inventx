package org.enggis.enventx.api.scan;

import java.io.InputStream;

public interface VirusScanner {

    /** Throws 422 FILE_INFECTED for a detection and 503 SCANNER_UNAVAILABLE when the scan cannot be completed. */
    void scan(InputStream data);

    static VirusScanner disabled() {
        return data -> {
        };
    }
}