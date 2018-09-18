package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public class PathUtils {

	public static final Path ROOT = Paths.get("");

	public static final Path SYS_TEMP;

	static {
		String sysTemp = System.getProperty("java.io.tmpdir");
		SYS_TEMP = Paths.get(sysTemp);
	}

	public static void deleteWithChildren(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	public static class CopyFileVisitor implements FileVisitor<Path> {

		private final CopyOption[] fileCopyOpts;

		private final boolean mergeFolders;

		private Path currDir;

		public CopyFileVisitor(Path targetDir, boolean replaceFiles, boolean mergeFolders) {
			this.fileCopyOpts = replaceFiles
					? new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING }
					: new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES };
			this.mergeFolders = mergeFolders;
			this.currDir = targetDir;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			Objects.requireNonNull(dir);
			Objects.requireNonNull(attrs);

			currDir = currDir.resolve(dir.getFileName());
			try {
				Files.createDirectory(currDir);
			} catch (FileAlreadyExistsException e) {
				if (!mergeFolders) {
					throw e;
				}
			}

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Objects.requireNonNull(file);
			Objects.requireNonNull(attrs);

			Path target = currDir.resolve(file.getFileName());
			Files.copy(file, target, fileCopyOpts);

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			Objects.requireNonNull(file);
			throw exc;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			Objects.requireNonNull(dir);
			if (exc != null) {
				throw exc;
			}

			currDir = currDir.getParent();

			return FileVisitResult.CONTINUE;
		}
	}
}