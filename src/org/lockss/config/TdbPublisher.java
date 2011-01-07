/*
 * $Id$
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
n
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
package org.lockss.config;

import java.io.*;
import java.util.*;

import org.lockss.config.Tdb.TdbException;
import org.lockss.util.*;

/**
 * This class represents a title database publisher.
 *
 * @author  Philip Gust
 * @version $Id$
 */
public class TdbPublisher {
  /**
   * Set up logger
   */
  protected final static Logger logger = Logger.getLogger("TdbPublisher");

  /**
   * The name of the publisher
   */
  private final String name;
  
  /**
   * The map of title IDs to titles for this publisher
   */
  private final HashMap<String, TdbTitle> titlesById = new HashMap<String, TdbTitle>();


  /**
   * Create a new instance for the specified publisher name.
   * 
   * @param name the publisher name
   */
  protected TdbPublisher(String name)
  {
    if (name == null) {
      throw new IllegalArgumentException("name cannot be null");
    }
    this.name = name;
  }
  
  /**
   * Return the name of this publisher.
   * 
   * @return the name of this publisher
   */
  public String getName() {
    return name;
  }
  
  /**
   * Return the number of TdbTitles for this publisher.
   * 
   * @return the number of TdbTitles for this publisher
   */
  public int getTdbTitleCount() {
    return titlesById.size();
  }

  /**
   * Return the number of TdbTitles for this publisher.
   * 
   * @return the number of TdbTitles for this publisher
   */
  public int getTdbAuCount() {
    int auCount = 0;
    for (TdbTitle title : titlesById.values()) {
      auCount += title.getTdbAuCount();
    }
    return auCount;
  }

  /**
   * Return the collection of TdbTitles for this publisher.
   * <p>
   * Note: The returned collection should not be modified.
   * 
   * @return the collection of TdbTitles for this publisher
   */
  public Collection<TdbTitle> getTdbTitles() {
    return titlesById.values();
  }
  
  /**
   * Return the collection of TdbTitles for this publisher
   * with the specified title name.
   * 
   * @param titleName the title name
   * @return the set of TdbTitles with the specified title name
   */
  public Collection<TdbTitle> getTdbTitlesByName(String titleName) {
    if (titleName == null) {
      return Collections.emptyList();
    }

    ArrayList<TdbTitle> matchTitles = new ArrayList<TdbTitle>();
    for (TdbTitle title : titlesById.values()) {
      if (title.getName().equals(titleName)) {
        matchTitles.add(title);
      }
    }
    matchTitles.trimToSize();
    return matchTitles;
  }
  
  /**
   * Return the TdbTitle for this publisher with the 
   * specified title ID.
   * 
   * @param titleId the title ID 
   * @return the TdbTitle with the specified title ID. 
   */
  public TdbTitle getTdbTitleById(String titleId) {
    return titlesById.get(titleId);
  }
  
  /**
   * Add a new TdbTitle for this publisher. 
   * 
   * @param title a new TdbTitle
   * @throws IllegalArgumentException if the title ID is not set
   * @throws TdbException if trying to add different TdbTitle with same id
   *   as existing TdbTitle
   */
  protected void addTdbTitle(TdbTitle title) throws TdbException{
    if (title == null) {
      throw new IllegalArgumentException("published title cannot be null");
    }

    // add the title assuming that is not a duplicate
    String titleId = title.getId();
    TdbTitle existingTitle = titlesById.put(titleId, title);
    if (existingTitle == title) {
      // title already added
      return;
      
    } else if (existingTitle != null) {
      
      // title already added -- restore and report existing title
      titlesById.put(titleId, existingTitle);
      if (title.getName().equals(existingTitle.getName())) {
        throw new TdbException("Cannot add duplicate title entry: \"" + title.getName() 
                               + "\" for title id: " + titleId
                               + " to publisher \"" + name + "\"");
      } else {
        // error because it could lead to a missing AU -- one probably has a typo
        throw new TdbException(
                       "Cannot add duplicate title entry: \"" + title.getName() 
                     + "\" with the same id: " + titleId + " as existing title \"" 
                     + existingTitle.getName() + "\" to publisher \"" + name + "\"");
      }
    }

    try {
      title.setTdbPublisher(this);
    } catch (TdbException ex) {
      // if can't set the publisher, remove title and re-throw exception
      titlesById.remove(title.getId());
      throw ex;
    }
  }
  
  /**
   * Add plugin IDs of all TdbAus in this publisher to the input set.
   * This method is used by {@link Tdb#getChangedPluginIds(Tdb)}.
   * 
   * @param pluginIds the set of plugin IDs to add to.
   */
  protected void addAllPluginIds(Set<String>pluginIds) {
    for (TdbTitle title : titlesById.values()) {
      title.addAllPluginIds(pluginIds);
    }
  }
  
  /**
   * Add plugin IDs for all TdbAus in this publisher that are
   * different between this and the specified publisher.
   * <p>
   * This method is used by {@link Tdb#getChangedPluginIds(Tdb)}.
   * @param pluginIds the set of plugin IDs to add to 
   */
  protected void addPluginIdsForDifferences(Set<String>pluginIds, TdbPublisher publisher) {
    if (pluginIds == null) {
      throw new IllegalArgumentException("pluginIds cannot be null");
    }
    
    if (publisher == null) {
      throw new IllegalArgumentException("pubisher cannot be null");
    }
    
    // add pluginIDs for TdbTitles that only appear in publisher
    for (TdbTitle publisherTitle : publisher.getTdbTitles()) {
      if (this.getTdbTitleById(publisherTitle.getId()) == null) {
        // add all pluginIDs for publisher title not in this TdbPublisher
        publisherTitle.addAllPluginIds(pluginIds);
      }
    }
    
    // search titles in this publisher
    for (TdbTitle thisTitle : this.titlesById.values()) {
      TdbTitle publisherTitle = publisher.getTdbTitleById(thisTitle.getId());
      if (publisherTitle == null) {
        // add all pluginIDs for title not in publisher
        thisTitle.addAllPluginIds(pluginIds);
      } else {
        try {
        // add pluginIDs for differences in titles with the same title ID
        thisTitle.addPluginIdsForDifferences(pluginIds, publisherTitle);
        } catch (TdbException ex) {
          // won't happen because all titles for publisher have an ID
          logger.error("Internal error: title with no id: " + thisTitle, ex);
          
        }
      }
    }
  }
  
  /**
   * Determines two TdbsPublshers are equal. The parent hierarchy is not checked.
   * 
   * @param o the other object
   * @return <code>true</code> iff they are equal TdbPubishers
   */
  public boolean equals(Object o) {
    // check for identity
    if (this == o) {
      return true;
    }

    if (o instanceof TdbPublisher) {
      try {
        // if no exception thrown, there are no differences
        // because the method did not try to modify the set
        addPluginIdsForDifferences(Collections.<String>emptySet(), (TdbPublisher)o);
        return true;
      } catch (UnsupportedOperationException ex) {
        // differences because method tried to add to unmodifiable set
      } catch (IllegalArgumentException ex) {
        // if something was wrong with the other publisher
      } catch (IllegalStateException ex) {
        // if something is wrong with this publisher
      }
    }
    return false;
  }

  /** Print a full description of the publisher and all its titles */
  public void prettyPrint(PrintStream ps, int indent) {
    ps.println(StringUtil.tab(indent) + "Publisher: " + name);
    TreeMap<String, TdbTitle> sorted =
      new TreeMap<String, TdbTitle>(CatalogueOrderComparator.SINGLETON);
    for (TdbTitle title : getTdbTitles()) {
      sorted.put(title.getName(), title);
    }
    for (TdbTitle title : sorted.values()) {
      title.prettyPrint(ps, indent + 2);
    }
  }

  /**
   * Returns the hashcode.  The hashcode for this instance
   * is the hashcode of its name.
   * 
   * @return the hashcode for this instance
   */
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * Return a String representation of the publisher.
   * 
   * @return a String representation of the publisher
   */
  public String toString() {
    return "[TdbPublisher: " + name + "]";
  }
}
