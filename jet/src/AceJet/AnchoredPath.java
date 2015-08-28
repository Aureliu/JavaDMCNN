package AceJet;

import Jet.Lex.Stemmer;

import java.util.*;

/**
 *  a representation of a lexicalizd dependency path, including
 *  the types of its endpoints.
 */

public class AnchoredPath {
    public static Stemmer stemmer=new Stemmer().getDefaultStemmer();
    public String arg1;
    public String path;
    public String arg2;
    public String source;

    public AnchoredPath (String arg1, String path, String arg2,
			 String source, int sentenceSpanStart, int sentenceSpanEnd) {
	this.arg1 = arg1;
	this.path = path;
	this.arg2 = arg2;
	this.source = source;
    }

    public AnchoredPath (String line) {
        boolean newFormat = true;
	String[] topFields = line.split("\t");
	if (topFields.length != 2) {
	    topFields = line.split("\\|");
            newFormat = false;
	}
        String[] pathFields;
        if (newFormat) {
	    pathFields = topFields[1].split(" -- ");
        }
        else {
            pathFields = topFields[0].split(" -- ");
        }
	if (pathFields.length != 3) {
	    System.out.println ("Error in path line: " + line);
	    System.exit (1);
	}
	this.arg1 = pathFields[0];
	this.path = lemmatizePath(pathFields[1]);
	this.arg2 = pathFields[2];
	// this.source = topFields[1];
    }

    public String toString() {
        return arg1 + " -- " + path + " -- " + arg2;
	//return arg1 + " -- " + path + " -- " + arg2 + " | " + source;
    }

    /**
     *  given a lexicalized dependency path <CODE>path</CODE>,
     *  consisting of arcs and lexical items separated by colons,
     *  replaces each lexical item by its lemma.
     */

    public static String lemmatizePath(String path) {
        String[] pathArray = path.split(":");
        LinkedList<String> ll = new LinkedList<String>(Arrays.asList(pathArray));
        StringBuilder sb  = new StringBuilder();
        while (ll.size() > 0) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(ll.removeFirst());
            if (ll.size() > 0) {
                sb.append(":");
                sb.append(stemmer.getStem(ll.removeFirst(), "NULL"));
            }
        }
        return sb.toString();
    }

    /**
     *  if the path includes a constituent conjoined with either
     *  arg1 or arg2, eliminate the conjoined constituent:
     *     arg1 ... X and arg2  ==>  arg1 ... arg2
     *     arg1 and X ... arg2  ==>  arg1 ... arg2
     *  if the path involves solely conjoining of arg1 and arg2,
     *  return null.
     */

    public static String reduceConjunction (String path) {
        String[] pathArray = path.split(":");
	int pathLen = pathArray.length;
        LinkedList<String> ll = new LinkedList<String>(Arrays.asList(pathArray));
	if (pathLen >= 4 &&
	    ll.get(0).equals("conj-1") &&
	    ll.get(1).equals("and") &&
	    ll.get(2).equals("cc-1"))
	    return cat(ll.subList(4, pathLen));
	if (pathLen >= 2 &&
	    ll.get(0).equals("conj-1"))
	    return cat(ll.subList(2, pathLen));
	if (pathLen >= 4 &&
	    ll.get(pathLen-3).equals("cc") &&
	    ll.get(pathLen-2).equals("and") &&
	    ll.get(pathLen-1).equals("conj"))
	    return cat(ll.subList(0, pathLen-4));
	if (pathLen >= 2 &&
	    ll.get(pathLen-1).equals("conj"))
	    return cat(ll.subList(0, pathLen-2));
	return path;
    }

    private static String cat (List<String> l) {
	if (l.isEmpty())
	    return null;
        StringBuilder sb  = new StringBuilder();
	for (String s : l) {
	    if (sb.length() > 0) sb.append(":");
	    sb.append(s);
	}
	return sb.toString();
    }
	
}
