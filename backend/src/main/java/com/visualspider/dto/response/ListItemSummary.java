package com.visualspider.dto.response;

import com.visualspider.entity.ListItem;
import com.visualspider.enums.ItemStatus;

public record ListItemSummary(
        Long id,
        Long listPageId,
        String detailUrl,
        ItemStatus status,
        String errorMessage
) {
    public static ListItemSummary from(ListItem i) {
        return new ListItemSummary(
                i.getId(),
                i.getListPage() == null ? null : i.getListPage().getId(),
                i.getDetailUrl(),
                i.getStatus(),
                i.getErrorMessage()
        );
    }
}