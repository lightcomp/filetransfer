package com.lightcomp.ft.sender.impl.phase;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.sender.SourceDir;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.sender.SourceItem;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.xsd.v1.Dir;
import com.lightcomp.ft.xsd.v1.File;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.Item;

public class FileTransferBuilder {

    private final LinkedList<DirItems> dirItemsStack = new LinkedList<>();

    private final TransferContext transferCtx;

    private int lastFileId;

    public FileTransferBuilder(TransferContext transferCtx) {
        this.transferCtx = transferCtx;
    }

    public FileTransfer build() throws CanceledException {
        FileTransfer ft = new FileTransfer();

        ChecksumType cht = transferCtx.getChecksumType();
        ft.setChecksumType(cht.value());

        createItems(ft.getItems());

        return ft;
    }

    private void createItems(List<Item> targetItems) throws CanceledException {
        dirItemsStack.clear();
        lastFileId = 0;

        Collection<SourceItem> sourceItems = transferCtx.getSourceItems();
        DirItems dis = new DirItems(sourceItems, targetItems);
        dirItemsStack.add(dis);

        while (dirItemsStack.size() > 0) {
            dis = dirItemsStack.removeFirst();
            convertDirItems(dis);
        }
    }

    private void convertDirItems(DirItems dis) throws CanceledException {
        for (SourceItem si : dis.sourceItems) {
            if (transferCtx.isCancelPending()) {
                throw new CanceledException();
            }
            Item item;
            if (si.isDir()) {
                item = convertDir(si.asDir());
            } else {
                item = convertFile(si.asFile());
            }
            dis.targetItems.add(item);
        }
    }

    protected Dir convertDir(SourceDir sourceDir) {
        Dir dir = new Dir();
        dir.setName(sourceDir.getName());

        Validate.notEmpty(dir.getName());

        DirItems dis = new DirItems(sourceDir.getItems(), dir.getItems());
        dirItemsStack.addLast(dis);

        return dir;
    }

    protected File convertFile(SourceFile sf) {
        String fileId = Integer.toString(++lastFileId);

        File file = new File();
        file.setFileId(fileId);
        file.setName(sf.getName());
        file.setSize(sf.getSize());
        file.setLastModified(sf.getLastModified().toMillis());

        Validate.notBlank(file.getName());
        Validate.isTrue(file.getSize() >= 0);

        return file;
    }

    private static class DirItems {

        public final Collection<SourceItem> sourceItems;

        public final List<Item> targetItems;

        public DirItems(Collection<SourceItem> sourceItems, List<Item> targetItems) {
            this.sourceItems = sourceItems;
            this.targetItems = targetItems;
        }
    }
}
