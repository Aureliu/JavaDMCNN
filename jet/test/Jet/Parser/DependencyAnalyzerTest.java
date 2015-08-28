package Jet.Parser;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import junit.framework.JUnit4TestAdapter;

import org.junit.Before;
import org.junit.Test;

import Jet.Format.InvalidFormatException;
import Jet.Format.PTBReader;
import Jet.Format.Treebank;
import Jet.Tipster.Annotation;


public class DependencyAnalyzerTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(DependencyAnalyzerTest.class);
	}
	
	private HeadRule headRule;
	private DependencyAnalyzer analyzer;

	@Before
	public void setUp() throws Exception {
		headRule = HeadRule.getDefaultRule();
		analyzer = new DependencyAnalyzer();
	}
	
	@Test
	public void testResolveTerminalDependency() throws Exception {
		String source = "( (S (NP (DT The) (NN company) (NN president)) (VP (VBD had) (NP (DT a) (JJ private) (NN plane))) (. .)))";
		Treebank treebank = load(source);
		ParseTreeNode tree = treebank.getParseTree(0);

		headRule.apply(tree);
		
		assertEquals(2, tree.head);
		
		analyzer.resolveTerminalDependency(tree);
		
		List<ParseTreeNode> terminals = getTerminalNodes(tree);
		
		List<Integer> headNumbers = getHeadNumbers(terminals);
		List<Integer> expectedHeadNumbers = Arrays.asList(2, 2, 3, -1, 6, 6, 3, 3);
		
		assertEquals(8, terminals.size());
		assertEquals(expectedHeadNumbers, headNumbers);
	}

	@Test
	public void testHeadNodeHasNoHead() throws Exception {
		String source = "( (S (CC And) (PP (IN for) (NP (PRP yourself))) (, ,) (SBAR (IN if) (S (NP (PRP you)) (ADVP (RB still)) (VP (AUX have) (S (VP (TO to) (VP (VB search) (PP (IN for) (NP (NP (NP (DT the) (NN letter)) (`` ``) (NX (NNP x)) ('' '')) (CC and) (NP (DT the) (NN character)))) (`` `) (SBAR (S (NP (`` `) (NP (POS ')) ('' ') (PP (IN before) (NP (PRP you)))) (VP (MD can) (VP (NN type) (NP (PRP them)))))))))))) (, ,) (NP (PRP you)) (VP (AUX are) (ADJP (RB still) (JJ ineffective) (PP (IN in) (S (VP (VBG using) (NP (PRP$ your) (NN computer))))))) (. .)))";
		Treebank treebank = load(source);
		ParseTreeNode tree = treebank.getParseTree(0);
		headRule.apply(tree);
		analyzer.resolveTerminalDependency(tree);
	}
	
	private static Treebank load(String source) throws IOException, InvalidFormatException {
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		assert treebank.getParseTreeList().size() == 1;
		return treebank;
	}
	
	private static List<ParseTreeNode> getTerminalNodes(ParseTreeNode tree) {
		if (tree.children == null) {
			return Collections.singletonList(tree);
		} else {
			List<ParseTreeNode> list = new ArrayList<ParseTreeNode>();
			for (ParseTreeNode child : tree.children) {
				list.addAll(getTerminalNodes(child));
			}
			return list;
		}
	}
	
	private static List<Integer> getHeadNumbers(List<ParseTreeNode> list) {
		IdentityHashMap<Annotation, Integer> map = new IdentityHashMap<Annotation, Integer>();
		for (int i = 0; i < list.size(); i++) {
			map.put(list.get(i).ann, i);
		}
		
		List<Integer> headNumbers = new ArrayList<Integer>();
		for (ParseTreeNode terminal : list) {
			Integer n = map.get(terminal.ann.get("dep"));
			if (n == null) {
				headNumbers.add(-1);
			} else {
				headNumbers.add(n);
			}
		}
		
		return headNumbers;
	}
}
