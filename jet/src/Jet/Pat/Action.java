// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Tipster.*;

/**
 *  The representation of an action as part of a <B>when</B> statement
 *  in a pattern collection.
 */

public abstract class Action {

  public abstract int perform(Document doc, PatternApplication patap);

  public abstract String toString();

}
