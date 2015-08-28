// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil

package Jet.Parser;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;
import Jet.Refres.Resolve;

/**
 * the set of syntactic relations associated with a document.
 */

public class SyntacticRelationSet {

    ArrayList relations;

    public static String[] relationTypes
            = {"of", "on", "in", "to", "by", "at", "through", "for", "with",
            "subject", "object", "poss", "nameMod"};

    // maps noms to Vs and vice versa
    public static HashMap nomVmap = new HashMap();

    public SyntacticRelationSet() {
        relations = new ArrayList();
    }

    /**
     * search Document <CODE>doc</CODE> for syntactic relatioms (encoded
     * as features on annotations) and add them to the SyntacticRelationSet.
     * Looks for features on the list <CODE>relationTypes</CODE>.
     */

    public void addRelations(Document doc) {
        Vector constits = doc.annotationsOfType("constit");
        if (constits != null) {
            for (int j = 0; j < constits.size(); j++) {
                Annotation ann = (Annotation) constits.elementAt(j);
                for (int r = 0; r < relationTypes.length; r++) {
                    String type = relationTypes[r];
                    if (ann.get(type) != null) {
                        Annotation value = (Annotation) ann.get(type);
                        Annotation annHeadC;
                        if (ann.get("mainV") != null) {
                            annHeadC = (Annotation) ann.get("mainV");
                        } else {
                            annHeadC = Resolve.getHeadC(ann);
                        }
                        String annHead = SynFun.getHead(doc, annHeadC);
                        Annotation valueHeadC = Resolve.getHeadC(value);
                        String valueHead = SynFun.getHead(doc, valueHeadC);
                        SyntacticRelation relation =
                                new SyntacticRelation
                                        (annHeadC.start(), annHead, type, valueHeadC.start(), valueHead);
                        relations.add(relation);
                        SyntacticRelation reciprocal =
                                new SyntacticRelation
                                        (valueHeadC.start(), valueHead, type + "-1", annHeadC.start(), annHead);
                        relations.add(reciprocal);
                    }
                }
            }
        }
    }

    /**
     * adds a SyntacticRelation to the set.
     */

    public void add(SyntacticRelation r) {
        if (!relations.contains(r))
            relations.add(r);
    }

    /**
     * adds all the elements of <CODE>c</CODE>, which <I>should</I> be
     * SyntacticRelations, to the set.
     */

    public void addAll(Collection c) {
        Iterator it = c.iterator();
        while (it.hasNext())
            add((SyntacticRelation) it.next());
    }

    /**
     * returns the number of SyntacticRelations in the set.
     */

    public int size() {
        return relations.size();
    }

    public boolean equals(Object o) {
        if (!(o instanceof SyntacticRelationSet))
            return false;
        SyntacticRelationSet p = (SyntacticRelationSet) o;
        return relations.size() == p.relations.size() &&
                relations.containsAll(p.relations);
    }

    public int hashCode() {
        if (size() == 0)
            return 1;
        else
            return size() + relations.get(0).hashCode();
    }

    /**
     * returns the i-th relation in the set, where i is non-negative
     * and less than the size of the set.
     */

    public SyntacticRelation get(int i) {
        return (SyntacticRelation) relations.get(i);
    }

    /**
     * return the first relation whose source is at position <CODE>from</CODE>
     * and whose target is at position <CODE>to</CODE>, or <CODE>null</CODE>
     * if no such relation exists.
     */

    public SyntacticRelation getRelation(int from, int to) {
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            if (r.sourcePosn == from && r.targetPosn == to)
                return r;
        }
        return null;
    }

    /**
     * return the first relation whose source is at position <CODE>from</CODE>
     * and whose type is <CODE>type</CODE>, or <CODE>null</CODE>
     * if no such relation exists.
     */

    public SyntacticRelation getRelation(int from, String type) {
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            if (r.sourcePosn == from && r.type.equalsIgnoreCase(type))
                return r;
        }
        return null;
    }

    /**
     * return a SyntacticRelationSet containing those relations whose source
     * is at position <CODE>from</CODE>.
     */

    public SyntacticRelationSet getRelationsFrom(int from) {
        SyntacticRelationSet s = new SyntacticRelationSet();
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            if (r.sourcePosn == from)
                s.add(r);
        }
        return s;
    }

    /**
     * return the first relation whose target is at position <CODE>to</CODE>
     * or <CODE>null</CODE> if no such relation exists.
     */

    public SyntacticRelation getRelationTo(int to) {
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            if (r.targetPosn == to)
                return r;
        }
        return null;
    }

    /**
     * return a printable version of the SyntacticRelationSet.
     */

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("{");
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            buf.append(r.toString());
            if (i < relations.size() - 1)
                buf.append((relations.size() > 3) ? "\n " : "; ");
        }
        buf.append("}");
        return buf.toString();
    }

    public void write(PrintWriter pw) {
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            pw.println(r.type + " | "
                    + r.sourceWord + " | " + r.sourcePosn + " | "
                    + r.targetWord + " | " + r.targetPosn);
        }
    }

    /**
     *  reads the SyntacticRelationSet from BufferedReader <CODE>br</CODE>.  The
     *  input should be one relation per line, consisting of 5 fields separated
     *  by "|".
     */

    public void read(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(" \\| ");
            if (parts.length != 5) {
                System.err.println("Invalid input: " + line);
                continue;
            }
            try {
                SyntacticRelation r = new SyntacticRelation(Integer.valueOf(parts[2]),
                        parts[1].trim(), parts[0].trim(),
                        Integer.valueOf(parts[4]), parts[3].trim());
                add(r);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // if true, use Prop/NomBank relations if available;
    // if false, use only logical grammatical relations
    public static boolean usePA = true;
    // if true, use base forms of words;  if false, use inflected forms
    public static boolean useBaseForm = true;

    // GLARF tuple structure till January 2006
    /*
	static final int GLARF_TUPLE_SIZE = 18;
	static final int GLARF_LOGICAL_ROLE = 0;
	static final int GLARF_PA_ROLE = 2;
	static final int GLARF_TRANSPARENT = 4;
	static final int GLARF_FUNCTOR = 5;
	static final int GLARF_FUNCTOR_OFFSET = 6;
	static final int GLARF_FUNCTOR_BASE_FORM = 7;
	static final int GLARF_FUNCTOR_VERB_FOR_NOM = 8;
	static final int GLARF_ARG = 13;
	static final int GLARF_ARG_OFFSET = 14;
	static final int GLARF_ARG_BASE_FORM = 15;
  
	// GLARF tuple structure January 2007
	
	static final int GLARF_TUPLE_SIZE = 23;
	static final int GLARF_LOGICAL_ROLE = 0;
	static final int GLARF_PA_ROLE = 2;
	static final int GLARF_TRANSPARENT = 4;
	static final int GLARF_FUNCTOR = 5;
	static final int GLARF_FUNCTOR_OFFSET = 6;
	static final int GLARF_FUNCTOR_BASE_FORM = 8;
	static final int GLARF_FUNCTOR_VERB_FOR_NOM = 9;
	static final int GLARF_ARG = 16;
	static final int GLARF_ARG_OFFSET = 17;
	static final int GLARF_ARG_BASE_FORM = 19;
  */

    // GLARF tuple structure 2011

    static final int GLARF_TUPLE_SIZE = 25;
    static final int GLARF_LOGICAL_ROLE = 0;
    static final int GLARF_PA_ROLE = 2;
    static final int GLARF_TRANSPARENT = 4;
    static final int GLARF_FUNCTOR = 5;
    static final int GLARF_FUNCTOR_OFFSET = 6;
    static final int GLARF_FUNCTOR_BASE_FORM = 9;
    static final int GLARF_FUNCTOR_VERB_FOR_NOM = 10;
    static final int GLARF_ARG = 17;
    static final int GLARF_ARG_OFFSET = 18;
    static final int GLARF_ARG_BASE_FORM = 21;

    /**
     * reads relations (in GLARF tuple format) from 'fileName'.  Current format
     * is  relation | PA-relation | ... (total of 25 fields)
     */

    public void readRelations(String fileName) {
        String line;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // ignore Lisp comments
                if (line.startsWith(";")) continue;
                String[] fields = line.split(" \\| ");
                if (fields.length != GLARF_TUPLE_SIZE) {
                    System.out.println("Invalid input line (" + fields.length + " fields): " + line);
                    continue;
                }
                try {
                    String type = fields[GLARF_LOGICAL_ROLE].trim();
                    if (type.equals("RELATIVE")) continue;
                    if (type.equals("RED-RELATIVE")) continue;
                    String paRole = fields[GLARF_PA_ROLE].trim();
                    String sourceWord = useBaseForm ?
                            fields[GLARF_FUNCTOR_BASE_FORM].trim().toLowerCase() : // take base form
                            fields[GLARF_FUNCTOR].trim();                // take inflected form
                    int sourcePosn = Integer.parseInt(fields[GLARF_FUNCTOR_OFFSET].trim());
                    String verb = fields[GLARF_FUNCTOR_VERB_FOR_NOM].trim().toLowerCase();
					/* -- code for word senses --
					String sourceWordSense = fields[10].trim();
					if (sourceWordSense.equals("NIL"))
						sourceWordSense = "1";
					String verbSense = fields[11].trim();
					if (verbSense.equals("NIL"))
						verbSense = "1";
					*/
                    String targetWord = useBaseForm ?
                            fields[GLARF_ARG_BASE_FORM].trim().toLowerCase() : // take base form
                            fields[GLARF_ARG].trim();                // take inflected form
                    int targetPosn = Integer.parseInt(fields[GLARF_ARG_OFFSET].trim());
                    if (!type.equals("NIL")) {
                        SyntacticRelation r = new SyntacticRelation
                                (sourcePosn, sourceWord, type, targetPosn, targetWord);
                        boolean transparent = fields[GLARF_TRANSPARENT].equals("T");
                        if (transparent)
                            r.setTransparent(true);
                        add(r);
                    }
                    // r.sourceWordSense = sourceWordSense;
                    if (!paRole.equals("NIL") && !paRole.equals("SUPPORT")) {
                        SyntacticRelation rpa = new SyntacticRelation
                                (sourcePosn, sourceWord, paRole, targetPosn, targetWord);
                        add(rpa);
                    }
                    // skip if doing just Prop Bank
                    if (!verb.equals("nil")) {
                        nomVmap.put(sourceWord + "/n", verb);
                        nomVmap.put(verb, sourceWord + "/n");
                        // nomVmap.put(sourceWord + sourceWordSense + "/n", verb + verbSense);
                        // nomVmap.put(verb + verbSense, sourceWord + sourceWordSense + "/n");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input line: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("readRelations error: " + e);
        }
        bypassTransparentLinks();
        addInverses();
    }

    private void bypassTransparentLinks() {
        for (int i = 0; i < relations.size(); i++) {
            SyntacticRelation rx = (SyntacticRelation) relations.get(i);
            String typex = rx.type;
            if ((typex.equals("ARG0") || typex.equals("ARG1") || typex.equals("ARG2") ||
                    typex.equals("SBJ") || typex.equals("OBJ")) &&
                    (rx.targetWord.equals("of") || rx.targetWord.equals("by"))) {
                int tox = rx.targetPosn;
                for (int j = 0; j < relations.size(); j++) {
                    SyntacticRelation ry = (SyntacticRelation) relations.get(j);
                    if (i != j &&
                            rx.targetPosn == ry.sourcePosn &&
                            ry.type.equals("OBJ") &&
                            ry.transparent) {
                        rx.targetWord = ry.targetWord;
                        rx.targetPosn = ry.targetPosn;
                        // System.out.println ("Bypassing transparent link.  New arc is " + rx);
                    }
                }
            }
        }
    }

    /**
     * for each relation in the SyntacticRelationSet of the form 'X rel Y', adds
     * an inverse relation of the form 'Y rel-1 X'.
     */

    public void addInverses() {
        int Nrelations = relations.size();
        for (int i = 0; i < Nrelations; i++) {
            SyntacticRelation r = (SyntacticRelation) relations.get(i);
            SyntacticRelation rinv = new SyntacticRelation
                    (r.targetPosn, r.targetWord, r.type + "-1", r.sourcePosn, r.sourceWord);
            add(rinv);
        }
    }

    static final String home =
            "C:/Documents and Settings/Ralph Grishman/My Documents/";
    static final String ACEdir =
            home + "ACE 05/V4/";
    static final String outputDir =
            ACEdir + "sents/";

    public static void main(String[] args) {
        SyntacticRelationSet s = new SyntacticRelationSet();
        s.readRelations(outputDir + "nw/AFP_ENG_20030323.0020.sent.txt.acetrip90");
        System.out.println(s.toString());
    }

}
