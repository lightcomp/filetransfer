package com.lightcomp.ft.simple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.core.send.items.SourceItem;

/**
 * Directory in memory.
 * 
 * Used mainly for tests.
 * Items have to be added manually. 
 */
public class MemoryDir implements SourceDir {
	
	String name;
	
	List<SourceItem> items = new ArrayList<>();
	
	public MemoryDir(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isDir() {
		return true;
	}

	@Override
	public SourceDir asDir() {
		return this;
	}

	@Override
	public SourceFile asFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<SourceItem> getItemIterator() {
		return items.iterator();
	}

	public void addChild(SourceItem item) {
		items.add(item);		
	}

	public void addChildren(Collection<SourceItem> its) {
		items.addAll(its);		
	}

}
