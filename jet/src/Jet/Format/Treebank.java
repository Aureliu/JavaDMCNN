// -*- tab-width: 4 -*-
package Jet.Format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Document;

public class Treebank {
	private Document document;

	private List<ParseTreeNode> parseTreeList;

	public Treebank(Document doc, List<ParseTreeNode> parseTreeList) {
		this.document = doc;
		this.parseTreeList = parseTreeList;
	}

	public Document getDocument() {
		return document;
	}

	public List<ParseTreeNode> getParseTreeList() {
		return parseTreeList;
	}

	public ParseTreeNode getParseTree(int i) {
		return parseTreeList.get(i);
	}
}
