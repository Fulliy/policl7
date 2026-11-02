package com.example.medicalsystem.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

final class PaginationHelper {

    static final int PAGE_SIZE = 10;

    private PaginationHelper() {
    }

    static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    static Pageable pageable(int page) {
        return PageRequest.of(normalizePage(page) - 1, PAGE_SIZE);
    }

    static <T> Page<T> pageFromList(List<T> items, int page) {
        Pageable pageable = pageable(page);
        int start = (int) pageable.getOffset();
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }
}
