// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import Jet.Util.IOUtils;

public class SimpleClassHierarchyResolver implements ClassHierarchyResolver {
	private Map<String, Set<String>> subClassMap = new HashMap<String, Set<String>>();

	private SimpleClassHierarchyResolver(Map<String, Set<String>> subClassMap) {
		this.subClassMap = subClassMap;
	}

	public boolean isSubClassOf(String target, String className) {
		if (className == target || className.equals(target)) {
			return true;
		}
		
		Set<String> entry = subClassMap.get(className);
		if (entry == null) {
			return false;
		} else {
			return entry.contains(target);
		}
	}

	public static ClassHierarchyResolver getInstance(Reader in) throws IOException {
		if (in instanceof BufferedReader) {
			return getInstance((BufferedReader) in);
		} else {
			return getInstance(new BufferedReader(in));
		}
	}

	public static ClassHierarchyResolver getInstance(File file) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(file));
			return getInstance(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	private static ClassHierarchyResolver getInstance(BufferedReader in)
			throws IOException {
		String line;
		Stack<Set<String>> stack = new Stack<Set<String>>();
		Map<String, Set<String>> subClassMap = new HashMap<String, Set<String>>();

		while ((line = in.readLine()) != null) {
			if (line.startsWith("#")) {
				continue;
			}

			String name = line.trim().intern();
			int level = countHeadingSpace(line);

			if (subClassMap.containsKey(name)) {
				throw new RuntimeException(name + " appeared twice.");
			}
			if (level > stack.size()) {
				throw new RuntimeException("invalid indent");
			}

			while (stack.size() > level) {
				stack.pop();
			}

			Set<String> newEntry = new HashSet<String>();
			subClassMap.put(name, newEntry);
			stack.push(newEntry);

			for (Set<String> entry : stack) {
				entry.add(name);
			}
		}

		return new SimpleClassHierarchyResolver(subClassMap);
	}

	private static int countHeadingSpace(String str) {
		int i = 0;
		while (i < str.length() && Character.isWhitespace(str.charAt(i))) {
			i++;
		}
		return i;
	}
}
