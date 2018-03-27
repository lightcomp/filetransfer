package com.lightcomp.ft.receiver.impl.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.receiver.impl.TransferContext;
import com.lightcomp.ft.xsd.v1.Dir;
import com.lightcomp.ft.xsd.v1.File;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.Item;

public class BeginTask implements Task {

    private final LinkedList<TransferDir> dirStack = new LinkedList<>();

    private final FileTransfer fileTransfer;

    private final TransferContext transferCtx;

    private ChecksumType checksumType;

    public BeginTask(FileTransfer fileTransfer, TransferContext transferCtx) {
        this.fileTransfer = fileTransfer;
        this.transferCtx = transferCtx;
    }

    @Override
    public void run() throws CanceledException {
        checksumType = ChecksumType.fromValue(fileTransfer.getChecksumType());

        Collection<Item> items = fileTransfer.getItems();
        if (items.isEmpty()) {
            throw new TransferException("Transfer must contain data");
        }

        Path transferDir = transferCtx.getTransferDir();
        for (Item item : items) {
            addItem(item, transferDir);
        }

        while (!dirStack.isEmpty()) {
            TransferDir td = dirStack.removeFirst();
            processDir(td);
        }
    }

    private void processDir(TransferDir td) throws CanceledException {
        try {
            Files.createDirectory(td.path);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to create directory").addParam("path", td.path).setCause(e).build();
        }
        for (Item item : td.items) {
            addItem(item, td.path);
        }
    }

    private void addItem(Item item, Path path) throws CanceledException {
        if (transferCtx.isCancelRequested()) {
            throw new CanceledException();
        }

        String name = Validate.notEmpty(item.getName());
        path = path.resolve(name);

        if (item.getClass() == File.class) {
            File file = (File) item;
            processFile(file, path);
        } else {
            Dir dir = (Dir) item;
            TransferDir td = new TransferDir(path, dir.getItems());
            dirStack.addLast(td);
        }
    }

    private void processFile(File file, Path path) {
        Validate.notEmpty(file.getFileId());
        Validate.isTrue(file.getSize() >= 0);

        TransferFile tf = new TransferFile(file.getFileId(), path, file.getSize(), file.getLastModified(), checksumType);
        transferCtx.addFile(tf);
    }

    private static class TransferDir {

        final Path path;

        final Collection<Item> items;

        TransferDir(Path path, Collection<Item> items) {
            this.path = path;
            this.items = items;
        }
    }
}
