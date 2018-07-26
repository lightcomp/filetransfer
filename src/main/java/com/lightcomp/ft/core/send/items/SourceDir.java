package com.lightcomp.ft.core.send.items;

/**
 * Transfer source directory.
 */
public interface SourceDir extends SourceItem {

	/**
	 * Return reader for directory children. not-null.
	 */
	SourceItemReader getChidrenReader();
}
