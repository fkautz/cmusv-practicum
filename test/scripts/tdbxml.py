#!/usr/bin/python

# $Id$
#
# Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
# all rights reserved.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
# STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
# WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
# IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#
# Except as contained in this notice, the name of Stanford University shall not
# be used in advertising or otherwise to promote the sale, use or other dealings
# in this Software without prior written authorization from Stanford University.

import re
from tdb import *
from tdbconst import *

_IMPLICIT_PARAM_ORDER = [
    'base_url', 'base_url2', 'base_url3', 'base_url4', 'base_url5',
    'oai_request_url',
    'publisher_id', 'publisher_code', 'publisher_name',
    'journal_id', 'journal_code', 'journal_issn', 'journal_dir',
    'year',
    'issues', 'issue_set', 'issue_range', 'num_issue_range',
    'volume_name', 'volume'
]

def _escape(str):
    from xml.sax import saxutils
    return saxutils.escape(str)

def _short_au_name(au):
    str = au.name()
    str = re.sub(r'Volume\s+(\S+)$', r'\1', str)
    str = re.sub(r'\s+', '', str)
    str = re.sub(r'\W+', '', str)
    return _escape(str)

def _preamble(tdb, options):
    if options.style == TDB_STYLE_XML_ENTRIES: return
    print '''\
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE lockss-config [
<!ELEMENT lockss-config (if|property)+>
<!ELEMENT property (property|list|value|if)*>
<!ELEMENT list (value)+>
<!ELEMENT value (#PCDATA)>
<!ELEMENT test EMPTY>
<!ELEMENT and (and|or|not|test)*>
<!ELEMENT or (and|or|not|test)*>
<!ELEMENT not (and|or|not|test)*>
<!ELEMENT if (and|or|not|then|else|test|property)*>
<!ELEMENT then (if|property)*>
<!ELEMENT else (if|property)*>
<!ATTLIST property name CDATA #REQUIRED>
<!ATTLIST property value CDATA #IMPLIED>
<!ATTLIST test hostname CDATA #IMPLIED>
<!ATTLIST test group CDATA #IMPLIED>
<!ATTLIST test daemonVersionMin CDATA #IMPLIED>
<!ATTLIST test daemonVersionMax CDATA #IMPLIED>
<!ATTLIST test daemonVersion CDATA #IMPLIED>
<!ATTLIST test platformVersionMin CDATA #IMPLIED>
<!ATTLIST test platformVersionMax CDATA #IMPLIED>
<!ATTLIST test platformVersion CDATA #IMPLIED>
<!ATTLIST test platformName CDATA #IMPLIED>
<!ATTLIST if hostname CDATA #IMPLIED>
<!ATTLIST if group CDATA #IMPLIED>
<!ATTLIST if daemonVersionMin CDATA #IMPLIED>
<!ATTLIST if daemonVersionMax CDATA #IMPLIED>
<!ATTLIST if daemonVersion CDATA #IMPLIED>
<!ATTLIST if platformVersionMin CDATA #IMPLIED>
<!ATTLIST if platformVersionMax CDATA #IMPLIED>
<!ATTLIST if platformVersion CDATA #IMPLIED>
<!ATTLIST if platformName CDATA #IMPLIED>
<!ATTLIST list append CDATA #IMPLIED>
]>
'''

def _introduction(tdb, options):
    if options.style == TDB_STYLE_XML_ENTRIES: return
    print '''\
<lockss-config>
'''

def _do_param(au, i, param, value=None):
    if value is None:
        value = au.param(param)
    print '''\
   <property name="param.%d">
    <property name="key" value="%s" />
    <property name="value" value="%s" />
   </property>''' % ( i, param, value )

def _do_attr(au, attr, value=None):
    if value is None:
        value = au.attr(attr)
    print '''\
   <property name="attribute.%s" value="%s" />''' % ( attr, value )

def _process_au(au, options):
    if au.status() not in options.level: return
    print '''\
  <property name="%s">
   <property name="attributes.publisher" value="%s" />
   <property name="journalTitle" value="%s" />''' % (
        _short_au_name(au),
        _escape(au.title().publisher().name()),
        _escape(au.title().name()) )
    if au.issn() is not None:
        print '''\
   <property name="issn" value="%s" />''' % ( au.issn(), )
    print '''\
   <property name="title" value="%s" />
   <property name="plugin" value="%s" />''' % (
        _escape(au.name()),
        au.plugin() )
    i = 1
    for param in _IMPLICIT_PARAM_ORDER:
        if param in au.params():
            _do_param(au, i, param)
            i = i + 1
    for param in au.params():
        if param not in _IMPLICIT_PARAM_ORDER:
            _do_param(au, i, param)
            i = i + 1
    if au.status() == TDB_STATUS_DOWN:
        _do_param(au, 99, 'pub_down', value='true')
    for attr in au.attrs():
        _do_attr(au, attr)
    if au.status() == TDB_STATUS_PRE_RELEASED:
        _do_attr(au, 'releaseStatus', 'pre-release')
    if au.rights() == 'openaccess':
        _do_attr(au, 'rights', 'openaccess')
    print '''\
  </property>
'''

def _process(tdb, options):
    current_pub = None
    if options.style == TDB_STYLE_XML_LEGACY:
        print '''\
 <property name="org.lockss.title">
'''
    for au in tdb.aus():
        if options.style == TDB_STYLE_XML and current_pub is not au.title().publisher():
            if current_pub is not None:
                print '''\
 </property>
'''
            current_pub = au.title().publisher()
            print '''\
 <property name="org.lockss.titleSet">

  <property name="%(publisher)s">
   <property name="name" value="All %(publisher)s Titles" />
   <property name="class" value="xpath" />
   <property name="xpath" value="[attributes/publisher='%(publisher)s']" />
  </property>
  
 </property>
 
 <property name="org.lockss.title">
''' % { 'publisher': _escape(current_pub.name()) }
        _process_au(au, options)
    else:
        if options.style == TDB_STYLE_XML: print '''\
 </property>
'''             
    if options.style == TDB_STYLE_XML_LEGACY:
        print '''\
 </property>
'''

def _conclusion(tdb, options):
    if options.style == TDB_STYLE_XML_ENTRIES: return
    print '</lockss-config>'

def _postamble(tdb, options):
    pass

def tdb_to_xml(tdb, options):
    _preamble(tdb, options)
    _introduction(tdb, options)
    _process(tdb, options)
    _conclusion(tdb, options)
    _postamble(tdb, options)
