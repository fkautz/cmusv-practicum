/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.daemon.status;

import java.util.*;
import java.text.SimpleDateFormat;

import org.lockss.test.*;
import org.lockss.util.*;

import org.w3c.dom.*;

public class TestXmlStatusTable extends LockssTestCase {
  XmlDomBuilder builder;

  public void testCreateTableDocument() throws Exception {
    StatusTable table = new StatusTable("table", "key");
    MockStatusAccessor accessor = new MockStatusAccessor();
    List colList = ListUtil.list(
      new ColumnDescriptor("intCol", "title1", ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("strCol", "title2", ColumnDescriptor.TYPE_STRING)
      );
    accessor.setColumnDescriptors(colList, "key");

    Object[][] rowObj = new Object[2][2];
    rowObj[0][0] = new Integer(123);
    rowObj[0][1] = "row1 string";
    StatusTable.DisplayedValue dispValue =
        new StatusTable.DisplayedValue(new Integer(456));
    dispValue.setColor("color1");
    dispValue.setBold(true);
    rowObj[1][0] = dispValue;
    StatusTable.Reference refValue =
        new StatusTable.Reference("row2 string", "table2", "key2");
    rowObj[1][1] = refValue;

    List rowList = accessor.makeRowsFrom(colList, rowObj);
    accessor.setRows(rowList, "key");

    Object[][] sumObj = new Object[1][3];
    sumObj[0][0] = "summary";
    sumObj[0][1] = new Integer(ColumnDescriptor.TYPE_STRING);
    sumObj[0][2] = "sum value";

    List sumList = accessor.makeSummaryInfoFrom(sumObj);
    accessor.setSummaryInfo("key", sumList);
    accessor.populateTable(table);
    XmlStatusTable xmlTable = new XmlStatusTable(table);
    Document tableDoc = xmlTable.createDocument();

    Element rootElem = tableDoc.getDocumentElement();
    builder = xmlTable.xmlBuilder;
    testHasColumnDescriptors(rootElem);
    testHasRows(rootElem);
    testHasSummaryInformation(rootElem);
  }

  private void testHasColumnDescriptors(Element rootElem) {
    NodeList cdElements =
        builder.getElementList(rootElem, XmlStatusConstants.COLUMNDESCRIPTOR);
    assertEquals(2, cdElements.getLength());

    // column 1
    Element cdElem1 = (Element)cdElements.item(0);
    Element name = builder.getElement(cdElem1, XmlStatusConstants.NAME);
    assertEquals("intCol", builder.getText(name));
    Element type = builder.getElement(cdElem1, XmlStatusConstants.TYPE);
    assertEquals(Integer.toString(ColumnDescriptor.TYPE_INT),
                 builder.getText(type));
    Element title = builder.getElement(cdElem1, XmlStatusConstants.TITLE);
    assertEquals("title1", builder.getText(title));

    // column 2
    Element cdElem2 = (Element)cdElements.item(1);
    name = builder.getElement(cdElem2, XmlStatusConstants.NAME);
    assertEquals("strCol", builder.getText(name));
    type = builder.getElement(cdElem2, XmlStatusConstants.TYPE);
    assertEquals(Integer.toString(ColumnDescriptor.TYPE_STRING),
                 builder.getText(type));
    title = builder.getElement(cdElem2, XmlStatusConstants.TITLE);
    assertEquals("title2", builder.getText(title));
  }

  private void testHasRows(Element rootElem) {
    NodeList rowElements =
        builder.getElementList(rootElem, XmlStatusConstants.ROW);
    assertEquals(2, rowElements.getLength());

    // row 1
    Element rowElem1 = (Element)rowElements.item(0);
    // 2 standard values
    NodeList values = builder.getElementList(rowElem1,
                                             XmlStatusConstants.STANDARD_ELEM);
    assertEquals(2, values.getLength());
    // standard values 1 & 2
    Node value1 = values.item(0);
    assertEquals("123", builder.getText(value1));
    Node value2 = values.item(1);
    assertEquals("row1 string", builder.getText(value2));

    // row 2
    Element rowElem2 = (Element)rowElements.item(1);
    // only 1 standard value
    values = builder.getElementList(rowElem2, XmlStatusConstants.STANDARD_ELEM);
    assertEquals(1, values.getLength());
    // display value
    Element dispValue = (Element)values.item(0);
    assertEquals("456", builder.getText(dispValue));
    assertEquals("color1", builder.getAttribute(dispValue,
                                                XmlStatusConstants.COLOR));
    assertEquals("true", builder.getAttribute(dispValue,
                                              XmlStatusConstants.BOLD));

    // 1 reference value
    values = builder.getElementList(rowElem2,
                                    XmlStatusConstants.REFERENCE_ELEM);
    assertEquals(1, values.getLength());
    Element refValue = (Element)values.item(0);
    Element name = builder.getElement(refValue, XmlStatusConstants.NAME);
    assertEquals("table2", builder.getText(name));
    Element key = builder.getElement(refValue, XmlStatusConstants.KEY);
    assertEquals("key2", builder.getText(key));
    Element value = builder.getElement(refValue, XmlStatusConstants.VALUE);
    assertEquals("row2 string", builder.getText(value));
  }

  private void testHasSummaryInformation(Element rootElem) {
    NodeList sumElements =
        builder.getElementList(rootElem, XmlStatusConstants.SUMMARYINFO);
    assertEquals(1, sumElements.getLength());

    Element sumElem = (Element)sumElements.item(0);
    Element title = builder.getElement(sumElem, XmlStatusConstants.TITLE);
    assertEquals("summary", builder.getText(title));
    Element type = builder.getElement(sumElem, XmlStatusConstants.TYPE);
    assertEquals(Integer.toString(ColumnDescriptor.TYPE_STRING),
                 builder.getText(type));
    Element value = builder.getElement(sumElem, XmlStatusConstants.VALUE);
    assertEquals("sum value", builder.getText(value));
  }

  public void testFormatByType() throws Exception {
    Object testObj = null;

    assertEquals("", format(testObj, ColumnDescriptor.TYPE_STRING));
    testObj = new Integer(123);
    assertEquals("123", format(testObj, ColumnDescriptor.TYPE_INT));
    testObj = new Float(123321);
    assertEquals(testObj.toString(),
                 format(testObj, ColumnDescriptor.TYPE_FLOAT));
    testObj = "test string";
    assertEquals("test string", format(testObj, ColumnDescriptor.TYPE_STRING));

    testObj = new Double(.45);
    assertEquals("45", format(testObj, ColumnDescriptor.TYPE_PERCENT));

    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, 2004);
    cal.set(Calendar.MONTH, Calendar.JANUARY);
    cal.set(Calendar.DATE, 1);
    cal.set(Calendar.HOUR_OF_DAY, 15);
    cal.set(Calendar.MINUTE, 15);
    testObj = cal.getTime();
    SimpleDateFormat sdf = new SimpleDateFormat(XmlStatusTable.DATE_FORMAT);
    assertEquals(sdf.format(testObj),
                 format(testObj, ColumnDescriptor.TYPE_DATE));

    testObj = IPAddr.getLocalHost();
    assertEquals(IPAddr.getLocalHost().getHostAddress(),
                 format(testObj, ColumnDescriptor.TYPE_IP_ADDRESS));

    long timeInt = Constants.HOUR + Constants.MINUTE;
    testObj = new Long(timeInt);
    assertEquals(StringUtil.timeIntervalToString(timeInt),
                 format(testObj, ColumnDescriptor.TYPE_TIME_INTERVAL));

    testObj = "unknown string";
    assertEquals("unknown string", format(testObj, -1));
  }

  private String format(Object obj, int type) {
    return XmlStatusTable.formatByType(obj, type);
  }
}
