/*
 * $Id$
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin;

import java.io.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.repository.*;
import java.net.MalformedURLException;

/**
 * This is a generic file implementation of CachedUrl which uses the
 * {@link LockssRepository}.
 *
 * @author  Emil Aalto
 * @version 0.0
 */

public class GenericFileCachedUrl extends BaseCachedUrl {
  private LockssRepository repository;
  private LeafNode leaf = null;
  protected static Logger logger = Logger.getLogger("CachedUrl", Logger.LEVEL_DEBUG);

  public GenericFileCachedUrl(CachedUrlSet owner, String url) {
    super(owner, url);
    repository = LockssRepositoryImpl.repositoryFactory(owner.getArchivalUnit());
  }

  public boolean exists() {
    ensureLeafLoaded();
    return leaf.exists();
  }

  public InputStream openForReading() {
    ensureLeafLoaded();
    return leaf.getInputStream();
  }

  public Properties getProperties() {
    ensureLeafLoaded();
    return leaf.getProperties();
  }

  private void ensureLeafLoaded() {
    if (leaf==null) {
      try {
        leaf = (LeafNode)repository.getRepositoryNode(url);
        if (leaf==null) {
          leaf = repository.createLeafNode(url);
        }
      } catch (MalformedURLException mue) {
        logger.error("Couldn't load node due to bad url: "+url);
      }
    }
  }
}

