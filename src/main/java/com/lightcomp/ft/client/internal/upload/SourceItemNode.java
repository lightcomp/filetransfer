package com.lightcomp.ft.client.internal.upload;

import com.lightcomp.ft.client.SourceItem;

public class SourceItemNode {

    private final String itemId;

    private final String parentItemId;

    private final SourceItem item;

    public SourceItemNode(String itemId, String parentItemId, SourceItem item) {
        this.itemId = itemId;
        this.parentItemId = parentItemId;
        this.item = item;
    }

    public String getItemId() {
        return itemId;
    }

    public String getParentItemId() {
        return parentItemId;
    }

    public SourceItem getItem() {
        return item;
    }
}