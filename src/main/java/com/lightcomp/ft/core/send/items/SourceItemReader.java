package com.lightcomp.ft.core.send.items;

public interface SourceItemReader {

	void open();

	boolean hasNext();

	SourceItem getNext();

	void close();
}
