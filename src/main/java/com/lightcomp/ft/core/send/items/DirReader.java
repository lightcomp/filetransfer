package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;

public class DirReader implements SourceItemReader {

	private final Path path;

	private Stream<SourceItem> res;

	private Iterator<SourceItem> it;

	public DirReader(Path path) {
		this.path = path;
	}

	@Override
	public void open() {
		Validate.isTrue(res == null);
		try {
			res = Files.list(path).<SourceItem>map(p -> {
				if (Files.isDirectory(p)) {
					return new BaseDir(p.getFileName().toString(), new DirReader(p));
				} else {
					return new SimpleFile(p);
				}
			});

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		it = res.iterator();
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public SourceItem getNext() {
		return it.next();
	}

	@Override
	public void close() {
		res.close();
		res = null;
		it = null;
	}

}
