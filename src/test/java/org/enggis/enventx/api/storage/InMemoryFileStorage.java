package org.enggis.enventx.api.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryFileStorage implements FileStorage {

    public final Map<String, byte[]> objects = new LinkedHashMap<>();
    public final List<String> deleted = new ArrayList<>();

    @Override
    public void put(String key, InputStream data, long size, String contentType) {
        try {
            objects.put(key, data.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration validFor, String downloadName) {
        return "https://storage.test/" + key + "?ttl=" + validFor.toSeconds() + "&name=" + downloadName;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        deleted.add(key);
    }
}
