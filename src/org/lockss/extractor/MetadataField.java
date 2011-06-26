/*
 * $Id: MetadataField.java,v 1.5 2011/06/26 05:42:06 pgust Exp $
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

import java.util.*;

import org.lockss.util.*;
import org.apache.commons.lang.StringUtils;

/**
 * Describes a field (key-value pair) in metadata: key name, required
 * cardinality and validator, if any.
 */
public class MetadataField {
  static Logger log = Logger.getLogger("MetadataField");

  /*
   * The canonical representation of a DOI has key "dc.identifier"
   * and starts with doi:
   */
  public static final String PROTOCOL_DOI = "doi:";
  public static final String KEY_DOI = "doi";
  public static final MetadataField FIELD_DOI =
    new MetadataField(KEY_DOI, Cardinality.Single) {
      @Override
      public String validate(ArticleMetadata am, String val)
	  throws MetadataException.ValidationException {
	// normalize away leading "doi:" before checking validity
	String doi = StringUtils.removeStartIgnoreCase(val, PROTOCOL_DOI);
	if (!MetadataUtil.isDOI(doi)) {
	  throw new MetadataException.ValidationException("Illegal DOI: "
							  + val);
	}
	return doi;
      }};	


  public static final String PROTOCOL_ISSN = "issn:";
  public static final String KEY_ISSN = "issn";
  public static final MetadataField FIELD_ISSN =
    new MetadataField(KEY_ISSN, Cardinality.Single) {
      @Override
      public String validate(ArticleMetadata am, String val)
	  throws MetadataException.ValidationException {
	// normalize away leading "issn:" before checking validity
	String issn = StringUtils.removeStartIgnoreCase(val, PROTOCOL_ISSN);
	if (!MetadataUtil.isISSN(issn)) {
	  throw new MetadataException.ValidationException("Illegal ISSN: "
							  + val);
	}
	return issn;
      }};

  public static final String PROTOCOL_EISSN = "eissn:";
  public static final String KEY_EISSN = "eissn";
  public static final MetadataField FIELD_EISSN =
    new MetadataField(KEY_EISSN, Cardinality.Single) {
      @Override
      public String validate(ArticleMetadata am, String val)
	  throws MetadataException.ValidationException {
	// normalize away leading "eissn:" before checking validity
	String issn = StringUtils.removeStartIgnoreCase(val, PROTOCOL_EISSN);
	if (!MetadataUtil.isISSN(issn)) {
	  throw new MetadataException.ValidationException("Illegal EISSN: "
							  + val);
	}
	return issn;
      }};
      
  public static final String PROTOCOL_ISBN = "isbn:";
  public static final String KEY_ISBN = "isbn";
  public static final MetadataField FIELD_ISBN =
    new MetadataField(KEY_ISBN, Cardinality.Single) {
    @Override
    public String validate(ArticleMetadata am, String val)
        throws MetadataException.ValidationException {
      // normalize away leading "isbn:" before checking validity
      String isbn = StringUtils.removeStartIgnoreCase(val, PROTOCOL_ISBN);
      if (!MetadataUtil.isISBN(isbn, false)) {  // ignore publisher malformed ISBNs
        throw new MetadataException.ValidationException("Illegal ISBN: "
                                                        + val);
      }
      return isbn;
    }};

  public static final String KEY_PUBLISHER = "publisher";
  public static final MetadataField FIELD_PUBLISHER =
    new MetadataField(KEY_PUBLISHER, Cardinality.Single);

  public static final String KEY_VOLUME = "volume";
  public static final MetadataField FIELD_VOLUME =
    new MetadataField(KEY_VOLUME, Cardinality.Single);

  public static final String KEY_ISSUE = "issue";
  public static final MetadataField FIELD_ISSUE =
    new MetadataField(KEY_ISSUE, Cardinality.Single);

  public static final String KEY_START_PAGE = "startpage";
  public static final MetadataField FIELD_START_PAGE =
    new MetadataField(KEY_START_PAGE, Cardinality.Single);

  /*
   * A date can be just a year, a month and year, or a specific issue date.
   */
  public static final String KEY_DATE = "date";
  public static final MetadataField FIELD_DATE =
    new MetadataField(KEY_DATE, Cardinality.Single);

  public static final String KEY_ARTICLE_TITLE = "article.title";
  public static final MetadataField FIELD_ARTICLE_TITLE =
    new MetadataField(KEY_ARTICLE_TITLE, Cardinality.Single);

  public static final String KEY_JOURNAL_TITLE = "journal.title";
  public static final MetadataField FIELD_JOURNAL_TITLE =
    new MetadataField(KEY_JOURNAL_TITLE, Cardinality.Single);

  /* Author is currently a delimited list of one or more authors. */
  public static final String KEY_AUTHOR = "author";
  public static final MetadataField FIELD_AUTHOR =
    new MetadataField(KEY_AUTHOR, Cardinality.Multi);

  public static final String KEY_ACCESS_URL = "access.url";
  public static final MetadataField FIELD_ACCESS_URL =
    new MetadataField(KEY_ACCESS_URL, Cardinality.Single);

  public static final String KEY_KEYWORDS = "keywords";
  public static final MetadataField FIELD_KEYWORDS =
    new MetadataField(KEY_KEYWORDS, Cardinality.Multi);

  // Dublin code fields.  See http://dublincore.org/documents/dces/ for
  // more information on the fields.  See also 
  // http://scholar.google.com/intl/en/scholar/inclusion.html for
  // recommended usage of citation subfields for serial publications
  // and books. The National Library of Medicine of the National Institutes 
  // of Health has also published the NLM Metadata Schema that includes
  // approved Dublin Core elements with approved NLM defined qualifiers.
  // See: http://www.nlm.nih.gov/tsd/cataloging/metafilenew.html.
  
  /** The chapter of a book (Google Scholar non-standard) */
  public static final String DC_KEY_CITATION_CHAPTER = "dc.citation.chapter";
  public static final MetadataField DC_FIELD_CITATION_CHAPTER =
    new MetadataField(DC_KEY_CITATION_CHAPTER, Cardinality.Single);

  /** 
   * The ending page of an article in a serial or a chapter in a book
   * (Google Scholar non-standard).
   */
  public static final String DC_KEY_CITATION_EPAGE = "dc.citation_epage";
  public static final MetadataField DC_FIELD_CITATION_EPAGE =
    new MetadataField(DC_KEY_CITATION_EPAGE, Cardinality.Single);

  /** The issue of a serial publication (Google Scholar non-standard) */
  public static final String DC_KEY_CITATION_ISSUE = "dc.citation.issue";
  public static final MetadataField DC_FIELD_CITATION_ISSUE =
    new MetadataField(DC_KEY_CITATION_ISSUE, Cardinality.Single);

  /** 
   * The starting page of an article in a serial or a chapter in a book 
   * (Google Scholar non-standard).
   */
  public static final String DC_KEY_CITATION_SPAGE = "dc.citation_spage";
  public static final MetadataField DC_FIELD_CITATION_SPAGE =
    new MetadataField(DC_KEY_CITATION_SPAGE, Cardinality.Single);

  /** The volume of a serial publication (Google Scholar non-standard). */
  public static final String DC_KEY_CITATION_VOLUME = "dc.citation_volume";
  public static final MetadataField DC_FIELD_CITATION_VOLUME =
    new MetadataField(DC_KEY_CITATION_VOLUME, Cardinality.Single);

  /** An entity responsible for making contributions to the resource. */
  public static final String DC_KEY_CONTRIBUTOR = "dc.contributor";
  public static final MetadataField DC_FIELD_CONTRIBUTOR =
    new MetadataField(DC_KEY_CONTRIBUTOR, Cardinality.Multi);

  /** 
   * The spatial or temporal topic of the resource, the spatial applicability 
   * of the resource, or the jurisdiction under which the resource is relevant.
   */
  public static final String DC_KEY_COVERAGE = "dc.coverage";
  public static final MetadataField DC_FIELD_COVERAGE =
    new MetadataField(DC_KEY_COVERAGE, Cardinality.Single);

  /** An entity primarily responsible for making the resource. */
  public static final String DC_KEY_CREATOR = "dc.creator";
  public static final MetadataField DC_FIELD_CREATOR =
    new MetadataField(DC_KEY_CREATOR, Cardinality.Multi);

  /** 
   * A point or period of time associated with an event in the lifecycle 
   * of the resource. Recommended best practice is to use an encoding scheme, 
   * such as the W3CDTF profile of ISO 8601 [W3CDTF].
   */
  public static final String DC_KEY_DATE = "dc.date";
  public static final MetadataField DC_FIELD_DATE =
    new MetadataField(DC_KEY_DATE, Cardinality.Single);

  /** 
   * An account of the resource. May include but is not limited to: an 
   * abstract, a table of contents, a graphical representation, or a 
   * free-text account of the resource.
   */
  public static final String DC_KEY_DESCRIPTION = "dc.description";
  public static final MetadataField DC_FIELD_DESCRIPTION =
    new MetadataField(DC_KEY_DESCRIPTION, Cardinality.Single);

  /**
   * The file format, physical medium, or dimensions of the resource.
   * Recommended best practice is to use a controlled vocabulary such 
   * as the list of Internet Media Types [MIME].
   */
  public static final String DC_KEY_FORMAT = "dc.format";
  public static final MetadataField DC_FIELD_FORMAT =
    new MetadataField(DC_KEY_FORMAT, Cardinality.Single);

  /**
   * An unambiguous reference to the resource within a given context.
   * Recommended best practice is to identify the resource by means 
   * of a string conforming to a formal identification system.
   * <p>
   * According to Google Scholar, "If a page shows only the abstract 
   * of the paper and you have the full text in a separate file, e.g., 
   * in the PDF format, please specify the locations of all full text 
   * versions using ... DC.identifier tags. The content of the tag is 
   * the absolute URL of the PDF file; for security reasons, it must 
   * refer to a file in the same subdirectory as the HTML abstract."
   */
  public static final String DC_KEY_IDENTIFIER = "dc.identifier";
  public static final MetadataField DC_FIELD_IDENTIFIER =
    new MetadataField(DC_KEY_IDENTIFIER, Cardinality.Multi);

  /** The ISSN of the resource (dc qualified: non-standard NIH) */
  public static final String DC_KEY_IDENTIFIER_ISSN = "dc.identifier.issn";
  public static final MetadataField DC_FIELD_IDENTIFIER_ISSN =
    new MetadataField(DC_KEY_IDENTIFIER_ISSN, Cardinality.Single);

  /** The EISSN of the resource (dc qualified: non-standard NIH) */
  public static final String DC_KEY_IDENTIFIER_EISSN = "dc.identifier.eissn";
  public static final MetadataField DC_FIELD_IDENTIFIER_EISSN =
    new MetadataField(DC_KEY_IDENTIFIER_EISSN, Cardinality.Single);

  /** The ISSNL of the resource (dc qualified: non-standard NIH) */
  public static final String DC_KEY_IDENTIFIER_ISSNL = "dc.identifier.issnl";
  public static final MetadataField DC_FIELD_IDENTIFIER_ISSNL =
    new MetadataField(DC_KEY_IDENTIFIER_ISSNL, Cardinality.Single);

  /** The ISBN of the resource (dc qualified: non-standard NIH) */
  public static final String DC_KEY_IDENTIFIER_ISBN = "dc.identifier.isbn";
  public static final MetadataField DC_FIELD_IDENTIFIER_ISBN =
    new MetadataField(DC_KEY_IDENTIFIER_ISBN, Cardinality.Single);

  /**
   * Date of publication, i.e., the date that would normally be cited 
   * in references to this paper from other papers. Don't use it for the
   * date of entry into the repository. Provide full dates in the 
   * "2010/5/12" format if available; or a year alone otherwise.
   */
  public static final String DC_KEY_ISSUED = "dc.issued";
  public static final MetadataField DC_FIELD_ISSUED =
    new MetadataField(DC_KEY_ISSUED, Cardinality.Single);

  /**
   * A language of the resource. Recommended best practice is to 
   * use a controlled vocabulary such as RFC 4646 [RFC4646].
   */
  public static final String DC_KEY_LANGUAGE = "dc.language";
  public static final MetadataField DC_FIELD_LANGUAGE =
    new MetadataField(DC_KEY_LANGUAGE, Cardinality.Single);

  /** An entity responsible for making the resource available. */
  public static final String DC_KEY_PUBLISHER = "dc.publisher";
  public static final MetadataField DC_FIELD_PUBLISHER =
    new MetadataField(DC_KEY_PUBLISHER, Cardinality.Single);

  /**
   * A related resource. Recommended best practice is to 
   * identify the related resource by means of a string 
   * conforming to a formal identification system.
   */
  public static final String DC_KEY_RELATION = "dc.relation";
  public static final MetadataField DC_FIELD_RELATION =
    new MetadataField(DC_KEY_RELATION, Cardinality.Multi);

  /**
   * The resource of which this resource is a part. For an article
   * in a journal or proceedings, identifies the publication
   * (dc qualified: by Google Scholar).
   */
  public static final String DC_KEY_RELATION_ISPARTOF = "dc.relation.ispartof";
  public static final MetadataField DC_FIELD_RELATION_ISPARTOF =
    new MetadataField(DC_KEY_RELATION_ISPARTOF, Cardinality.Single);

  /**
   * Information about rights held in and over the resource.
   * Typically, rights information includes a statement about 
   * various property rights associated with the resource, 
   * including intellectual property rights.
   */
  public static final String DC_KEY_RIGHTS = "dc.rights";
  public static final MetadataField DC_FIELD_RIGHTS =
    new MetadataField(DC_KEY_RIGHTS, Cardinality.Single);

  /**
   * A related resource from which the described resource is 
   * derived. Typically, the subject will be represented using 
   * keywords, key phrases, or classification codes.
   */
  public static final String DC_KEY_SOURCE = "dc.source";
  public static final MetadataField DC_FIELD_SOURCE =
    new MetadataField(DC_KEY_SOURCE, Cardinality.Single);

  /**
   * The topic of the resource. Typically, the subject will be 
   * represented using keywords, key phrases, or classification codes.
   */
  public static final String DC_KEY_SUBJECT = "dc.subject";
  public static final MetadataField DC_FIELD_SUBJECT =
    new MetadataField(DC_KEY_SUBJECT, Cardinality.Single);

  /**
   * A name given to the resource. Typically, a Title will be 
   * a name by which the resource is formally known.
   */
  public static final String DC_KEY_TITLE = "dc.title";
  public static final MetadataField DC_FIELD_TITLE =
    new MetadataField(DC_KEY_TITLE, Cardinality.Single);

  /**
   * The nature or genre of the resource. Recommended best practice 
   * is to use a controlled vocabulary such as the DCMI Type 
   * Vocabulary [DCMITYPE]. To describe the file format, physical medium, 
   * or dimensions of the resource, use the Format element.
   */
  public static final String DC_KEY_TYPE = "dc.type";
  public static final MetadataField DC_FIELD_TYPE =
    new MetadataField(DC_KEY_TYPE, Cardinality.Single);

  private static MetadataField[] fields = {
    FIELD_VOLUME,
    FIELD_ISSUE,
    FIELD_START_PAGE,
    FIELD_DATE,
    FIELD_ARTICLE_TITLE,
    FIELD_JOURNAL_TITLE,
    FIELD_AUTHOR,
    FIELD_ACCESS_URL,
    FIELD_KEYWORDS,
    DC_FIELD_IDENTIFIER.
    DC_FIELD_DATE,
    DC_FIELD_CONTRIBUTOR,
  };

  // maps keys to fields
  private static Map<String,MetadataField> fieldMap =
    new HashMap<String,MetadataField>();
  static {
    for (MetadataField f : fields) {
      fieldMap.put(f.getKey().toLowerCase(), f);
    }
  }

  /** Return the predefined MetadataField with the given key, or null if
   * none */
  public static MetadataField findField(String key) {
    return fieldMap.get(key.toLowerCase());
  }


  protected final String key;
  protected final Cardinality cardinality;
  protected final Validator validator;
  protected final Splitter splitter;

  /** Create a metadata field descriptor with Cardinality.Single
   * @param key the map key
   */
  public MetadataField(String key) {
    this(key, Cardinality.Single, null, null);
  }

  /** Create a metadata field descriptor
   * @param key the map key
   * @param cardinality
   */
  public MetadataField(String key, Cardinality cardinality) {
    this(key, cardinality, null, null);
  }

  /** Create a metadata field descriptor
   * @param key the map key
   * @param cardinality
   * @param validator
   */
  public MetadataField(String key, Cardinality cardinality,
		       Validator validator) {
    this(key, cardinality, validator, null);
  }

  /** Create a metadata field descriptor
   * @param key the map key
   * @param cardinality
   * @param splitter
   */
  public MetadataField(String key, Cardinality cardinality,
		       Splitter splitter) {
    this(key, cardinality, null, splitter);
  }

  /** Create a metadata field descriptor
   * @param key the map key
   * @param cardinality
   * @param validator
   */
  public MetadataField(String key, Cardinality cardinality,
		       Validator validator, Splitter splitter) {
    this.key = key;
    this.cardinality = cardinality;
    this.validator = validator;
    if (cardinality != Cardinality.Multi && splitter != null) {
      throw new IllegalArgumentException("Splitter legal only with Cardinality.Multi");
    }
    this.splitter = splitter;
  }

  /** Create a MetadataField that's a copy of another one
   * @param field the MetadataField to copy
   * @param splitter
   */
  public MetadataField(MetadataField field) {
    this(field.getKey(), field.getCardinality(), field.getValidator());
  }

  /** Create a MetadataField that's a copy of another one
   * @param field the MetadataField to copy
   */
  public MetadataField(MetadataField field, Splitter splitter) {
    this(field.getKey(), field.getCardinality(),
	 field.getValidator(), splitter);
  }

  /** Return the field's key. */
  public String getKey() {
    return key;
  }

  /** Return the field's cardinality. */
  public Cardinality getCardinality() {
    return cardinality;
  }

  private Validator getValidator() {
    return validator;
  }

  /** If a validator is present, apply it to the argument.  If valid,
   * return the argument or a normalized value.  If invalid, throw
   * MetadataException.ValidationException */
  public String validate(ArticleMetadata am, String value)
      throws MetadataException.ValidationException {
    if (validator != null) {
      return validator.validate(am, this, value);
    }
    return value;
  }

  /** If a splitter is present, apply it to the argument return a list of
   * strings.  If no splitter is present, return a singleton list of the
   * argument */
  public List<String> split(ArticleMetadata am, String value) {
    if (splitter != null) {
      return splitter.split(am, this, value);
    }
    return ListUtil.list(value);
  }

  public boolean hasSplitter() {
    return splitter != null;
  }


  /** Cardinality of a MetadataField: single-valued or multi-valued */
  public static enum Cardinality {Single, Multi};

  static class Default extends MetadataField {
    public Default(String key) {
      super(key, Cardinality.Single);
    }
  }

  /** Validator can be associated with a MetadataField to check and/or
   * normalize values when stored. */
  public interface Validator {
    /** Validate and/or normalize value.
     * @param am the ArticleMeta being stored into (source of Locale, if
     * necessary)
     * @param field the field being stored
     * @param value the value being stored
     * @return original value or a normalized value to store
     * @throws MetadataField.ValidationException if the value is illegal
     * for the field
     */
    public String validate(ArticleMetadata am,
			   MetadataField field,
			   String value)
	throws MetadataException.ValidationException;
  }

  /** Splitter can be associated with a MetadataField to split value
   * strings into substring to be stored into a multi-valued field. */
  public interface Splitter {
    /** Split a value into a list of values
     * @param am the ArticleMeta being stored into (source of Locale, if
     * necessary)
     * @param field the field being stored
     * @param value the value being stored
     * @return list of values
     */
    public List<String> split(ArticleMetadata am,
			      MetadataField field,
			      String value);
  }

  /** Return a Splitter that splits substrings separated by the separator
   * string.
   * @param separator the separator string
   */
  public static Splitter splitAt(String separator) {
    return new SplitAt(separator, null, null);
  }

  /** Return a Splitter that first removes the delimiter string from the
   * ends of the input, then splits substrings separated by the separator
   * string. 
   * @param separator the separator string
   * @param delimiter the delimiter string removed from both ends of the input
   */
  public static Splitter splitAt(String separator, String delimiter) {
    return new SplitAt(separator, delimiter, delimiter);
  }

  /** Return a Splitter that first removes the two delimiter strings from
   * the front and end of the input, respectively, then splits substrings
   * separated by the separator string.
   * @param separator the separator string
   * @param delimiter1 the delimiter string removed from the beginning of
   * the input
   * @param delimiter2 the delimiter string removed from the end of the
   * input
   */
  public static Splitter splitAt(String separator,
				 String delimiter1,
				 String delimiter2) {
    return new SplitAt(separator, delimiter1, delimiter2);
  }

  /** A Splitter that splits substrings separated by a separator string,
   * optionally after removing delimiters from the beginning and end of the
   * string.  Blanks are trimmed from the ends of the input string and from
   * each substring, and empty substrings are discarded. */
  public static class SplitAt implements Splitter {
    protected String splitSep;
    protected String splitDelim1;
    protected String splitDelim2;

    public SplitAt(String separator, String delimiter1, String delimiter2) {
      splitSep = separator;
      splitDelim1 = delimiter1;
      splitDelim2 = delimiter2;
    }

    public List<String> split(ArticleMetadata am,
			      MetadataField field,
			      String value) {
      value = value.trim();
      if (splitDelim1 != null) {
	if (splitDelim2 == null) {
	  splitDelim2 = splitDelim1;
	}
	value = StringUtils.removeStartIgnoreCase(value, splitDelim1);
	value = StringUtils.removeEndIgnoreCase(value, splitDelim2);
      }
      return StringUtil.breakAt(value, splitSep, -1, true, true);
    }
  }
}
