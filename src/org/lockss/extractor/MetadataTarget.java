/*
 * $Id: MetadataTarget.java,v 1.2 2011/01/22 08:22:30 tlipkis Exp $
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.extractor;


/**
 * Describes the purpose for which metadata is being extracted and the
 * format desired.  Passed to ArticleIterator.
 */
public class MetadataTarget {

  /** Use when no knowledge of the particular type of metadata needed. */
  public static MetadataTarget Any = new MetadataTarget("Any");
  public static MetadataTarget DOI = new MetadataTarget("DOI");
  public static MetadataTarget OpenURL = new MetadataTarget("OpenURL");
  public static MetadataTarget Article = new MetadataTarget("Article");

  private String format;
  private String purpose;

  public MetadataTarget() {
  }

  public MetadataTarget(String purpose) {
    this.purpose = purpose;
  }

  public MetadataTarget setFormat(String format) {
    this.format = format;
    return this;
  }

  public String getFormat() {
    return format;
  }

  public MetadataTarget setPurpose(String purpose) {
    this.purpose = purpose;
    return this;
  }

  public String getPurpose() {
    return purpose;
  }
}
