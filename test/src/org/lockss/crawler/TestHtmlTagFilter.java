/*
 * $Id$
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.crawler;
import junit.framework.TestCase;
import java.io.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestHtmlTagFilter extends LockssTestCase {
  private static final String startTag1 = "<start>";
  private static final String endTag1 = "<end>";

  private static final String startTag2 = "<script>";
  private static final String endTag2 = "</script>";

  private static final HtmlTagFilter.TagPair tagPair1 =
    new HtmlTagFilter.TagPair(startTag1, endTag1);

  private static final HtmlTagFilter.TagPair tagPair2 =
    new HtmlTagFilter.TagPair(startTag2, endTag2);


  public TestHtmlTagFilter(String msg) {
    super(msg);
  }


  public void testCanNotCreateWithNullReader() {
    try {
      HtmlTagFilter filter =
	new HtmlTagFilter(null, new HtmlTagFilter.TagPair("blah", "blah"));
      fail("Trying to create a HtmlTagFilter with a null Reader should throw "+
	   "an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
  }

  public void testCanNotCreateWithNullTagPair() {
    try {
      HtmlTagFilter filter =
	new HtmlTagFilter(new StringReader("blah"),
			  (HtmlTagFilter.TagPair)null);
      fail("Trying to create a HtmlTagFilter with a null TagPair should "+
	   "throw an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
  }

  public void testCanNotCreateWithNullTagPairList() {
    try {
      HtmlTagFilter filter =
	new HtmlTagFilter(new StringReader("blah"),
			  (List)null);
      fail("Trying to create a HtmlTagFilter with a null TagPair list should "+
	   "throw an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
  }

  public void testCanNotCreateWithEmptyTagPairList() {
    try {
      HtmlTagFilter filter =
	new HtmlTagFilter(new StringReader("blah"), new LinkedList());
      fail("Trying to create a HtmlTagFilter with an empty TagPair should "+
	   "throw an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
  }

  public void testDoesNotModifyTagList() {
    List list = new LinkedList();
    list.add(tagPair1);
    list.add(tagPair2);
    HtmlTagFilter filter =
      new HtmlTagFilter(new StringReader("blah"), list);
    assertEquals(2, list.size());
    assertEquals(tagPair1, (HtmlTagFilter.TagPair)list.get(0));
    assertEquals(tagPair2, (HtmlTagFilter.TagPair)list.get(1));
  }

  public void testDoesNotFilterContentWithOutTags() throws IOException {
    String content = "This is test content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content.toString()),
			new HtmlTagFilter.TagPair("blah", "blah2"));

    assertReaderMatchesString(content, reader);
  }

  public void testFiltersSingleCharTags() throws IOException {
    String content = "This <is test >content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content),
			new HtmlTagFilter.TagPair("<", ">"));

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersSingleTagsNoNesting() throws IOException {
    String content = "This "+startTag1+"is test "+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testCaseInsensitive() throws IOException {
    String content = "This <Start> is test <END>content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersIgnoreEndTagWithNoStartTag() throws IOException {
    String content = "This is test "+endTag1+"content";
    String expectedContent = "This is test "+endTag1+"content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersTrailingTag() throws IOException {
    String content = "This "+startTag1+"is test content";
    String expectedContent = "This ";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersSingleTagsNestingVariant1() throws IOException {
    String content =
      "This "+startTag1+startTag1
      +"is test "+endTag1+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }


  public void testFiltersSingleTagsNestingVariant2() throws IOException {
    String content =
      "This "+startTag1+"is "+startTag1
      +"test "+endTag1+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersSingleTagsNestingVariant3() throws IOException {
    String content =
      "This "+startTag1+"is "+startTag1
      +"test "+endTag1+"error "+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersSingleTagsNestingVariant4() throws IOException {
    String content =
      "This "+startTag1+startTag1
      +"test "+endTag1+"is "+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    assertReaderMatchesString(expectedContent, reader);
  }


   public void testFiltersMultipleTagsNoNesting() throws IOException {
    String content =
      "This "+startTag1+"is "+endTag1
      +"test "+startTag2+"content"+endTag2+"here";
    String expectedContent = "This test here";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content),
			ListUtil.list(tagPair1, tagPair2));

    assertReaderMatchesString(expectedContent, reader);
  }

   public void testFiltersMultipleTagsComplexNesting() throws IOException {
    String content =
      startTag2+startTag1+endTag2+"blah1"+endTag1+endTag2+"blah2";

    String expectedContent = "blah2";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content),
			ListUtil.list(tagPair1, tagPair2));

    assertReaderMatchesString(expectedContent, reader);

    expectedContent = "blah1"+endTag1+endTag2+"blah2";
    reader =
      new HtmlTagFilter(new StringReader(content),
			ListUtil.list(tagPair2, tagPair1));
    assertReaderMatchesString(expectedContent, reader);
  }

  public void testFiltersMultipleTagsSimpleNesting() throws IOException {
    String content =
      "This "+startTag1+"is "+startTag2
      +"test "+endTag2+"content"+endTag1+"here";
    String expectedContent = "This here";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content),
			ListUtil.list(tagPair2, tagPair1));

    assertReaderMatchesString(expectedContent, reader);
  }

  public void testReadWithBuffer() throws IOException {
    String content = "This "+startTag1+"is test "+endTag1+"content";
    String expectedContent = "This content";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);


    char actual[] = new char[expectedContent.length()];
    reader.read(actual);
    assertEquals(expectedContent, new String(actual));
    assertEquals(-1, reader.read(actual));
  }

  public void testReadWithOffsetAndLength() throws IOException {
    String content = "This "+startTag1+"is test "+endTag1+"content";
    String expectedContent = "is cont";

    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    char actual[] = new char[expectedContent.length()];
    assertEquals(7, reader.read(actual, 2, 7));
    assertEquals(expectedContent, new String(actual));

    actual = new char[3];
    reader.read(actual);
    assertEquals("ent", new String(actual));
  }

  public void testReadReturnsNegOneWithTooLargeOffset() throws IOException {
    String content = "This "+startTag1+"is test "+endTag1+"content";
    HtmlTagFilter reader =
      new HtmlTagFilter(new StringReader(content), tagPair1);

    char actual[] = new char[5];
    assertEquals(-1, reader.read(actual, 100, 7));
  }    



  private void assertReaderMatchesString(String expected,
					 Reader reader)
  throws IOException{
    StringBuffer actual = new StringBuffer(expected.length());
    int kar;
    while ((kar = reader.read()) != -1) {
      actual.append((char)kar);
    }
    assertEquals(expected, actual.toString());
  }


  //Tests for TagPair
  public void testCanNotCreateTagPairWithNullStrings() {
    try {
      HtmlTagFilter.TagPair pair = new HtmlTagFilter.TagPair(null, "blah");
      fail("Trying to create a tag pair with a null string should throw "+
	   "an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
    try {
      HtmlTagFilter.TagPair pair = new HtmlTagFilter.TagPair("blah", null);
      fail("Trying to create a tag pair with a null string should throw "+
 	   "an IllegalArgumentException");
    } catch(IllegalArgumentException iae) {
    }
  }

  public void testTagPairNotEqual() {
    HtmlTagFilter.TagPair pair1 = new HtmlTagFilter.TagPair("blah1", "blah2");
    HtmlTagFilter.TagPair pair2 = new HtmlTagFilter.TagPair("bleh3", "bleh4");
    assertNotEquals(pair1, pair2);
  }

  public void testTagPairIsEqual() {
    HtmlTagFilter.TagPair pair1 = new HtmlTagFilter.TagPair("blah1", "blah2");
    HtmlTagFilter.TagPair pair2 = new HtmlTagFilter.TagPair("blah1", "blah2");
    assertEquals(pair1, pair2);
  }

  public void testTagPairNotEqualHash() {
    //To be fair, this is not guaranteed to succeed, since two objects
    //may hash to each other.  However, if all 7 of these have the same hash
    //value, somethign is probably wrong.
    HtmlTagFilter.TagPair pair1 = new HtmlTagFilter.TagPair("blah1", "bleh1");
    HtmlTagFilter.TagPair pair2 = new HtmlTagFilter.TagPair("blah2", "bleh2");
    HtmlTagFilter.TagPair pair3 = new HtmlTagFilter.TagPair("blah3", "bleh3");
    HtmlTagFilter.TagPair pair4 = new HtmlTagFilter.TagPair("blah4", "bleh4");
    HtmlTagFilter.TagPair pair5 = new HtmlTagFilter.TagPair("blah5", "bleh5");
    HtmlTagFilter.TagPair pair6 = new HtmlTagFilter.TagPair("blah6", "bleh6");
    HtmlTagFilter.TagPair pair7 = new HtmlTagFilter.TagPair("blah7", "bleh7");
    assertFalse((pair1.hashCode() == pair2.hashCode() &&
		 pair2.hashCode() == pair3.hashCode() &&
		 pair3.hashCode() == pair4.hashCode() &&
		 pair4.hashCode() == pair5.hashCode() &&
		 pair5.hashCode() == pair6.hashCode() &&
		 pair6.hashCode() == pair7.hashCode()));
  }

  public void testTagPairHasEqualHash() {
    HtmlTagFilter.TagPair pair1 = new HtmlTagFilter.TagPair("blah1", "blah2");
    HtmlTagFilter.TagPair pair2 = new HtmlTagFilter.TagPair("blah1", "blah2");
    assertEquals(pair1.hashCode(), pair2.hashCode());
  }


}

