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
import java.net.*;
import org.lockss.util.*;

/**
 * Returned by {@link StatusService#getTable(String, String)} 
 */
public class StatusTable {
  /** Adding this key to a row, with a non-null value, requests a separator
   * before the row. */
  public static Object ROW_SEPARATOR = new Object();

  public static final int OPTION_NO_ROWS = 1;

  private String name;
  private String key;
  private String title = null;
  private String titleFootnote;
  private List columnDescriptors;
  private Map columnDescriptorMap;
  private List rows;
  private List defaultSortRules;
  private static Logger logger = Logger.getLogger("StatusTable");
  private List summaryInfo;
  private BitSet options = new BitSet();
  private boolean isResortable = true;

  /**
   * @param name String representing table name
   * @param key String representing the key for this table, may be null
   */
  public StatusTable(String name, String key) {
    this.name = name;
    this.key = key;
  }

  /**
   * Constructor for tables that don't have a key
   * @param name String representing table name
   */
  public StatusTable(String name) {
    this(name, null);
  }

  private List makeDefaultSortRules() {
    ColumnDescriptor firstCol = (ColumnDescriptor)columnDescriptors.get(0);
    SortRule sortRule = new SortRule(firstCol.getColumnName(), true);
    return ListUtil.list(sortRule);
  }

  /**
   * Get the name of this table
   * @return name of this table
   */
  public String getName() {
    return name;
  }

  protected void setName(String name) {
    this.name = name;
  }

  /**
   * Get the key for this table
   * @return key for this table
   */
  public String getKey() {
    return key;
  }

  /**
   * Get the title for this table
   * @return title for this table
   */
  public String getTitle() {
    return title;
  }
  /**
   * Sets the title for this table
   * @param title title of this table
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Get the title footnote for this table
   * @return title footnote for this table
   */
  public String getTitleFootnote() {
    return titleFootnote;
  }

  /**
   * Set the title footnote for this table
   * @param footnote the title footnote for this table
   */
  public void setTitleFootnote(String footnote) {
    this.titleFootnote = footnote;
  }

  /**
   * Returns the options for this table
   * @return BitSet in which to set and test options
   */
  public BitSet getOptions() {
    return options;
  }

  /**
   * Returns a List of {@link SummaryInfo} objects for this table
   * @return List of {@link SummaryInfo} objects for this table
   */
  public List getSummaryInfo() {
    return summaryInfo;
  }

  /**
   * Sets a list of {@link SummaryInfo} objects for this table
   * @param summaryInfo list of {@link SummaryInfo} objects for this table
   */
  public void setSummaryInfo(List summaryInfo) {
    this.summaryInfo = summaryInfo;
  }

  
  /**
   * Gets a list of {@link ColumnDescriptor}s representing the 
   * columns in this table in their preferred display order.
   * @return list of {@link ColumnDescriptor}s the columns in 
   * the table in the preferred display order
   */
  public List getColumnDescriptors() {
    return columnDescriptors;
  }

  /**
   * Returns a map that maps column name to ColumnDescriptor
   */
  public Map getColumnDescriptorMap() {
    if (columnDescriptorMap == null) {
      columnDescriptorMap = new HashMap();
      for (Iterator iter = columnDescriptors.iterator(); iter.hasNext(); ) {
	ColumnDescriptor col = (ColumnDescriptor)iter.next();
	columnDescriptorMap.put(col.getColumnName(), col);
      }
    }
    return columnDescriptorMap;
  }

  /**
   * Sets a list of {@link ColumnDescriptor}s in their perferred display 
   * order for this table
   * @param columnDescriptors List of {@link ColumnDescriptor}s in their 
   * perferred display order for this table
   */
  public void setColumnDescriptors(List columnDescriptors) {
    this.columnDescriptors = columnDescriptors;
    columnDescriptorMap = null;
  }

  /**
   * Gets a list of {@link java.util.Map} objects for all the rows in the 
   * table in their default sort order.
   * @return list of {@link java.util.Map}s representing rows in the table 
   * in their default sort order 
   */
  public List getSortedRows() {
    if (rows == null) {
      return Collections.EMPTY_LIST;
    }
    if (defaultSortRules == null) {
      defaultSortRules = makeDefaultSortRules();
    }      
    return getSortedRows(defaultSortRules);
  }

  /**
   * Same as getSortedRows(), but will sort according to the rules 
   * specified in sortRules
   * @param sortRules list of {@link StatusTable.SortRule} objects describing
   *  how to sort  the rows
   * @return list of {@link java.util.Map}s representing rows in the table 
   * in the sort order specified by sortRules 
   */
  public List getSortedRows(List sortRules) {
    Collections.sort(rows, new SortRuleComparator(sortRules,
						  getColumnDescriptorMap()));
    return rows;
  }

  /**
   * Set the rows ({@link Map}s) for this table
   * @param rows List of unsorted rows for this table
   */
  public void setRows(List rows) {
    this.rows = rows;
  }

  /** Return the actual value, possibly embedded in a {@link
   * StatusTable.DisplayedValue} and/or a {@link
   * StatusTable.Reference}
   * @param value an object, possibly an DisplayedValue or Reference
   * @return The innermost embedded value that is not an DisplayedValue
   * or a Reference.
   */
  public static Object getActualValue(Object value) {
    while (value instanceof EmbeddedValue) {
      value = ((EmbeddedValue)value).getValue();
    }
    return value;
  }

  /**
   * Sets the default {@link StatusTable.SortRule}s for this table
   * @param defaultSortRules List of default {@link StatusTable.SortRule}s 
   * for this table
   */
  public void setDefaultSortRules(List defaultSortRules) {
    this.defaultSortRules = defaultSortRules;
  }

  /**
   * Gets the default {@link StatusTable.SortRule}s for this table
   */
  public List getDefaultSortRules() {
    return defaultSortRules;
  }

  /** Set whether the table may be resorted by the user via the UI */
  public void setResortable(boolean isResortable) {
    this.isResortable = isResortable;
  }

  /** @return true if the table allows sorting from the UI */
  public boolean isResortable() {
    return isResortable;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[StatusTable:");
    sb.append(name);
    sb.append(", ");
    sb.append(key);
    sb.append(", ");
    sb.append(columnDescriptors);
    sb.append(", ");
    sb.append(rows);
    sb.append("]");
    return sb.toString();
  }

  /**
   * Interface for embedded values
   */
  public interface EmbeddedValue {
    public Object getValue();
  }

  /**
   * Wrapper for a value with additional display properties.
   */
  public static class DisplayedValue implements EmbeddedValue {
    private Object value;
    private String color = null;
    private boolean bold = false;

    /** Create a DisplayedValue with the specified value.  Any value is
     * legal except a Reference or another DisplayedValue. */
    public DisplayedValue(Object value) {
      if (value instanceof EmbeddedValue) {
	throw new IllegalArgumentException("Value of a DisplayedValue can't be a DisplayedValue or Reference");
      }
      this.value = value;
    }

    /** Get the value */
    public Object getValue() {
      return value;
    }

    /** Set the color.
     * @param color the name of the color (understandable by html)
     */
    public void setColor(String color) {
      this.color = color;
    }

    /** Get the color */
    public String getColor() {
      return color;
    }

    /** Set bold.
     * @param bold true if should be bold
     */
    public void setBold(boolean bold) {
      this.bold = bold;
    }

    /** Get the bold */
    public boolean getBold() {
      return bold;
    }

  }
  /**
   * Object which refers to another table
   */
  public static class Reference implements EmbeddedValue {
    private Object value;
    private String tableName;
    private String key;

    /**
     * Create a Reference object with an embedded value.
     * @param value value to be displayed.  Any value is
     * legal except a Reference or a DisplayedValue.
     * @param tableName name of the {@link StatusTable} that this 
     * links to
     * @param key object further specifying the table this links to
     */
    public Reference(Object value, String tableName, String key){
      if (value instanceof Reference) {
	throw new IllegalArgumentException("Value of a Reference can't be a Reference");
      }
      this.value = value;
      this.tableName = tableName;
      this.key = key;
    }

    public Object getValue() {
      return value;
    }
    
    public String getTableName() {
      return tableName;
    }

    public String getKey() {
      return key;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("[StatusTable.Reference:");
      sb.append(value);
      sb.append(", ");
      sb.append(tableName);
      sb.append(", ");
      sb.append(key);
      sb.append("]");
      return sb.toString();
    }

    public boolean equals(Object obj) {
      if (! (obj instanceof StatusTable.Reference)) {
  	return false;
      }
      StatusTable.Reference ref = (StatusTable.Reference)obj;
      if (!value.equals(ref.getValue())) {
	return false;
      }
      if (!tableName.equals(ref.getTableName())) {
	return false;
      }

      //true iff both strings are equal or null
      return StringUtil.equalStrings(key, ref.getKey());
    }
  }

  /**
   * Object representing scalar information in a table
   */
  public static class SummaryInfo {
    private String title;
    private int type;
    private Object value;
    private String footnote;

    /**
     * @param title title for this SummaryInfo
     * @param type int representing the type of value
     * @param value value object associated with this SummaryInfo
     */    
    public SummaryInfo(String title, int type, Object value) {
      this.title = title;
      this.type = type;
      this.value = value;
    }

    public String getTitle() {
      return this.title;
    }

    public int getType() {
      return this.type;
    }

    public Object getValue() {
      return value;
    }

    public String getFootnote() {
      return this.footnote;
    }

    public void setFootnote(String footnote) {
      this.footnote = footnote;
    }
  }

  static class SortRuleComparator implements Comparator {
    List sortRules;

    public SortRuleComparator(List sortRules, Map columnDescriptorMap) {
      this.sortRules = sortRules;
      setSortTypes(columnDescriptorMap);
    }

    private void setSortTypes(Map columnDescriptorMap) {
      Iterator it = sortRules.iterator();
      while (it.hasNext()) {
	SortRule rule = (SortRule)it.next();
	if (rule.getColumnType() < 0) {
	  rule.inferColumnType(columnDescriptorMap);
	}
      }
    }

    public int compare(Object a, Object b) {
      Map rowA = (Map)a;
      Map rowB = (Map)b;
      int returnVal = 0;
      Iterator it = sortRules.iterator();

      while (returnVal == 0 && it.hasNext()){
	SortRule sortRule = (SortRule)it.next();
	String colName = sortRule.getColumnName();
	// Either object might be either a Reference or an
	// DisplayedValue.  We want to compare the actual value.
	Object valA = getActualValue(rowA.get(colName));
	Object valB = getActualValue(rowB.get(colName));
	returnVal = sortRule.compare(valA, valB);
      }
      return returnVal;
    }
    
  }

  /**
   * Encapsulation of the info needed to sort on a single field
   */
  public static class SortRule {
    String columnName;
    boolean sortAscending;
    Comparator comparator = null;
    int columnType = -1;
    
    public SortRule(String columnName, boolean sortAscending) {
      this.columnName = columnName;
      this.sortAscending = sortAscending;
    }

    public SortRule(String columnName, boolean sortAscending, int columnType) {
      this(columnName, sortAscending);
      this.columnType = columnType;
    }

    public SortRule(String columnName, Comparator comparator) {
      this(columnName, comparator, true);
    }

    public SortRule(String columnName, Comparator comparator,
		    boolean sortAscending) {
      this.columnName = columnName;
      this.comparator = comparator;
      this.sortAscending = sortAscending;
    }

    /**
     * @return name of the field to sort on
     */
    public String getColumnName(){
      return columnName;
    }
    
    /**
     * @return the value type for the column
     */
    public int getColumnType(){
      return columnType;
    }
    
    /**
     * @return true if this column should be sorted in ascending order,
     * false if it should be sorted in descending order
     */
    public boolean sortAscending(){
      return sortAscending;
    }

    /**
     * @return the comparator, or null if no explicit comparator supplied
     */
    public Comparator getComparator(){
      return comparator;
    }
    
    /**
     * Lookup the column type in the columnDescriptors, store in self
     * @param columnDescriptors
     */
    void inferColumnType(Map columnDescriptorMap){
      ColumnDescriptor col =
	(ColumnDescriptor)columnDescriptorMap.get(columnName);
      if (col != null) {
	columnType = col.getType();
	if (comparator == null) {
	  comparator = col.getComparator();
	}
	return;
      }
      // XXX this isn't really an error, just somebody sorting on a
      // column that isn't displayed.
//       logger.warning("Unknown type for sort column: "+ columnName);
      columnType = ColumnDescriptor.TYPE_INT;
    }

    public int compare(Object valA, Object valB) {
      int returnVal = 0;
      if (comparator != null) {
	returnVal = comparator.compare(valA, valB);
      } else {
	switch (getColumnType()) {
	case ColumnDescriptor.TYPE_IP_ADDRESS:
	  returnVal = compareIPAddrs((IPAddr)valA, (IPAddr)valB);
	  break;
	case ColumnDescriptor.TYPE_INT:
	case ColumnDescriptor.TYPE_FLOAT:
	case ColumnDescriptor.TYPE_PERCENT:
	case ColumnDescriptor.TYPE_TIME_INTERVAL:
	case ColumnDescriptor.TYPE_STRING:
	default: //if we don't know the type, assume comparable
	  returnVal = compareHandlingNulls((Comparable)valA, (Comparable)valB);
	  break;
	}
      }
      return sortAscending ? returnVal : -returnVal;
    }

    private static int compareIPAddrs(IPAddr addr1, IPAddr addr2) {
      return (addr1.getHostAddress().compareTo(addr2.getHostAddress()));
    }

    static int compareHandlingNulls(Comparable val1,
					    Comparable val2) {
      int returnVal = 0;
      if (val1 == null) {
	returnVal = val2 == null ? 0 : -1;
      } else if (val2 == null) {
	returnVal = 1;
      } else {
	returnVal = val1.compareTo(val2);
      }
      return returnVal;
    }

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("[SortRule: ");
      sb.append(columnName);
      sb.append(sortAscending ? ":A" : "D:");
      if (comparator != null) {
	sb.append(":");
	sb.append(comparator.toString());
      }
      sb.append("]");
      return sb.toString();
    }
  }
}
