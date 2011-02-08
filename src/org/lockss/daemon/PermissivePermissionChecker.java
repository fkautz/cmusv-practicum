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
import org.lockss.state.*;

/**
 * An implementation of PermissionChecker that looks for an RDF
 * statement granting permission to distribute the licensed work.
 * Used by RegistryArchivalUnit to check for plugin redistribution
 * permission.
 *
 * Currently does not support checking any other license restrictions
 * or permissions.
 */
public class PermissivePermissionChecker extends BasePermissionChecker {

  public PermissivePermissionChecker() {
  }

  /**
   * Always returns true. For testing purposes only. Should not be used in real plugins.
   */
  public boolean checkPermission(Crawler.PermissionHelper pHelper,
				 Reader reader, String permissionUrl) {
    return true;
  }
}
