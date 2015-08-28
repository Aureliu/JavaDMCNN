// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2003, 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Lex;


import Jet.Tipster.*;
import Jet.Lisp.*;
import java.lang.Character;
import java.util.*;
import java.util.regex.*;

/**
 *  Tokenizer contains the methods for dividing a string into tokens.
 *  <P>
 *  The rules generally follow those of the Penn Tree Bank, although
 *  hyphenated items are separated, with the hyphen a separate token,
 *  and single quotes (') are always treated as separate tokens unless
 *  part of a standard suffix ('s, 'm, 'd, 're, 've, n't, 'll).
 *  <P>
 *  For a capitalized word, we set the feature <B>case=cap</B>, except
 *  that at the beginning of a sentence, the token is marked
 *  <B>case=forcedCap</B>. In addition, words following
 *  a ``, ", `, or _ are marked forcedCap.
 *  <P>
 *  The tokenizer is loosely based on the version for OAK.
 */

public class Tokenizer {

static Vector tokens;
static String lastToken;
static HashSet suffixes2 = new HashSet();
static HashSet suffixes3 = new HashSet();

static {suffixes2.add("'s");
        suffixes2.add("'m");
        suffixes2.add("'d");
        suffixes2.add("'S");
        suffixes2.add("'M");
        suffixes2.add("'D");
        suffixes3.add("'re");
        suffixes3.add("'ve");
        suffixes3.add("n't");
        suffixes3.add("'ll");
        suffixes3.add("'RE");
        suffixes3.add("'VE");
        suffixes3.add("N'T");
        suffixes3.add("'LL");
       }

private static HashMap<Integer, Integer> specialTokenEnd;
private static HashMap<Integer, String> specialTokenType;

/**
 *  tokenizes the portion of Document doc covered by span.  For each
 *  token, adds to doc an annotation of type <B>token</B>.
 */

public static void tokenize (Document doc, Span span) {
  findTokens (doc, doc.text(), span.start(), span.end());
}

/**
 *   tokenizes the argument string.  Returns a vector, each of whose
 *   elements is the character string for one token in the argument.
 */

public static String[] tokenize (String text) {
  tokens = new Vector();
  findTokens (null, text, 0, text.length());
  return (String[]) tokens.toArray(new String[0]);
}


private static void findTokens (Document doc, String text, int ic, int end) {
	String block;
	int tokenStart;
	boolean firstBlock = true;
	boolean lastBlock;
	lastToken = "";
	findTokensByPattern (doc, text, ic, end);
	// skip white space preceding first token
	ic = skipWSX(text, ic, end);
	while (ic < end) {
		tokenStart = ic;
		Integer tokenEnd = specialTokenEnd.get(ic);
		if (tokenEnd != null)
			ic = tokenEnd;
		else
			ic++;
		while ((ic < end) && !Character.isWhitespace(text.charAt(ic))) {
			tokenEnd = specialTokenEnd.get(ic);
			if (tokenEnd != null)
				ic = tokenEnd;
			else
				ic++;
		}
		block = text.substring(tokenStart,ic);
		// include whitespace following token
		while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
		lastBlock = ic >= end && doc != null;
		boolean[] newToken = splitIntoTokens (block, tokenStart, lastBlock);
		buildTokens (doc, block, newToken, tokenStart, ic, firstBlock);
		firstBlock = false;
	}
}

static private String userNamePatStg = "[a-zA-Z0-9_\\.-]+";
static private String domainNamePatStg = "([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9]+";
static private String emailPatStg = userNamePatStg + "( ?\\.\\.\\. ?)?@" + domainNamePatStg;
static private String pathPatStg = "[a-zA-Z0-9_=\\?/-]+";
static private String urlPatStg = "http://" + domainNamePatStg + pathPatStg;
static private Pattern emailPat = Pattern.compile(emailPatStg);
static private Pattern urlPat = Pattern.compile(urlPatStg);

/**
 *  look for predefined patterns for email addresses and URLs, starting at
 *  position 'start' of 'text'.  If a match, record the token in
 *  specialTokenEnd and specialTokenType.
 */
 
private static void findTokensByPattern (Document doc, String text, int start, int end) {
	Matcher emailMatcher = emailPat.matcher(text).region(start,end);
	specialTokenEnd = new HashMap<Integer, Integer>();
	specialTokenType = new HashMap<Integer, String>();
	while (emailMatcher.find()) {
		int tokenStart = emailMatcher.start();
		int tokenEnd = emailMatcher.end();
		specialTokenEnd.put(tokenStart, tokenEnd);
		specialTokenType.put(tokenStart, "email");
	}
	Matcher urlMatcher = urlPat.matcher(text).region(start,end);
	while (urlMatcher.find()) {
		int tokenStart = urlMatcher.start();
		int tokenEnd = urlMatcher.end();
		specialTokenEnd.put(tokenStart, tokenEnd);
		specialTokenType.put(tokenStart, "url");
	}
}

/**
 *  divides a white-space delimited sequence of characters into tokens.
 *
 *  @param blockString  the character sequence to be divided
 *  @param blockStart   offset within document of start of block
 *  @param lastBlock    the previous sequence
 *
 *  @return a boolean array whose i-th element is true if the i-th character
 *          of blockString is the beginning of a new token [if blockString has
 *          n characters, this is an n+1 element array, in which the last
 *          element is always true]
 */

private static boolean[] splitIntoTokens (String blockString, int blockStart, boolean lastBlock) {
	char[] block = blockString.toCharArray();
	int blockLength = block.length;
	boolean[] newToken = new boolean[blockLength+1];
	newToken[blockLength] = true;
	// except for "." , make any non-alphanumeric a separate token
	for (int i=0; i<blockLength; i++) {
		char c = block[i];
		if (!(Character.isLetterOrDigit(c) || c =='.')) {
			newToken[i] = true;
			newToken[i+1] = true;
		}
	}
	// make ``, '', --, and ... single tokens
	for (int i=0; i<blockLength-1; i++) {
		char c = block[i];
		if ((c == '`' || c == '\'' || c == '-') && c == block[i+1]
		    && newToken[i]) {
			newToken[i+1] = false;
		}
	}
	for (int i=0; i<blockLength-2; i++) {
		if (block[i] == '.' && block[i+1] == '.' &&
		    block[i+2] == '.' && newToken[i]) {
			newToken[i+1] = false;
			newToken[i+2] = false;
		}
	}
	// make comma a separate token unless surrounded by digits
	for (int i=1; i<blockLength-2; i++) {
		if (block[i] == ',' && Character.isDigit(block[i-1]) &&
		    Character.isDigit(block[i+1])) {
			newToken[i] = false;
			newToken[i+1] = false;
		}
	}
	// make period a separate token if this is the last block
	// [of a sentence] and the period is final or followed by ["'}>)] or ''
	// Note that this may split off the period even if the token is an
	// abbreviation.
	if (lastBlock) {
		if (block[blockLength-1] == '.') {
			newToken[blockLength-1] = true;
		} else if (blockLength > 1 && block[blockLength-2] == '.' &&
		           "\"'}>)".indexOf(block[blockLength-1]) >= 0) {
		    newToken[blockLength-2] = true;
		} else if (blockLength > 2 && block[blockLength-3] == '.' &&
		           block[blockLength-2] == '\'' &&
		           block[blockLength-1] == '\'') {
		    newToken[blockLength-3] = true;
		}
	}
	// split off standard 2 and 3-character suffixes ('s, n't, 'll, etc.)
	for (int i=0; i<blockLength-2; i++) {
		if (newToken[i+3] && suffixes3.contains(blockString.substring(i,i+3))){
			newToken[i] = true;
			newToken[i+1] = false;
			newToken[i+2] = false;
		}
	}
	for (int i=0; i<blockLength-1; i++) {
		if (newToken[i+2] && suffixes2.contains(blockString.substring(i,i+2))){
			newToken[i] = true;
			newToken[i+1] = false;
		}
	}
	// make &...; a single token (probable XML escape sequence)
	for (int i=0; i<blockLength-1; i++) {
		if (block[i] == '&') {
			for (int j=i+1; j<blockLength; j++) {
				if (block[j] == ';') {
					for (int k=i+1; k<=j; k++) {
						newToken[k] = false;
					}
				}
			}
		}
	}
	for (int i=0; i<blockLength; i++) {
		Integer tokenEnd = specialTokenEnd.get(blockStart + i);
		if (tokenEnd != null) {
			newToken[i] = true;
			for (int j=i+1; j<blockLength && j+blockStart < tokenEnd; j++) {
				newToken[j] = false;
			}
		}
	}
	return newToken;
}

	/**
	 *  adds token Annotations to doc consisting of the tokens within block.
	 */

	private static void buildTokens (Document doc, String block, boolean[] newToken,
    int offset, int nextBlockStart, boolean firstBlock) {
    int tokenStart = 0;
   	for (int i=1; i<=block.length(); i++) {
   		if(newToken[i]) {
   			int tokenEnd = i;
   			FeatureSet fs = null;
   			// if number, compute value (set value=-1 if not a number)
   			int value = 0;
   			for (int j=tokenStart; j<tokenEnd; j++) {
   				if (Character.isDigit(block.charAt(j))) {
   					value = (value * 10) +  Character.digit(block.charAt(j),10);
   				} else if (block.charAt(j) == ',' && value > 0) {
   					// skip comma if preceded by non-zero digit
   				} else {
   					value = -1;
   					break;
   				}
   			}
   			String type = specialTokenType.get(tokenStart + offset);
   			if (type != null) {
   				fs = new FeatureSet ("type", type);
   			} else if (Character.isUpperCase(block.charAt(tokenStart))) {
   				if (firstBlock ||
   				    // for ACE
   				    lastToken.equals("_") ||
   				    lastToken.equals("\"") || lastToken.equals("``") || lastToken.equals("`")) {
   					fs = new FeatureSet ("case", "forcedCap");
   				} else {
   					fs = new FeatureSet ("case", "cap");
   				}
   			} else if (value >= 0) {
   				fs = new FeatureSet ("intvalue", new Integer(value));
   			} else {
   				fs = new FeatureSet ();
   			}
   			// create token
   			int spanEnd = (tokenEnd == block.length()) ? nextBlockStart : tokenEnd + offset;
   			String tokenString = block.substring(tokenStart, tokenEnd);
   			recordToken (doc, tokenString, tokenStart + offset, spanEnd, fs);
   			tokenStart = tokenEnd;
   			lastToken = tokenString;
   		}
   	}
	}

/*  -- old Proteus version -- all special chars are separate tokens
 *
private static void findTokens (Document doc, String text, int ic, int end) {
  int tokenStart;
  while (ic < end) {
    // skip white space preceding token
    while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
    if (ic >= end) break;
    // letter:  collect subsequent letters into token
    if (Character.isLetter(text.charAt(ic))) {
      tokenStart = ic;
      FeatureSet fs = (Character.isUpperCase(text.charAt(ic))) ?
                      new FeatureSet ("case", "cap") :
                      new FeatureSet ();
      ic++;
      while ((ic < end) && Character.isLetter(text.charAt(ic))) ic++;
      // include whitespace following token
      while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
      recordToken (doc, text, tokenStart, ic, fs);
    // digit:  collect subsequent digits into token
    } else if (Character.isDigit (text.charAt(ic))) {
      tokenStart = ic;
      int value = Character.digit(text.charAt(ic),10);
      ic++;
      while ((ic < end) && Character.isDigit(text.charAt(ic))) {
        value = (value * 10) +  Character.digit(text.charAt(ic),10);
        ic++;
      }
      while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
      recordToken (doc, text, tokenStart, ic,
                    new FeatureSet ("intvalue", new Integer(value)));
    } else {
      tokenStart = ic;
      ic++;
      while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
      recordToken (doc, text, tokenStart, ic, new FeatureSet ());
    }
  }
}

*/

	private static void recordToken (Document doc, String text,
	                                 int start, int end, FeatureSet fs) {
		// System.out.println ("Adding token " + text + fs + " over " + start + "-" + end);
	  if (doc == null) {
	    tokens.addElement(text);
	  } else {
	    doc.annotate ("token", new Span (start,end), fs);
	    if (fs.get("type") != null)
	    	doc.annotate ("ENAMEX", new Span (start, end), new FeatureSet ("TYPE", fs.get("type")));
	  }
	}

	/**
	 *  tokenizes portion 'span' of 'doc', splitting only on white space.
	 */

	public static void tokenizeOnWS (Document doc, Span span) {
		String text = doc.text();
		int tokenStart;
		int ic = span.start();
		int end = span.end();
		// skip white space preceding first token
		while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
		while (ic < end) {
	 		tokenStart = ic;
	      	ic++;
	      	while ((ic < end) && !Character.isWhitespace(text.charAt(ic))) ic++;
	      	// include whitespace following token
	      	while ((ic < end) && Character.isWhitespace(text.charAt(ic))) ic++;
	      	recordToken (doc, text, tokenStart, ic, new FeatureSet());
	     }
	}

	/**
	 *  advances to the next non-whitespace character in a document.
	 *  <CODE>posn</CODE> is a character position within Document
	 *  <CODE>doc</CODE>.  Returns <CODE>posn</CODE> (if that character
	 *  position is occupied by a non-whitespace character), or the position
	 *  of the next non-whitespace character, or <CODE>end</CODE> if all
	 *  the characters up to <CODE>end</CODE> are whitespace.
	 */

	public static int skipWS (Document doc, int posn, int end) {
		while ((posn < end) && Character.isWhitespace(doc.charAt(posn))) posn++;
		return posn;
	}

	/**
   *  advances to the next non-whitespace character in a String.
   *  <CODE>posn</CODE> is a character position within String
   *  <CODE>text</CODE>.  Returns <CODE>posn</CODE> (if that character
   *  position is occupied by a non-whitespace character), or the position
   *  of the next non-whitespace character, or <CODE>end</CODE> if all
   *  the characters up to <CODE>end</CODE> are whitespace.
   */

	public static int skipWS (String text, int posn, int end) {
		while ((posn < end) && Character.isWhitespace(text.charAt(posn))) posn++;
		return posn;
	}

	/**
	 *  advances to the next non-whitespace character in a document,
	 *  skipping any XML tags.
	 */

	public static int skipWSX (Document doc, int posn, int end) {
		while (posn < end) {
			if (Character.isWhitespace(doc.charAt(posn))) {
				posn++;
			} else if (doc.charAt(posn) == '<') {
				posn++;
				while (posn < end && doc.charAt(posn) != '>')
					posn++;
				if (posn < end) posn++;
			} else break;
		}
		return posn;
	}

	public static int skipWSX (String text, int posn, int end) {
		while (posn < end) {
			if (Character.isWhitespace(text.charAt(posn))) {
				posn++;
			} else if (text.charAt(posn) == '<') {
				posn++;
				while (posn < end && text.charAt(posn) != '>')
					posn++;
				if (posn < end) posn++;
			} else break;
		}
		return posn;
	}

	/**
	 *  returns an array containing all <B>token</B> annotations in
	 *  <CODE>span</CODE> of <CODE>doc</CODE>.
	 */

	public static Annotation[] gatherTokens (Document doc, Span span) {
		int start = span.start();
		int end = span.end();
		ArrayList tokens = new ArrayList();
		int posn = Tokenizer.skipWSX(doc, start, end);
		while (posn < end) {
			Annotation token = doc.tokenAt(posn);
			if (token == null) break;
			tokens.add(token);
			posn = token.span().end();
		}
		int count = tokens.size();
		return (Annotation[]) tokens.toArray(new Annotation[count]);
	}

	/**
	 *  returns an array of Strings corresponding to all the tokens
	 *  in <CODE>span</CODE> of <CODE>doc</CODE>.
	 */

	public static String[] gatherTokenStrings (Document doc, Span span) {
		Annotation[] tokens = gatherTokens(doc, span);
		int length = tokens.length;
		String[] stgs = new String[length];
		for (int i=0; i<length; i++)
			stgs[i] = doc.text(tokens[i]).trim();
		return stgs;
	}

	/**
	 *  performs a very simple validation of the tokenizer, returning a success
	 *  or failure indication.
	 */

	public static void main (String[] args) {
		Document doc = new Document(", DKo...@hotmail.com (Daniel Kolle)");
		tokenize (doc, doc.fullSpan());
		String[] tokens = gatherTokenStrings (doc, doc.fullSpan());
		if (tokens.length == 8 &&
		    tokens[0].equals("'") &&
		    tokens[1].equals("grishman ... @cs.nyu.edu") &&
		    tokens[2].equals("'") &&
		    tokens[3].equals("sold") &&
		    tokens[4].equals("$") &&
		    tokens[5].equals("3,100") &&
		    tokens[6].equals("shares") &&
		    tokens[7].equals("."))
			System.out.println ("Tokenizer validation succeeds.");
		else
			System.out.println ("Tokenizer validation fails.");
			for (int i=0; i<tokens.length; i++)
				System.out.println ("  tokens[" + i + "] = " + tokens[i]);
	}

}