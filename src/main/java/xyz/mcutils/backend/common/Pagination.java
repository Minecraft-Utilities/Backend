package xyz.mcutils.backend.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.exception.impl.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Pagination<T> {
    private int itemsPerPage = 0;
    private long totalItems = 0;

    public Pagination<T> setItemsPerPage(int perPage) {
        this.itemsPerPage = perPage;
        return this;
    }

    public Pagination<T> setTotalItems(long totalItems) {
        this.totalItems = totalItems;
        return this;
    }

    /**
     * Gets the items for this page.
     *
     * @param page    the page to get the items on
     * @param fetcher the fetcher to fetch the items
     * @return the page
     */
    public Page<T> getPage(int page, Function<PageCallback, List<T>> fetcher) {
        int skip = (page - 1) * this.itemsPerPage;
        if (this.itemsPerPage == 0 || skip >= this.totalItems || page < 1) {
            throw new BadRequestException("Invalid or unknown page '%s'".formatted(page));
        }

        return new Page<>(
                fetcher.apply(new PageCallback(this.itemsPerPage, skip)),
                this.totalItems,
                this.itemsPerPage,
                (int) ((this.totalItems + this.itemsPerPage - 1) / this.itemsPerPage)
        );
    }

    /**
     * Returns an empty page
     *
     * @return the empty page
     */
    public Page<T> empty() {
        return new Page<>(new ArrayList<>(), this.totalItems, this.itemsPerPage, 0);
    }

    @Getter
    @AllArgsConstructor
    public static class Page<T> {
        private List<T> items;
        private long totalItems;
        private int itemsPerPage;
        private int totalPages;
    }

    @Getter
    @AllArgsConstructor
    public static class PageCallback {
        private int limit;
        private int skip;
    }
}
