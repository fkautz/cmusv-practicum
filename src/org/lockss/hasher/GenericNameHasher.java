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

package org.lockss.hasher;
import java.security.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

/**
 * General class to handle name hashing
 */
public class GenericNameHasher extends GenericHasher {
  public static final char DELIMITER= '\n';

  byte[] nameBytes = null;
  int nameIdx = -1;
  boolean onFirstElement = true;

  public GenericNameHasher(CachedUrlSet cus, MessageDigest dig) {
    super(cus, dig);
    iterator = cus.flatSetIterator();
  }

  protected int hashElementUpToNumBytes(CachedUrlSetNode element, int numBytes) {
    if (nameBytes == null) {
      String nameStr = element.getUrl();
      StringBuffer sb = new StringBuffer(nameStr.length()+1);
      if (!onFirstElement) {
	sb.append(DELIMITER);
      }
      else {
	onFirstElement = false;
      }
      sb.append(nameStr);
      nameBytes = (sb.toString().getBytes());
      nameIdx = 0;
    }

    int len = numBytes < (nameBytes.length - nameIdx)
      ? numBytes : (nameBytes.length - nameIdx);
    digest.update(nameBytes, nameIdx, len);
    nameIdx += len;
    if (nameIdx >= nameBytes.length) {
      shouldGetNextElement = true;
      element = null;
      nameBytes = null;
    }
    return len;
  }

}


