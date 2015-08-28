// -*- tab-width: 4 -*-
package Jet.Concepts;

import java.io.File;
import javax.swing.filechooser.*;

public class ConcreteFileFilter extends FileFilter {

  private String extension = null;
  private String description = null;

  public ConcreteFileFilter(String extension, String description) {
    this.extension = extension;
    this.description = description;
  }

  public boolean accept(File f) {
    if(f != null) {
      if(f.isDirectory())
        return true;
      if(this.extension.equals(getExtension(f)))
        return true;
    }
    return false;
  }

  public String getExtension(File f) {
    if(f != null) {
      String filename = f.getName();
      int i = filename.lastIndexOf('.');
      if(i > 0 && i < filename.length() - 1)
        return filename.substring(i + 1).toLowerCase();
    }
    return null;
  }

  public String getDescription() {
    return description;
  }
}
