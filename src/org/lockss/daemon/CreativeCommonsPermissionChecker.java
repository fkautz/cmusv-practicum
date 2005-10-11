/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.daemon;

import java.io.*;
import java.util.*;

import edu.stanford.db.rdf.model.i.StatementImpl;

import org.w3c.rdf.implementation.model.*;
import org.w3c.rdf.implementation.syntax.sirpac.*;
import org.w3c.rdf.model.*;
import org.w3c.rdf.syntax.*;
import org.w3c.rdf.util.*;
import org.xml.sax.*;

import org.lockss.util.*;

/**
 * An implementation of PermissionChecker that looks for an RDF
 * statement granting permission to distribute the licensed work.
 * Used by RegistryArchivalUnit to check for plugin redistribution
 * permission.
 *
 * Currently does not support checking any other license restrictions
 * or permissions.
 */
public class CreativeCommonsPermissionChecker
  implements PermissionChecker {

  private static String RDF_START = "<rdf:RDF ";
  private static String RDF_END = "</rdf:RDF>";

  // Maximum size for the RDF buffer
  private static int RDF_BUF_LEN = 65535;

  // License resource
  private static final Resource LICENSE =
    new ResourceImpl("http://web.resource.org/cc/license");

  // Permission-kind resources
  private static final Resource PERMITS =
    new ResourceImpl("http://web.resource.org/cc/permits");
  private static final Resource REQUIRES =
    new ResourceImpl("http://web.resources.org/cc/requires");
  private static final Resource PROHIBITS =
    new ResourceImpl("http://web.resources.org/cc/prohibits");

  // Permission labels
  private static final String DERIVATIVE_WORKS =
    "http://web.resource.org/cc/DerivativeWorks";
  private static final String REPRODUCTION =
    "http://web.resource.org/cc/Reproduction";
  private static final String DISTRIBUTION =
    "http://web.resource.org/cc/Distribution";

  private static final Logger log =
    Logger.getLogger("CreativeCommonsPermissionChecker");

  // The URI of the resource being checked for permission
  private String m_licenseURI;

  /**
   * Construct a Creative Commons permission checker with a license
   * location URI.  If the CC RDF being parsed does not have a URI set
   * for the "Work" element's "about:rdf" attribute, this will just be
   * ignored.  But if it does, this URL *must* match the attribute's
   * value in order to be a valid license.
   *
   * <p>For example, if the license Work opening element reads
   * &lt;Work rdf:about=""&gt;, then this URI will be ignored (but, for
   * SAX parser reasons, must be valid).  If the license Work opening
   * element reads &lt;Work rdf:about="http://some.url/foo/"&gt;, then
   * the licenseURI must be "http://some.url/foo/", or the license will
   * not be considered valid.
   */
  /*  public CreativeCommonsPermissionChecker(String licenseURI) {
    m_licenseURI = licenseURI;
  }
  */

  public CreativeCommonsPermissionChecker() {
  }

  /**
   * Check for "Distribution" permission granted by a Creative Commons
   * License.
   */
  public boolean checkPermission(Reader reader, String permissionUrl) {
    if (reader == null) {
      throw new NullPointerException("Called with null reader");
    } else if (permissionUrl == null) {
      throw new NullPointerException("Called with null permissionUrl");
    }

    String rdfString;

    try {
      rdfString = extractRDF(reader);
    } catch (IOException ex) {
      log.warning("Extracting RDF caused an IOException", ex);
      return false;
    }

    // extractRDF will return null if no RDF is found
    if (rdfString == null) {
      log.warning("No Creative Commons RDF found to parse.");
      return false;
    }


    Model model = new RDFFactoryImpl().createModel();

    RDFParser parser = new SiRPAC();
    // Default error handler prints to stderr.  This error handler
    // will log any RDF parsing errors to the LOCKSS cache log
    // instead.
    parser.setErrorHandler(new LoggingErrorHandler());
    try {
      InputSource source = new InputSource(new StringReader(rdfString));
//       source.setSystemId(m_licenseURI);
      source.setSystemId(permissionUrl);
      parser.parse(source, new ModelConsumer(model));
    } catch (Exception ex) {
      // Can throw SAXException or ModelException
      log.warning("Exception while parsing RDF.", ex);
      return false;
    }

    log.debug("Extracted RDF.");

    try {
      // Find the license type to use as the subject key
      // for obtaining "permission" triples from this RDF model.
      Model license =
// 	(Model)model.find(new ResourceImpl(m_licenseURI), LICENSE, null);
	model.find(new ResourceImpl(permissionUrl), LICENSE, null);

      if (license == null) {
	log.warning("No 'license' resource.  Invalid CC RDF.");
	return false;
      }

      String licenseType = null;
      for (Enumeration e = license.elements(); e.hasMoreElements(); ) {
	Statement triple = (StatementImpl)e.nextElement();
	// find the first valid license type.  It's not clear whether
	// it would be valid to have more than one (seems unlikely),
	// or what to do if it is valid.
	licenseType = triple.object().getLabel();
	break;
      }

      if (licenseType == null) {
	log.warning("No CC license type found.");
	return false;
      }

      // Now loop through all the permission statement triples looking for one
      // that permits redistribution.
      Model permission = model.find(new ResourceImpl(licenseType), null, null);
      for (Enumeration e = permission.elements(); e.hasMoreElements(); ) {
	Statement triple = (StatementImpl)e.nextElement();
	if (PERMITS.equals(triple.predicate()) &&
	    DISTRIBUTION.equals(triple.object().getLabel())) {
	  // Valid distribution permission found!
	  log.debug("Permission granted.");
	  return true;
	}
      }
    } catch (ModelException ex) {
      log.warning("Couldn't parse RDF", ex);
    }

    // No permission granted if this point is reached.
    return false;
  }

  /**
   * Extract RDF from a reader.  This method will read from the input
   * reader until it has found the closing RDF tag, or until the
   * reader is exhausted.  It will not close the reader.
   *
   * Because it builds a String representation of the RDF, it will
   * only read to a predetermined maximum length to avoid problems
   * with unbounded input. Since it targets Creative Commons License
   * RDF blocks, the size of the buffer is fairly small, 65535 chars.
   * Anything larger than that is almost certainly not a properly
   * formed CC licence.
   */
  private String extractRDF(Reader in) throws IOException {
    boolean found_start = false;
    boolean found_end = false;

    char[] buf = new char[RDF_BUF_LEN];

    int in_pos = 0;
    int buf_pos = 0;

    int start_len = RDF_START.length();
    int end_len = RDF_END.length();

    int c;
    while ((c = in.read()) != -1) {
      if (buf_pos > RDF_BUF_LEN) {
	// Too long to fit in buffer.
	log.warning("RDF block too long to fit in buffer.");
	return null;
      }

      buf[buf_pos++] = (char)c;

      if (!found_start) {
	if (c != RDF_START.charAt(in_pos++)) {
	  in_pos = 0;
	  buf_pos = 0;
	  continue;
	}

	if (in_pos == start_len) {
	  log.debug3("Found start of RDF");
	  found_start = true;
	  in_pos = 0;
	  continue;
	}
      } else {
	// Found the starting token, read until
	// the ending token is found.
	if (c != RDF_END.charAt(in_pos++)) {
	  in_pos = 0;
	}
	if (in_pos == end_len) {
	  log.debug3("Found end of RDF");
	  found_end = true;
	  break; // done with this stream.
	}
      }
    }

    if (found_end) {
      return new String(buf, 0, buf_pos);
    } else {
      log.debug3("Got to end of reader and didn't find the RDF end");
      return null;
    }
  }

  /**
   * Simple RDF error handler that logs output instead of dumping to
   * stderr.
   */
  private static class LoggingErrorHandler implements ErrorHandler {
    public void error(SAXParseException e) {
      log.warning("RDF Parser: " + e);
    }

    public void fatalError(SAXParseException e) {
      log.error("RDF Parser: " + e);
    }

    public void warning(SAXParseException e) {
      log.warning("RDF Parser: " + e);
    }
  }
}
