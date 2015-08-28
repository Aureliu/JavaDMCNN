// -*- tab-width: 4 -*-
package Jet.Format;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import Jet.Parser.ParseTreeNode;

public class PTBWriter {
	private static final Map<String, String> TRANSFORM_TABLE;

	static {
		TRANSFORM_TABLE = new HashMap<String, String>();
		TRANSFORM_TABLE.put("(", "-LRB-");
		TRANSFORM_TABLE.put("{", "-LCB-");
		TRANSFORM_TABLE.put("[", "-LSB-");
		TRANSFORM_TABLE.put(")", "-RRB-");
		TRANSFORM_TABLE.put("}", "-RCB-");
		TRANSFORM_TABLE.put("]", "-RSB-");
	}

	public void save(ParseTreeNode tree, Writer out) throws IOException {
		out.write('(');
		out.write(buildTagName(tree));
		out.write(' ');

		if (tree.children != null && tree.children.length != 0) {
			// non terminal
			for (ParseTreeNode child : tree.children) {
				save(child, out);
				out.write(' ');
			}
		} else {
			// terminal
			out.write(escape(tree.word));
		}

		out.write(')');
	}

	/**
	 * Builds tag name.
	 * @param node
	 * @return
	 */
	private String buildTagName(ParseTreeNode node) {
		StringBuilder buffer = new StringBuilder();
		buffer.append(node.category.toString().toUpperCase());
		if (node.head != 0) {
			buffer.append('-');
			buffer.append(node.head);
		}

		return buffer.toString();
	}

	/**
	 * Escape string to write in Penn Treebank bracket format.
	 * @param str
	 * @return
	 */
	private String escape(String str) {
		StringBuilder buffer = new StringBuilder();
		int length = str.length();

		for (int i = 0; i < length; i++) {
			char ch = str.charAt(i);

			if (ch == '*' || ch == '/') {
				buffer.append('\\');
			}
			buffer.append(ch);
		}

		String result = buffer.toString();
		if (TRANSFORM_TABLE.containsKey(result)) {
			return TRANSFORM_TABLE.get(result);
		} else {
			return result;
		}
	}
}
