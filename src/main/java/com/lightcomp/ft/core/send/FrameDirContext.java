package com.lightcomp.ft.core.send;

import java.nio.file.Path;

import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.core.send.items.SourceItemReader;

class FrameDirContext implements SourceItemReader {

	private final String name;

	private final Path path;

	private final SourceItemReader reader;

	private boolean open;

	public FrameDirContext(String name, Path path, SourceItemReader reader) {
		this.name = name;
		this.path = path;
		this.reader = reader;
	}

	public String getName() {
		return name;
	}

	public Path getPath() {
		return path;
	}

	public boolean isOpen() {
		return open;
	}

	@Override
	public void open() {
		reader.open();
		open = true;
	}

	@Override
	public boolean hasNext() {
		return reader.hasNext();
	}

	@Override
	public SourceItem getNext() {
		return reader.getNext();
	}

	@Override
	public void close() {
		reader.close();
		open = false;
	}
}
