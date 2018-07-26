package com.lightcomp.ft.simple;

import java.nio.file.Path;

import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.core.send.items.DirReader;
import com.lightcomp.ft.core.send.items.SourceItemReader;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class UploadRequestImpl extends AbstractRequest implements UploadRequest {

	private final Path dataDir;

	public UploadRequestImpl(Path dataDir, GenericDataType data) {
		super(data);
		this.dataDir = dataDir;
	}

	@Override
	public SourceItemReader getRootItemsReader() {
		return new DirReader(dataDir);
	}
}
