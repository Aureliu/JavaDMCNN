// -*- tab-width: 4 -*-
package Jet.Parser;

/**
 * Dependency Analyzer using syntax tree.
 * 
 * @author <a href="oda@cs.nyu.edu">Akira ODA</a>
 */
public class DependencyAnalyzer {
	/**
	 * Resolves dependency for each terminal nodes.
	 * 
	 * @param tree
	 *            parse tree
	 */
	public void resolveTerminalDependency(ParseTreeNode tree) {
		resolveDependency(tree);
	}

	/**
	 * Determines head element which is under specified node.
	 * 
	 * @param node
	 *            of parse tree to be determine head
	 * @return head node
	 */
	private ParseTreeNode resolveDependency(ParseTreeNode node) {
		if (node.children == null) {
			return node;
		}

		ParseTreeNode[] headNodes = new ParseTreeNode[node.children.length];
		int head = node.head - 1;
		// assert head != -1 : "head should be set";
		if (head == -1) {
			return null;
		}

		for (int i = 0; i < headNodes.length; i++) {
			headNodes[i] = resolveDependency(node.children[i]);
		}

		if (headNodes[head] != null) {
			for (int i = 0; i < headNodes.length; i++) {
				if (i != head && headNodes[i] != null) {
					headNodes[i].ann.put("dep", headNodes[head].ann);
				}
			}
		}

		return headNodes[head];
	}
}
