package Jet.Format;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;

import junit.framework.JUnit4TestAdapter;
import junit.framework.TestCase;

import static org.junit.Assert.*;
import org.junit.Test;

import Jet.Parser.ParseTreeNode;

public class PTBWriterTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(PTBWriterTest.class);
	}

	
	@Test
	public void testSave() throws Exception {
		String source = "(NP (DT an) (-LRB- -LRB-) (VBG offending) (-RRB- -RRB-) (NN country) )";
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		ParseTreeNode parseTree = treebank.getParseTree(0);
		
		StringWriter out = new StringWriter();
		PTBWriter writer = new PTBWriter();
		writer.save(parseTree, out);
		
		assertEquals(normalize(source), normalize(out.toString()));
	}
	
	public String normalize(String source) {
		return source.replaceAll("\\)\\s*", "( ");
	}
}
