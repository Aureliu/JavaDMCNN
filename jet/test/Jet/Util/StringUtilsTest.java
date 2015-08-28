/**
 * 
 */
package Jet.Util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;

/**
 * TestCase for StringUtils
 * 
 * @author Akira ODA
 */
public class StringUtilsTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(StringUtilsTest.class);
	}

	/**
	 * Test method for
	 * {@link Jet.Util.StringUtils#join(java.lang.String, java.lang.Object[])}.
	 */
	@Test
	public void testJoinArray() {
		assertEquals("username:password", StringUtils.join(":", "username", "password"));
		assertEquals("", StringUtils.join("/"));
	}

	/**
	 * Test method for
	 * {@link Jet.Util.StringUtils#join(java.lang.String, java.lang.Iterable)}.
	 */
	@Test
	public void testJoinIterable() {
		assertEquals("foo, bar", StringUtils.join(", ", Arrays.asList("foo", "bar")));
		assertEquals("", StringUtils.join("\\", Collections.emptyList()));
	}
}
