package com.file;


import java.io.File;
import java.io.InputStream;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;

public class TikaParser {
	public TikaConfig tc;
	public Tika tika;

	public TikaParser() {
		tc = TikaConfig.getDefaultConfig();
		tika = new Tika(tc);
	}

	public File getResourceAsFile(String name) {
		File file = new File(name);
		if (null == file) {
			System.out.println("文件不存在 :" + name);
		}
		return file;
	}

	public InputStream getResourceAsStream(String name) {
		InputStream stream = this.getClass().getResourceAsStream(name);
		if (stream == null) {
			System.out.println("文件不存在 :" + name);
		}
		return stream;
	}

	public void assertContains(String needle, String haystack) {
		// assertTrue(needle + " not found in:\n" + haystack,
		// haystack.contains(needle));
	}
}
