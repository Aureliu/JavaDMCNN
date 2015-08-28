// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.62
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Chunk;

import Jet.Tipster.*;
import java.util.*;

public class NameConstraints {

    boolean[][] validState = null;

    /**
     *  captures a set of constraints on name tagging, specified by a set
     *  of annotations of the form <br>
     *     isName type = ...       <br>
     *  or                         <br>
     *     isExactName type = ...  <br>
     *  The first form specifies that all the tokens within the annotation
     *  are part of a name of the specified type;  the second form specifies 
     *  that the tokens are a complete name of that type. <p>
     *    An isName annotation with no specified type indicates that the
     *  enclosed tokens must be part of a name, but that name may be of
     *  any type.
     */

    NameConstraints (Document doc, Annotation[] tokens, String[] state) {
	// if no name annotations, leave table empty
	if (doc.annotationsOfType("isName") == null && 
	    doc.annotationsOfType("isExactName") == null)
	    return;
	int nTokens = tokens.length;
	int nStates = state.length;
	validState = new boolean[nTokens][nStates];
	List<String> stateList = Arrays.asList(state);

	String type = null;
	// extent < 0:  not in scope of an isName or isExactName annotation
	// extent >= 0:  current isName/isExactName extends through character offset 'extent'
	int extent = -1;
	// exact:  true if in scope of an isExactName annotation
	boolean exact = false;
	for (int iToken = 0; iToken < nTokens; iToken++) {
	    int posn = tokens[iToken].start();
	    if (extent >= 0 && posn < extent) {
		for (int iState = 0; iState < nStates; iState++) {
		    if (type == null) {
			validState[iToken][iState] = !state[iState].equals("other");
		    } else if (type.equals("other")) {
			validState[iToken][iState] = state[iState].equals("other");
		    } else {
			validState[iToken][iState] = state[iState].equals("I-" + type);
		    }
		}
	    } else {
		boolean endOfExact = exact;
		Vector<Annotation> isName = doc.annotationsAt(posn, "isName");
		Vector<Annotation> isExactName = doc.annotationsAt(posn, "isExactName");
		if (isExactName != null && isExactName.size() > 0) {
		    Annotation a = isExactName.get(0);
		    type = (String) a.get("type");
		    if (type == null)
			System.err.println ("NameConstraints: no type attribute in <isExactName>");
		    else if (!stateList.contains("B-" + type) && !type.equals("other"))
			System.err.println ("NameConstraints: invalid type in <isExactName type=" + type + ">");
		    extent = a.end();
		    exact = true;
		} else if (isName != null && isName.size() > 0) {
		    Annotation a = isName.get(0);
		    type = (String) a.get("type");
		    if (type != null && !stateList.contains("B-" + type) && !type.equals("other"))
			System.err.println ("NameConstraints: invalid type in <isName type=" + type + ">");
		    extent = a.end();
		    exact = false;
		} else {
		    extent = -1;
		    exact = false;
		}
		for (int iState = 0; iState < nStates; iState++) {
		    String s = state[iState];
		    if (extent >= 0) {
			if (type == null) {
			    validState[iToken][iState] = !s.equals("other");
			} else if (type.equals("other")) {
			    validState[iToken][iState] = s.equals("other");
			} else if (exact || endOfExact) {
			    validState[iToken][iState] = s.equals("B-" + type);
			} else {
			    validState[iToken][iState] = s.equals("B-" + type) || s.equals("I-" + type);
			}
		    } else {
			if (endOfExact) {
			    validState[iToken][iState] = s.startsWith("B") || s.equals("other");
			} else {
			    validState[iToken][iState] = true;
			}
		    }
		}
	    }
	}
	// printTable (validState, state, nTokens, nStates);
    }
    
    // for debugging

    private void printTable (boolean[][] v, String[] state, int nTokens, int nStates) {
	System.out.print("                    ");
	for (int iToken = 0; iToken < nTokens; iToken++) 
	    System.out.print (" " + iToken);
	System.out.println ();
	for (int iState = 0; iState < nStates; iState++) {
	    String stateName = state[iState];
	    stateName += "                    ";
	    stateName = stateName.substring(0, 20);
	    System.out.print(stateName);
	    for (int iToken = 0; iToken < nTokens; iToken++) {
		System.out.print (v[iToken][iState] ? " T" : " F");
	    }
	System.out.println ();
	}
    }

    /**
     *  returns 'true' if state 'istate' of the name tagger at token 'itoken'
     *  is consistent with the name constraints.
     */

    boolean allowedState (int itoken, int istate) {
	if (validState == null)
	    return true;
	return validState[itoken][istate];
    }

}
