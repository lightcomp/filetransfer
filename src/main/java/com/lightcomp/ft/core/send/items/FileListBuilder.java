package com.lightcomp.ft.core.send.items;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Trida pro vytvoreni souboru s popisem odesilanych souboru pres FileTransfer pomoci tridy FileListReader
 */
public class FileListBuilder {
    
    /**
     * Seznam odesilanych souboru
     */
    final List<TransferedFile> fileList;

    /**
     * Zanoreni v adresarove strukture
     */
    final ArrayDeque<Path> stack = new ArrayDeque<>();
    
    public FileListBuilder(List<TransferedFile> fileList) {
        this.fileList = fileList;
    }
    
    public void writeFileList(Path path) throws Exception {
                
        try (Writer writer = Files.newBufferedWriter(path)) {
            
            // seradim lexikograficky podle cesty vytvareneho souboru
            fileList.sort((f1,f2)->{return f1.getRemoteFile().compareTo(f2.getRemoteFile());});

            int i = 1;
            while (i < fileList.size()) {
                if (fileList.get(i - 1).getRemoteFile().equals(fileList.get(i).getRemoteFile())) {
                    if (fileList.get(i - 1).getLocalFile().equals(fileList.get(i).getLocalFile())) {
                        fileList.remove(i);
                    } else {
                        throw new IllegalStateException("Remote file duplicity with different local file");
                    }
                } else {
                    i++;
                }
            }

            for(TransferedFile file:fileList) {
                Path tmp = Paths.get(file.getRemoteFile());
                int dirParts = tmp.getNameCount() - 1;
                int stackSize = stack.size();            
                int depth = Math.min(dirParts, stackSize);            
                Iterator<Path> it = stack.iterator();
                int index = 0;
                // zjistim kam az se shoduje adresarova struktura s predchozim souborem, index ukazuje prvni neshodujici se adresar
                for(;index<depth;index++) {
                    Path dir = it.next();
                    if ( !tmp.getName(index).equals(dir) ) {                        
                        break;
                    }                
                }
           
                // uzavre jiz nepouzivane adresare
                for(int closeIndex=index;closeIndex<stackSize;closeIndex++) {
                    Path dirPath = stack.removeLast();
                    writer.append("E|").append(dirPath.toString()).append(System.lineSeparator());
                }
                                
                // otevre nove vytvorene adresare
                for(int openIndex=index;openIndex<dirParts;openIndex++) {
                    Path dir = tmp.getName(openIndex);
                    stack.add(dir);
                    writer.append("D|").append(dir.toString()).append(System.lineSeparator());
                }
                                
                // write file
                writer.append("F|").append(tmp.getFileName().toString()).append("|").append(file.getLocalFile()).append(System.lineSeparator());                
            }
            
            // uzavre vsechny adresare
            while(!stack.isEmpty()) {
                Path dirPath = stack.removeLast();
                writer.append("E|").append(dirPath.toString()).append(System.lineSeparator());
            }
        }        
    }
    

    public static class TransferedFile {
        
        /**
         * cesta vzdalene vytvarenenho souboru
         */
        final String remoteFile;
        
        /**
         * cesta k lokalnimu souboru
         */
        final String localFile;
        
        public TransferedFile(String remoteFile, String localFile) {
            this.remoteFile = remoteFile;
            this.localFile = localFile;
        }

        public String getRemoteFile() {
            return remoteFile;
        }

        public String getLocalFile() {
            return localFile;
        }
        
    }

    public static void main(String [] args) throws Exception {
        List<TransferedFile> tfs = new ArrayList<>();
        tfs.add(new TransferedFile("komponenty/a.txt", "objects/komponenty/a.txt"));
        tfs.add(new TransferedFile("komponenty/b.txt", "objects/komponenty/b.txt"));
        tfs.add(new TransferedFile("komponenty/x/c.txt", "objects/komponenty/c.txt"));
        tfs.add(new TransferedFile("komponenty/x/d.txt", "objects/komponenty/d.txt"));
        tfs.add(new TransferedFile("konvertovane/komponenty/a.txt", "konvertovane/komponenty/a.txt"));
        FileListBuilder flb = new FileListBuilder(tfs);
        flb.writeFileList(Paths.get("test.txt"));
    }

}
