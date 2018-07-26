package com.lightcomp.ft.core.send.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.apache.commons.lang3.Validate;

public class ListReader implements SourceItemReader {

	public static final ListReader EMPTY = new ListReader(Collections.emptyList());

	private final Collection<SourceItem> items;

	private Iterator<SourceItem> it;

	public ListReader(Collection<SourceItem> items) {
		this.items = items;
	}

	public ListReader(int capacity) {
		this(new ArrayList<>(capacity));
	}

	public ListReader() {
		this(new ArrayList<>());
	}

	public void addItem(SourceItem item) {
		Validate.notNull(item);

		items.add(item);
	}

	@Override
	public void open() {
		Validate.isTrue(it == null);

		it = items.iterator();
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
		it = null;
	}

	public static ListReader getSingleton(SourceItem item) {
		ListReader lr = new ListReader(0);
		lr.addItem(item);
		return lr;
	}
}
