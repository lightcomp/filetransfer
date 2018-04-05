package com.lightcomp.ft.client.internal.upload;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import com.lightcomp.ft.client.SourceDir;
import com.lightcomp.ft.client.SourceItem;

public class SourceItemIterator implements Iterator<SourceItemNode> {

    private final LinkedList<SourceItemNode> dirStack = new LinkedList<>();

    private final Iterator<SourceItem> rootItems;

    private String currDirItemId;

    private Iterator<SourceItem> currDirIt;

    private int lastItemId;

    public SourceItemIterator(Iterator<SourceItem> rootItems) {
        this.rootItems = rootItems;
    }

    @Override
    public boolean hasNext() {
        return currDirIt.hasNext() || dirStack.size() > 0 || rootItems.hasNext();
    }

    @Override
    public SourceItemNode next() {
        if (currDirIt.hasNext()) {
            SourceItem item = currDirIt.next();
            return addItem(item, currDirItemId);
        }
        if (dirStack.size() > 0) {
            SourceItemNode dirNode = dirStack.removeFirst();
            changeCurrentDir(dirNode);
            return dirNode;
        }
        if (rootItems.hasNext()) {
            SourceItem item = rootItems.next();
            return addItem(item, null);
        }
        throw new NoSuchElementException();
    }

    private void changeCurrentDir(SourceItemNode dirNode) {
        SourceDir dir = dirNode.getItem().asDir();
        currDirItemId = dirNode.getItemId();
        currDirIt = dir.getItemIterator();
    }

    private SourceItemNode addItem(SourceItem item, String parentItemId) {
        SourceItemNode itemNode = new SourceItemNode(getNextItemId(), parentItemId, item);
        if (item.isDir()) {
            dirStack.addLast(itemNode);
        }
        return itemNode;
    }

    private String getNextItemId() {
        return Integer.toString(++lastItemId);
    }
}
