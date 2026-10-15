package org.enggis.enventx.api.repository;

import org.springframework.data.domain.Sort;

public enum IdeaSort {
    NEWEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    OLDEST(Sort.by(Sort.Direction.ASC, "createdAt")),
    POPULAR(Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "createdAt"))),
    TITLE(Sort.by(Sort.Direction.ASC, "title").and(Sort.by(Sort.Direction.DESC, "createdAt")));

    private final Sort sort;

    IdeaSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    /** Lenient parse for query-string values; unknown or missing values fall back to NEWEST. */
    public static IdeaSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NEWEST;
        }
    }
}
