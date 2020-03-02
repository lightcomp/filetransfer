package com.lightcomp.ft;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.lightcomp.ft.core.send.items.FileListBuilder;
import com.lightcomp.ft.core.send.items.FileListBuilder.TransferedFile;

public class FileListBuilderTest {

    private static final String TEST_FILE_NORMAL = "testRemoveDublicates.txt";

    private static final String TEST_FILE_EXCEPTION = "testRemoveDublicatesException.txt";

    @Test
    public void testRemoveDublicates() throws Exception {
        List<TransferedFile> tfs = new ArrayList<>();
        tfs.add(new TransferedFile("komponenty/a.txt", "objects/komponenty/a.txt"));
        tfs.add(new TransferedFile("komponenty/b.txt", "objects/komponenty/b.txt"));
        tfs.add(new TransferedFile("komponenty/b.txt", "objects/komponenty/b.txt"));
    
        FileListBuilder flb = new FileListBuilder(tfs);
        flb.writeFileList(Paths.get(TEST_FILE_NORMAL));
        List<String> lines = Files.lines(Paths.get(TEST_FILE_NORMAL)).collect(Collectors.toList());
        Assert.assertTrue(lines.size() == 4);
    
        Files.delete(Paths.get(TEST_FILE_NORMAL));
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveDublicatesIllegalStateException() throws Exception {
        List<TransferedFile> tfsEx = new ArrayList<>();
        tfsEx.add(new TransferedFile("komponenty/a.txt", "objects/komponenty/a.txt"));
        tfsEx.add(new TransferedFile("komponenty/a.txt", "objects/komponenty/a1.txt"));

        FileListBuilder flb = new FileListBuilder(tfsEx);
        try {
        	flb.writeFileList(Paths.get(TEST_FILE_EXCEPTION));
        } finally {
        	Files.delete(Paths.get(TEST_FILE_EXCEPTION));
        }
    }

}
