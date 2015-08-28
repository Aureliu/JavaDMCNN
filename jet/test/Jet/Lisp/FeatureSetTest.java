package Jet.Lisp;

import junit.framework.TestCase;

public class FeatureSetTest extends TestCase {
	public void testEquals() {
		FeatureSet a = new FeatureSet();
		FeatureSet b = new FeatureSet();
		
		a.put("VAL", "1999-03-31");
		b.put("VAL", "1999-03-31");
		
		assertTrue(a.equals(b));
		assertEquals(a, b);
	}
}
