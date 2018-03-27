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

    private void createItems(List<Item> target) throws CanceledException {
        dirItemsStack.clear();
        lastFileId = 0;

        Collection<SourceItem> source = transferCtx.getSourceItems();
        DirItems dirItems = new DirItems(source, target);
        dirItemsStack.add(dirItems);

        while (!dirItemsStack.isEmpty()) {
            dirItems = dirItemsStack.removeFirst();
            convertDirItems(dirItems);
        }
    }

    private void convertDirItems(DirItems dirItems) throws CanceledException {
        for (SourceItem si : dirItems.source) {
            if (transferCtx.isCancelRequested()) {
                throw new CanceledException();
            }
            Item ti;
            if (si.isDir()) {
                ti = convertDir(si.asDir());
            } else {
                ti = convertFile(si.asFile());
            }
            dirItems.target.add(ti);
        }
    }

    protected Dir convertDir(SourceDir sd) {
        Dir dir = new Dir();
        dir.setName(sd.getName());

        Validate.notEmpty(dir.getName());

        DirItems dirItems = new DirItems(sd.getItems(), dir.getItems());
        dirItemsStack.addLast(dirItems);

        return dir;
    }

    protected File convertFile(SourceFile sf) {
        String fileId = Integer.toString(++lastFileId);

        File file = new File();
        file.setFileId(fileId);
        file.setName(sf.getName());
        file.setSize(sf.getSize());
        file.setLastModified(sf.getLastModified());

        Validate.notBlank(file.getName(), "Empty file name");
        Validate.isTrue(file.getSize() >= 0, "neg");

        return file;
    }

    private static class DirItems {

        final Collection<SourceItem> source;

        final Collection<Item> target;

        DirItems(Collection<SourceItem> source, Collection<Item> target) {
            this.source = source;
            this.target = target;
        }
    }
}
