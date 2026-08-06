package com.gyansys.intellirelease.api;

import java.util.List;

/**
 * The paging envelope every list endpoint returns.
 *
 * <p>One shape for all of them, because the frontend's data table is generic:
 * it drives any list endpoint without special-casing, which is only true while
 * they all answer "here are the items, here is how many there are in total".
 *
 * <p>Deliberately not Spring Data's {@code Page}. That type serialises around
 * twenty fields — {@code pageable}, {@code sort.sorted}, {@code first},
 * {@code numberOfElements} and so on — of which a UI uses three, and its JSON
 * shape has changed across Spring Data versions. Four fields we own beat
 * twenty we do not.
 *
 * @param items the page's contents
 * @param total total matching rows across every page, not just this one
 * @param page  zero-based page index
 * @param size  requested page size
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(items, total, page, size);
    }

    /**
     * Wraps a fully-materialised list — the honest answer for the curated
     * catalogues (interfaces, error patterns, knowledge articles), which are
     * loaded from data files at startup and are never large enough to page.
     */
    public static <T> PageResponse<T> ofAll(List<T> items) {
        return new PageResponse<>(items, items.size(), 0, items.size());
    }

    /** Applies offset paging to an in-memory list, clamping out-of-range requests. */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResponse<>(List.copyOf(all.subList(from, to)), all.size(), safePage, safeSize);
    }
}
