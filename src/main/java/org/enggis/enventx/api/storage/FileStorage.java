package org.enggis.enventx.api.storage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorage {

    /** Stores the stream under {@code key} (relative to the configured source folder). */
    void put(String key, InputStream data, long size, String contentType);

    /** Short-lived GET URL; {@code downloadName} forces a download with that (sanitised) file name. */
    String presignedGetUrl(String key, Duration validFor, String downloadName);

    /** Best effort; a missing object is not an error. */
    void delete(String key);
}
