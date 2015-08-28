package Jet.Parser;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;

import Jet.Format.InvalidFormatException;
import Jet.Format.PTBReader;
import Jet.Format.Treebank;

public class HeadRuleTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(HeadRuleTest.class);
	}

	@Test
	public void testFindHead() throws Exception {
		String source = "S        right-to-left  VP S SBAR ADJP UCP SINV SBARQ SQ NP";
		HeadRule rule = HeadRule.getRule(new StringReader(source));
	
		String parseTreeSource = "(S (NP (DT This)) (VP (VBZ is) (NP (DT a) (NN pen))) (. .))";
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(parseTreeSource));
		ParseTreeNode tree = treebank.getParseTree(0);
		
		int head = rule.getHead(tree);
		assertEquals(2, head);
	}
	
	@Test
	public void testApplyRule() throws Exception {
		String ruleString = "TOP      left-to-right  S SBAR ADJP UCP SINV SBARQ SQ NP\n"
			 + "S        right-to-left  VP S SBAR ADJP UCP SINV SBARQ SQ NP\n"
			 + "NP       right-to-left  NP NN NNS NNP NNPS NAC EX $ CD QP PRP VBG JJ JJR JJS ADJP FW RB DT RBR RBS SYM PRP$\n"
			 + "VP       right-to-left  VBN VBD MD VBZ VBP VP VB VBG\n";
		
		String source = "(S (NP (DT This)) (VP (VBZ is) (NP (DT a) (NN pen))) (. .))";
		HeadRule headRule = HeadRule.getRule(new StringReader(ruleString));
		
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		ParseTreeNode tree = treebank.getParseTree(0);
		headRule.apply(tree);
		assertEquals(2, tree.head);
		assertEquals(1, tree.children[0].head);
		assertEquals(1, tree.children[1].head);
		assertEquals(2, tree.children[1].children[1].head);
	}
	
	@Test
	public void testApplyRule2() throws Exception {
		String source = "( (S (NP (DT The) (NN company) (NN president)) (VP (VBD had) (NP (DT a) (JJ private) (NN plane))) (. .)))";
		HeadRule rule = HeadRule.getDefaultRule();
		
		ParseTreeNode tree = getParseTree(source);
		rule.apply(tree);
		assertEquals(2, tree.head);
		assertEquals(3, tree.children[0].head);
		assertEquals(1, tree.children[1].head);
		assertEquals(3, tree.children[1].children[1].head);
	}
	
	@Test
	public void testS1Node() throws Exception {
		String source = "(S1 (S (NP (PRP You)) (VP (MD should) (RB n't) (VP (VB depend) (PP (IN on) (NP (JJ other) (NNS people))))) (. .)))";
		ParseTreeNode tree = getParseTree(source);
		HeadRule headRule = HeadRule.getDefaultRule();
		
		headRule.apply(tree);
		assertEquals(1, tree.head);
		assertEquals(2, tree.children[0].head);
		
		ParseTreeNode node = tree.children[0];
		assertEquals(1, node.children[0].head);
		assertEquals(1, node.children[1].head);
		assertEquals(1, node.children[1].children[2].head);
		assertEquals(1, node.children[1].children[2].children[1].head);
		assertEquals(2, node.children[1].children[2].children[1].children[1].head);
	}
	
	private static ParseTreeNode getParseTree(String source) throws IOException, InvalidFormatException {
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		return treebank.getParseTree(0);
	}
}
