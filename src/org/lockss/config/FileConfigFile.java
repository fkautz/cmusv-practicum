/*
 * $Id$
 */

/*

Copyright (c) 2001-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.config;

import java.io.*;
import java.net.*;

import org.lockss.util.*;
import org.lockss.util.urlconn.*;

/**
 * A simple wrapper class around the representation of a
 * generic Configuration loaded from disk.
 */

public class FileConfigFile extends ConfigFile {

  public FileConfigFile(String url) throws IOException {
    super(url);
  }

  /**
   * Given a file spec as either a String (path name) or url (file://),
   * return a File object.
   *
   * NB: Java 1.4 supports constructing File objects from a file: URI,
   * which will eliminate the need for this method.
   */
  private File getFile(String file)
      throws IOException, MalformedURLException {
    if (UrlUtil.isFileUrl(file)) {
      String fileLoc = file.substring("file:".length());
      return new File(fileLoc);
    } else {
      return new File(file);
    }
  }

  /**
   * Load a config from a local file if it has changed on disk.
   */
  protected synchronized boolean reload() throws IOException {
    File f = getFile(m_fileUrl);

    // The semantics of this are a bit odd, because File.lastModified()
    // returns a long, but we store it as a String.  We're not comparing,
    // just checking equality, so this should be OK
    String lm = Long.toString(f.lastModified());

    // Only reload the file if the last modified timestamp is different.
    if (!lm.equals(m_lastModified)) {
      if (log.isDebug2()) {
	if (m_lastModified == null) {
	  log.debug2("No previous file loaded, loading: " + m_fileUrl);
	} else {
	  log.debug2("File has changed on disk, reloading: " + m_fileUrl);
	}
      }
      m_lastModified = Long.toString(f.lastModified());
      m_lastAttempt = TimeBase.nowMs();
      InputStream in = null;
      m_IOException = null;

      // Open an output stream to write to our string
      try {
	in = new FileInputStream(f);
      } catch (FileNotFoundException ex) {
	// Perfectly normal behavior for some local config files which
	// may not exist.  Throw and let the ConfigCache worry about
	// it, don't bother logging.
	m_IOException = ex;
	throw ex;
      } catch (IOException ex) {
	// Other, unexpected IO exception.
	log.warning("Unexpected exception trying to load " +
		    "config file (" + m_fileUrl + "): " + ex);
	m_IOException = ex;
	throw ex;
      }

      if (in != null) {
	setConfigFrom(in);
	in.close();
      }
    } else {
      log.debug2("File has not changed on disk, not reloading: " + m_fileUrl);
    }

    return m_IOException == null;
  }
}
