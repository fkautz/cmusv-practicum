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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.lockss.config.Tdb.TdbException;
import org.lockss.util.Logger;

/**
 * This class represents a title database publisher.
 *
 * @author  Philip Gust
 * @version $Id$
 */
public class TdbTitle {
  /**
   * Set up logger
   */
  protected final static Logger logger = Logger.getLogger("TdbTitle");

  /**
   * This enumeration defines a relationship between two titles,
   * corresponding to field 785 of a MARC record.
   * <p>
   * The first half of the enumeration represents forward relationships,
   * while the second half represents corresponding backward relationships
   * @author phil
   *
   */
  static public enum LinkType{
    // forward relationships
    continuedBy,
    continuedInPartBy,
    supersededBy,
    supersededInPartBy,
    absorbedBy,
    absorbedInPartBy,
    splitInto,
    mergedWith,
    changedBackTo,
    
    // backward relationships
    continues,
    continuesInPart,
    supersedes,
    supersedesInPart,
    absorbs,
    absorbsInPart,
    splitFrom,
    mergedFrom,
    changedBackFrom;
    
    /**
     * Return the inverse link type for this link type.  If this is a forward
     * link type, returns the backward link type.  If this is a backward
     * link type, returns the forward one.
     * 
     * @return the inverse link type
     */
    public LinkType inverseLinkType() {
      LinkType[] values = LinkType.values();
      return values[(ordinal() < values.length/2) ? (ordinal() + values.length/2) : (ordinal()-values.length/2)];
    }
    
    /**
     * Determines whether this is a forward link type.
     * 
     * @return <code>true</code> if a forward link type
     */
    public boolean isForwardLinkType() {
      return (ordinal() < LinkType.values().length/2);
    }
  }

  /**
   * The title name
   */
  final private String name;
  
  /**
   * The title ID
   */
  private String id;
  
  /**
   * The publisher for this title
   */
  TdbPublisher publisher = null;
  
  /**
   * A collection of AUs for this title
   */
  private final HashMap<TdbAu.Id, TdbAu> tdbAus = new HashMap<TdbAu.Id, TdbAu>();

  /**
   * A map of link types to a collection of title IDs
   */
  private Map<LinkType, Collection<String>> linkTitles;
  
  /**
   * Create a new instance for the specified name and id.
   * The title ID must be globally unique. For example, 
   * the ID of a journal may be its ISSN.
   * 
   * @param name the title name
   * @param id the title id
   */
  protected TdbTitle(String name, String id)
  {
    if (name == null) {
      throw new IllegalArgumentException("name cannot be null");
    }
    
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    this.name = name;
    this.id = id;
  }
  
  /**
   * Return the name of this title. The name is not guaranteed
   * to be unique within a given publisher
   * 
   * @return the name of this title
   */
  public String getName() {
    return name;
  }
  
  /**
   * Return the ID of this title. The ID is guaranteed to be
   * globally unique.  For example, the ID of a journal may be
   * its ISSN.
   * <p>
   * If ID is not set by the time this instance is added to its
   * TdbPublisher, the publisher will generate and assign one
   * to this instance.
   * 
   * @return the ID of this title or <code>null</code> if not set
   */
  public String getId() {
    return id;
  }
  
  /**
   * Return the publisher of this title.
   * 
   * @return the publisher of this title
   */
  public TdbPublisher getTdbPublisher() {
    return publisher;
  }
  
  /**
   * Set the publisher of this title.  
   * 
   * @param publisher the publisher of this title
   * @throws TdbException if publisher already set
   */
  protected void setTdbPublisher(TdbPublisher publisher) throws TdbException{
    if (publisher == null) {
      throw new IllegalArgumentException("title publisher cannot be null");
    }
    if (this.publisher != null) {
      throw new TdbException("publisher cannot be reset");
    }
    
    this.publisher = publisher;
  }
  
  /**
   * Add a title link for a specified link type.  Link types
   * define evolutionary changes of title and publisher that
   * enable queries to examine predecessor or successor titles
   * as they change over time.
   * <p>
   * Link types correspond to field 785 of the MARC format.
   * Backward (e.g. continues) as well as forward (e.g. continuedBy)
   * links can be added to facilitate navigation.
   * 
   * @param linkType the link type
   * @param title the title for the link
   */
  public void addLinkToTdbTitle(LinkType linkType, TdbTitle title) {
    if (title == null) {
      throw new IllegalArgumentException("title cannot be null");
    }
    addLinkToTdbTitleId(linkType, title.getId());
  }
  
  /**
   * Add a title link for a specified link type.  Link types
   * define evolutionary changes of title and publisher that
   * enable queries to examine predecessor or successor titles
   * as they change over time.
   * <p>
   * Link types correspond to field 785 of the MARC format.
   * Backward (e.g. continues) as well as forward (e.g. continuedBy)
   * links can be added to facilitate navigation.
   * 
   * @param linkType the link type
   * @param titleId the title ID for the link
   */
  public void addLinkToTdbTitleId(LinkType linkType, String titleId) {
    if (linkType == null) {
      throw new IllegalArgumentException("linkType cannot be null");
    }
    if (titleId == null) {
      throw new IllegalArgumentException("titleId cannot be null");
    }
    
    if (linkTitles == null) {
      if (System.getProperty("org.lockss.unitTesting", "false").equals("true")) {
        // use an ordered map to facilitate testing
        linkTitles = new LinkedHashMap<LinkType, Collection<String>>();
      } else {
        // use a standard map when not testing
        linkTitles = new HashMap<LinkType, Collection<String>>();
      }
    }
    Collection<String> targets = linkTitles.get(linkType);
    if (targets == null) {
      targets = new ArrayList<String>();
      linkTitles.put(linkType, targets);
    } else if (targets.contains(titleId)) {
      // duplicate target
      return;
    }
    targets.add(titleId);
  }
  /**
   * Returns a collection of title IDs for the specified link type.
   * <p>
   * Note: The returned collection should be treated as read-only
   * 
   * @param linkType the specified link type
   * @return
   */
  public Collection<String> getLinkedTdbTitleIdsForType(LinkType linkType) {
    if (linkType == null) {
      throw new IllegalArgumentException("linkType cannot be null");
    }
    if (linkTitles == null) {
      return Collections.EMPTY_LIST;
    }
    Collection<String> titleIds = linkTitles.get(linkType);
    return (titleIds != null) ? titleIds : Collections.EMPTY_LIST;
  }
  
  /**
   * Get all linked titles by link type.
   * <p>
   * Note: The returned map and its collections should be treated 
   * as read-only.
   * 
   * @return all linked titles by link type
   */
  public Map<LinkType,Collection<String>> getAllLinkedTitleIds() {
    return (linkTitles != null) ? linkTitles : Collections.EMPTY_MAP;
  }
  
  /**
   * Return all TdbAus for this title.
   * <p>
   * Note: the collection should be treated as read-only.
   * 
   * @return a collection of TdbAus for this title
   */
  public Collection<TdbAu> getTdbAus() {
    return tdbAus.values();
  }
  
  /**
   * Add a new TdbAu for this title.  All params must be set prior
   * to adding tdbAu to this title.
   * 
   * @param tdbAu a new TdbAus
   * @throws TdbException if trying to add different TdbAu with the same id as
   *   an existing TdbAu
   */
  public void addTdbAu(TdbAu tdbAu) throws TdbException {
    if (tdbAu == null) {
      throw new IllegalArgumentException("au cannot be null");
    }
    if (tdbAu.getPluginId() == null) {
      throw new IllegalArgumentException(
                        "cannot add au because its plugin ID is not set: \"" 
                      + tdbAu.getName() + "\"");
    }
    TdbTitle otherTitle = tdbAu.getTdbTitle();
    if (otherTitle == this) {
      throw new IllegalArgumentException(
                        "au entry \"" + tdbAu.getName() 
                      + "\" already exists in title \"" + name + "\"");
    } else if (otherTitle != null) {
      throw new IllegalArgumentException(
                        "au entry \"" + tdbAu.getName() 
                      + "\" already in another title: \"" + otherTitle.getName() + "\"");
    }

    // add au assuming that it is not a duplicate
    TdbAu.Id id = tdbAu.getId();
    TdbAu existingAu = tdbAus.put(id, tdbAu);
    if (existingAu == tdbAu) {
      // au is already added
      return;
    } else if (existingAu != null) {

      // au already added -- restore and report existing au
      tdbAus.put(id, existingAu);
      if (tdbAu.getName().equals(existingAu.getName())) {
        throw new TdbException(
                        "Cannot add duplicate au entry: \"" + tdbAu.getName() 
                      + "\" to title \"" + name + "\"");
      } else {
        // error because it could lead to a missing su -- one probably has a typo
        throw new TdbException(
                       "Cannot add duplicate au entry: \"" + tdbAu.getName() 
                     + "\" with the same id as existing au entry \"" + existingAu.getName()
                     + "\" to title \"" + name + "\"");
        }
    }
    try {
      tdbAu.setTdbTitle(this);
    } catch (TdbException ex) {
      // if we can't set the title, remove the au and re-throw exception
      tdbAus.remove(id);
      throw ex;
    }
  }
  
  /**
   * Return the number of TdbAus for this title.
   * 
   * @return the number of TdbAus
   */
  public int getTdbAuCount()
  {
    return tdbAus.size();
  }

  /**
   * Return the TdbAu for with the specified TdbAu name.
   * 
   * @param tdbAuName the name of the AU to select
   * @return the TdbAu for the specified name
   */
  public Collection<TdbAu> getTdbAusByName(String tdbAuName)
  {
    ArrayList<TdbAu> aus = new ArrayList<TdbAu>();
    for (TdbAu tdbAu : tdbAus.values()) {
      if (tdbAu.getName().equals(tdbAuName)) {
        aus.add(tdbAu);
      }
    }
    aus.trimToSize();
    return aus;
  }
  
  /**
   * Return the TdbAu for with the specified TdbAu.Key.
   * 
   * @param tdbAuId the Id of the TdbAu to select
   * @return the TdbAu for the specified key
   */
  public TdbAu getTdbAuById(TdbAu.Id tdbAuId)
  {
    return tdbAus.get(tdbAuId);
  }

  /**
   * Add all plugin IDs for this title.  This method is used
   * by {@link Tdb#getChangedPluginIds(Tdb)}.
   * 
   * @param pluginIds a set of plugin IDs
   */
  protected void addAllPluginIds(Set<String>pluginIds) {
    if (pluginIds == null) {
      throw new IllegalArgumentException("pluginIds cannot be null");
    }
    
    for (TdbAu au : tdbAus.values()) {
      pluginIds.add(au.getPluginId());
    }
  }
  
  /**
   * Determines two TdbsTitles are equal. Equality is based on having
   * equal TdbTitles and their child TdbAus.   The parent hierarchy is 
   * not checked.
   * 
   * @param o the other object
   * @return <code>true</code> iff they are equal TdbTitles
   */
  public boolean equals(Object o) {
    // check for identity
    if (this == o) {
      return true;
    }

    if (o instanceof TdbTitle) {
      try {
        // if no exception thrown, there are no differences
        // because the method did not try to modify the set
        addPluginIdsForDifferences(Collections.EMPTY_SET, (TdbTitle)o);
        return true;
      } catch (UnsupportedOperationException ex) {
        // differences because method tried to add to unmodifiable set
      } catch (IllegalArgumentException ex) {
        // if something was wrong with the other title
      } catch (TdbException ex) {
        // if something is wrong with this title
      }
    }
    return false;
  }

  /**
   * Return the hashcode.  The hashcode of this instance is the
   * hashcode of its Id.
   * 
   * @throws the hashcode of this instance
   */
  public int hashCode() {
    if (id == null) {
      throw new IllegalStateException("id not set");
    }
    return id.hashCode();
  }

  /**
   * Add all plugin IDs for TdbAus that are different between this and
   * the specified title.
   * <p>
   * This method is used by {@link Tdb#getChangedPluginIds(Tdb)}.
   * @param pluginIds the pluginIds for TdbAus that are different 
   * @throws TdbException if this TdbTitle's ID not set
   */
  protected void addPluginIdsForDifferences(Set<String>pluginIds, TdbTitle title) 
    throws TdbException {
    
    if (pluginIds == null) {
      throw new IllegalArgumentException("pluginIds cannot be null");
    }
    
    if (title == null) {
      throw new IllegalArgumentException("title cannot be null");
    }
    
    if (!title.getId().equals(id)) {
      throw new IllegalArgumentException("title ID \"" + title.getId() + "\" different than \"" + getId() + "\"");
    }
    
    if (   !title.getName().equals(name)
        || !title.getAllLinkedTitleIds().equals(this.getAllLinkedTitleIds())) {
      // titles have changed if they don't have the same names or links
      title.addAllPluginIds(pluginIds);
      this.addAllPluginIds(pluginIds);
    } else {

      // pluginIDs for TdbAus that only appear in title
      for (TdbAu titleAu : title.getTdbAus()) {
        if (!this.getTdbAus().contains(titleAu)) {
          // add pluginID for title AU that is not in this TdbTitle
          pluginIds.add(titleAu.getPluginId());
        }
      }
      for (TdbAu thisAu : this.getTdbAus()) {
        if (!title.getTdbAus().contains(thisAu)) {
          // add pluginId for AU in this TdbTitle that is not in title 
          pluginIds.add(thisAu.getPluginId());
        }
      }
    }
  }

  /**
   * Create a copy of this TdbTitle for the specified publisher.
   * TdbAus are not copied by this method.
   * <p>
   * This is method is used by Tdb to make a deep copy of a publisher.
   * 
   * @param publisher the publisher
   * @throws TdbException if publisher already has this title
   */
  protected TdbTitle copyForTdbPublisher(TdbPublisher publisher) throws TdbException {
    TdbTitle title = new TdbTitle(name, id);
    publisher.addTdbTitle(title);
    title.linkTitles = linkTitles;  // immutable: no need to copy
    return title;
  }

  /**
   * Return a String representation of the title.
   * 
   * @return a String representation of the title
   */
  public String toString() {
    return "[TdbTitle: " + name + "]";
  }
}
