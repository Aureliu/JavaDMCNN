// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.02
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import Jet.Tipster.Document;

/**
 *  the encoding of the "write <I>message</I>" action, where <I>message</I>
 *  is a StringExpression (one or more strings or variables).
 */

public class WriteAction extends Action {

  StringExpression message;

  /**
   *  constructs a new WriteAction, given a message
   */

  public WriteAction (StringExpression message) {
    this.message = message;
  }

  /**
   *  constructs a new WriteAction by reading from StreamTokenizer <I>tok</I>.
   *  The input should be the token "write" (the current token) followed
   *  by a StringExpression:  one or more strings or variables,
   *  separated by "+".
   */

  public WriteAction (StreamTokenizer tok)
         throws IOException, PatternSyntaxError {
    tok.nextToken();
    message = new StringExpression(tok);
  }

  /**
   *  performs the "write" action, writing the message to standard output.
   */

  public int perform(Document doc, PatternApplication patap) {
    String stg = message.evaluate(doc, patap);
    System.out.println (stg);
    return -1;
  }

  /**
   *   returns a printable form of the "write" action, consisting of
   *   "write" followed by the elements of the message, separated by "+".
   */

  public String toString() {
    return "write " + message;
  }
}
