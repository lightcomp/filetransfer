package com.lightcomp.ft.core.send.items;

public class BaseDir implements SourceDir {

	private final String name;

	private final SourceItemReader childrenReader;

	public BaseDir(String name, SourceItemReader childrenReader) {
		this.name = name;
		this.childrenReader = childrenReader;
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
	public SourceItemReader getChidrenReader() {
		return childrenReader;
	}
	
	public static BaseDir getEmpty(String name) {
		return new BaseDir(name, ListReader.EMPTY);
	}
}
