/*
 * $Id$
 */

/*

Copyright (c) 2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.simulated;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import java.text.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.plugin.base.*;
import org.lockss.crawler.*;
import org.lockss.daemon.*;

/**
 * A convenience class which takes care of handling the content
 * tree itself for the case where the content is in a ZIP file.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public class ActualZipContentGenerator extends SimulatedContentGenerator {
  private static Logger logger = Logger.getLogger("ActualZipContentGenerator");
  String zipFilePrefix = "SimulatedCrawl";
  AtomicInteger serialNo = new AtomicInteger(0);
  int maxSize = 100000000;
  String[] suffix = {
    ".txt",
    ".html",
    ".pdf",
    ".jpg",
    ".bin",
  };
  String[] mimeType = {
    "text/plain",
    "text/html",
    "application/pdf",
    "image/jpg",
    "application/octet-stream",
  };
  String stem = "http://www.content.org/";

  public ActualZipContentGenerator(String rootPath) {
    super(rootPath);
    logger.debug3("Created instance for " + rootPath);
  }

  public String generateContentTree() {
    String ret = super.generateContentTree();
    String zipName = "SpringerSample.zip";

    //  There should now be a suitable hierarchy at contentRoot,
    //  except that we need to copy the ZIP file into place and
    //  create a link to it.
    InputStream in = null;
    OutputStream os = null;
    try {
      in = this.getClass().getResourceAsStream(zipName);
      if (in == null) {
	throw new IOException(zipName + " missing");
      }
      File of = new File(contentRoot + File.separator + zipName);
      os = new FileOutputStream(of);
      byte[] buffer = new byte[4096];
      int i = 0;
      while ((i = in.read(buffer)) > 0) {
	os.write(buffer, 0, i);
	logger.debug2("Wrote " + i + " bytes of ZIP");
      }
      linkToZipFiles();
    } catch (IOException ex) {
      logger.error("copy threw " + ex);
      return null;
    } finally {
      IOUtil.safeClose(os);
      IOUtil.safeClose(in);
    }
    printZipFiles(0);
    return ret;
  }

  private void linkToZipFiles() {
    File dir = new File(contentRoot);
    if (dir.isDirectory()) {
      File index = new File(dir, INDEX_NAME);
      if (index.exists() && index.isFile()) try {
	FileOutputStream fos = new FileOutputStream(index);
	PrintWriter pw = new PrintWriter(fos);
	logger.debug3("Re-creating index file at " + index.getAbsolutePath());
	String file_content =
	  getIndexContent(dir, INDEX_NAME, LockssPermission.LOCKSS_PERMISSION_STRING);
	pw.print(file_content);
	pw.flush();
	pw.close();
	fos.close();
      } catch (IOException ex) {
	logger.error("linkToZipFiles() threw " + ex);
      } else {
	logger.error("index.html missing");
      }
    } else {
      logger.error("Directory " + contentRoot + " missing");
    }
  }

  private void printZipFiles(long startPosition) {
    File dir = new File(contentRoot);
    if (dir.isDirectory()) try {
      String[] fileNames = dir.list();

      for (int i = 0; i < fileNames.length; i++) {
	if (fileNames[i].endsWith(".zip")) {
	  logger.debug3(fileNames[i] + " headers offset" + startPosition);
		    
	  File aFile = new File(dir, fileNames[i]);
	  ZipFile zFile = new ZipFile(aFile);
	  for (Enumeration e = zFile.entries(); e.hasMoreElements(); ) {
	    ZipEntry ze = (ZipEntry)e.nextElement();
	    if (ze.isDirectory()) {
	      logger.debug3("Dir: " + ze.getName());
	    } else {
	      logger.debug3("File: " + ze.getName() + " size " + ze.getSize());
	    }
	  }
	}
      }
    } catch (IOException ex) {
      logger.error("printZipFiles() threw " + ex);
    } else {
      logger.error("Directory " + contentRoot + " missing");
    }
  }
}
