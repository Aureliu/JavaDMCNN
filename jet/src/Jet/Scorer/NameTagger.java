// -*- tab-width: 4 -*-
package Jet.Scorer;

import java.io.*;
import Jet.Tipster.*;
import Jet.HMM.HMMannotator;

public interface NameTagger {

	public void tagDocument (Document doc);

	public void tag (Document doc, Span span);

	public void load (String fileName) throws IOException;

	public void newDocument ();

}
