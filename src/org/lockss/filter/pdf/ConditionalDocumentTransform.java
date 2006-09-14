/*
 * $Id$
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.filter.pdf;

import org.lockss.filter.pdf.DocumentTransformUtil.*;
import org.lockss.util.PdfUtil.ResultPolicy;

/**
 * <p>A document transform decorator that applies a "then" document
 * transform only if the PDF document to be transformed is recognized
 * by an "if" document transform.</p>
 * @author Thib Guicherd-Callin
 */
public class ConditionalDocumentTransform extends DocumentTransformDecorator {

  /**
   * <p>Builds a new conditional document transform using the given
   * strictness, out of the given "if" document transform and
   * "then" document transform.</p>
   * @param ifTransform    An "if" document transform.
   * @param thenStrictness True to wrap the "then" document transform
   *                       as a {@link StrictDocumentTransform} if it
   *                       is not one already, false otherwise.
   * @param thenTransform  A "then" document transform.
   * @see DocumentTransformDecorator#DocumentTransformDecorator(DocumentTransform)
   * @see AggregateDocumentTransform#AggregateDocumentTransform(DocumentTransform, DocumentTransform)
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      boolean thenStrictness,
                                      DocumentTransform thenTransform) {
    super(new AggregateDocumentTransform(ifTransform,
                                         !thenStrictness || thenTransform instanceof StrictDocumentTransform
                                         ? thenTransform
                                         : new StrictDocumentTransform(thenTransform)));
  }

  /**
   * <p>Builds a new conditional document transform using the given
   * strictness, out of the given "if" document transform and the
   * aggregation of the given "then" document transforms (using the
   * default aggregation result policy).</p>
   * @param ifTransform    An "if" document transform.
   * @param thenStrictness True to wrap the aggregated "then" document
   *                       transform as a
   *                       {@link StrictDocumentTransform}, false
   *                       otherwise.
   * @param thenTransform1  A "then" document transform.
   * @param thenTransform2  A "then" document transform.
   * @see #ConditionalDocumentTransform(DocumentTransform, boolean, DocumentTransform)
   * @see AggregateDocumentTransform#AggregateDocumentTransform(DocumentTransform, DocumentTransform)
   * @see AggregateDocumentTransform#POLICY_DEFAULT
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      boolean thenStrictness,
                                      DocumentTransform thenTransform1,
                                      DocumentTransform thenTransform2) {
    this(ifTransform,
         thenStrictness,
         new AggregateDocumentTransform(thenTransform1,
                                        thenTransform2));
  }

  /**
   * <p>Builds a new conditional document transform using the given
   * strictness, out of the given "if" document transform and the
   * aggregation of the given "then" document transforms (using the
   * given aggregation result policy).</p>
   * @param ifTransform      An "if" document transform.
   * @param thenStrictness   True to wrap the aggregated "then" document
   *                         transform as a
   *                         {@link StrictDocumentTransform}, false
   *                         otherwise.
   * @param thenResultPolicy A result policy for the aggregate "then"
   *                         document transform.
   * @param thenTransform1   A "then" document transform.
   * @param thenTransform2   A "then" document transform.
   * @see #ConditionalDocumentTransform(DocumentTransform, boolean, DocumentTransform)
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      boolean thenStrictness,
                                      ResultPolicy thenResultPolicy,
                                      DocumentTransform thenTransform1,
                                      DocumentTransform thenTransform2) {
    this(ifTransform,
         thenStrictness,
         new AggregateDocumentTransform(thenResultPolicy,
                                        thenTransform1,
                                        thenTransform2));
  }

  /**
   * <p>Builds a new conditional document transform using the default
   * strictness, out of the given "if" document transform and
   * "then" document transform.</p>
   * @param ifTransform    An "if" document transform.
   * @param thenTransform  A "then" document transform.
   * @see #ConditionalDocumentTransform(DocumentTransform, boolean, DocumentTransform)
   * @see #STRICTNESS_DEFAULT
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      DocumentTransform thenTransform) {
    this(ifTransform,
         STRICTNESS_DEFAULT,
         thenTransform);
  }

  /**
   * <p>Builds a new conditional document transform using the default
   * strictness, out of the given "if" document transform and the
   * aggregation of the given "then" document transforms (using the
   * default aggregation result policy).</p>
   * @param ifTransform      An "if" document transform.
   * @param thenTransform1   A "then" document transform.
   * @param thenTransform2   A "then" document transform.
   * @see #ConditionalDocumentTransform(DocumentTransform, boolean, DocumentTransform, DocumentTransform)
   * @see #STRICTNESS_DEFAULT
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      DocumentTransform thenTransform1,
                                      DocumentTransform thenTransform2) {
    this(ifTransform,
         STRICTNESS_DEFAULT,
         thenTransform1,
         thenTransform2);
  }

  /**
   * <p>Builds a new conditional document transform using the default
   * strictness, out of the given "if" document transform and the
   * aggregation of the given "then" document transforms (using the
   * given aggregation result policy).</p>
   * @param ifTransform    An "if" document transform.
   * @param thenResultPolicy A result policy for the aggregate "then"
   *                         document transform.
   * @param thenTransform1  A "then" document transform.
   * @param thenTransform2  A "then" document transform.
   * @see #ConditionalDocumentTransform(DocumentTransform, boolean, ResultPolicy, DocumentTransform, DocumentTransform)
   * @see #STRICTNESS_DEFAULT
   */
  public ConditionalDocumentTransform(DocumentTransform ifTransform,
                                      ResultPolicy thenResultPolicy,
                                      DocumentTransform thenTransform1,
                                      DocumentTransform thenTransform2) {
    this(ifTransform,
         STRICTNESS_DEFAULT,
         thenResultPolicy,
         thenTransform1,
         thenTransform2);
  }

  /**
   * <p>Te default strict policy for "then" transforms used by this
   * class.</p>
   */
  public static final boolean STRICTNESS_DEFAULT = true;

}