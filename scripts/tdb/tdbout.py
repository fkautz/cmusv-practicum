#!/usr/bin/python

# $Id$

# Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

__version__ = '''0.3.1'''

from optparse import OptionGroup, OptionParser
import sys
import tdbparse
import tdbq

class TdboutConstants:
    '''Constants needed by this module.'''

    OPTION_FIELDS = 'fields'
    OPTION_FIELDS_SHORT = 'f'
    OPTION_FIELDS_HELP = 'comma-separated list of fields to select'

    OPTION_STYLE = 'style'
    OPTION_STYLE_SHORT = 's'
    OPTION_STYLE_CSV = 'csv'
    OPTION_STYLE_LIST = 'list'
    OPTION_STYLE_TSV = 'tsv'
    OPTION_STYLES = [OPTION_STYLE_CSV, OPTION_STYLE_LIST, OPTION_STYLE_TSV]
    OPTION_STYLE_HELP = 'output style (%s)' % (', '.join(OPTION_STYLES),)

    OPTION_AUID = 'auid'
    OPTION_AUID_SHORT = 'a'
    OPTION_AUID_HELP = 'equivalent of \'-%s list -%s auid\'' % (OPTION_STYLE_SHORT, OPTION_FIELDS_SHORT)

    OPTION_CSV = 'csv'
    OPTION_CSV_SHORT = 'c'
    OPTION_CSV_META = OPTION_FIELDS.upper()
    OPTION_CSV_HELP = 'equivalent of \'-%s csv -%s %s\'' % (OPTION_STYLE_SHORT, OPTION_FIELDS_SHORT, OPTION_CSV_META)

    OPTION_LIST = 'list'
    OPTION_LIST_SHORT = 'l'
    OPTION_LIST_META = 'FIELD'
    OPTION_LIST_HELP = 'equivalent of \'-%s list -%s %s\'' % (OPTION_STYLE_SHORT, OPTION_FIELDS_SHORT, OPTION_LIST_META) 

    OPTION_TSV = 'tsv'
    OPTION_TSV_SHORT = 't'
    OPTION_TSV_META = OPTION_FIELDS.upper()
    OPTION_TSV_HELP = 'equivalent of \'-%s tsv -%s %s\'' % (OPTION_STYLE_SHORT, OPTION_FIELDS_SHORT, OPTION_TSV_META)

    OPTION_NAMES = 'names'
    OPTION_NAMES_SHORT = 'n'
    OPTION_NAMES_HELP = 'include column names'

    OPTION_WARNINGS = 'warnings'
    OPTION_WARNINGS_SHORT = 'w'
    OPTION_WARNINGS_HELP = 'include warnings with the CSV or TSV output'

def __process(tdb, options):
    fields = options.fields.split(',')
    result = [[lam(au) for lam in map(tdbq.str_to_lambda_au, fields)] for au in tdb.aus()]
    if options.style == TdboutConstants.OPTION_STYLE_CSV:
        import csv
        if options.headings: result = [[w.capitalize() for w in fields]] + result
        if options.warnings:
            from datetime import date
            warnings = [['Current as of %s' % (date.today())],
                        ['Subject to change without notice']]
            result = warnings + [[]] + result + [[]] + warnings
        writer = csv.writer(sys.stdout, dialect='excel')
        for lst in result: writer.writerow(lst)
    else:
        for lst in result: print '\t'.join(lst) 

def __option_parser__(parser=None):
    if parser is None: parser = OptionParser(version=__version__)
    parser = tdbparse.__option_parser__(parser)
    tdbout_group = OptionGroup(parser, 'tdbout module (%s)' % (__version__,))
    tdbout_group.add_option('-' + TdboutConstants.OPTION_STYLE_SHORT,
                            '--' + TdboutConstants.OPTION_STYLE,
                            choices=TdboutConstants.OPTION_STYLES,
                            help=TdboutConstants.OPTION_STYLE_HELP)
    tdbout_group.add_option('-' + TdboutConstants.OPTION_FIELDS_SHORT,
                            '--' + TdboutConstants.OPTION_FIELDS,
                            help=TdboutConstants.OPTION_FIELDS_HELP)
    def __synonym_auid(opt, str, val, par):
        if getattr(par.values, TdboutConstants.OPTION_STYLE, None): par.error('cannot specify -%s and -%s together' % (TdboutConstants.OPTION_AUID_SHORT, TdboutConstants.OPTION_STYLE_SHORT))
        setattr(par.values, TdboutConstants.OPTION_STYLE, TdboutConstants.OPTION_STYLE_LIST)
        setattr(par.values, TdboutConstants.OPTION_FIELDS, 'auid')
    tdbout_group.add_option('-' + TdboutConstants.OPTION_AUID_SHORT,
                            '--' + TdboutConstants.OPTION_AUID,
                            type=None,
                            action='callback',
                            help=TdboutConstants.OPTION_AUID_HELP,
                            callback=__synonym_auid) 
    def __synonym_csv(opt, str, val, par):
        if getattr(par.values, TdboutConstants.OPTION_STYLE, None): par.error('cannot specify -%s and -%s together' % (TdboutConstants.OPTION_CSV_SHORT, TdboutConstants.OPTION_STYLE_SHORT))
        setattr(par.values, TdboutConstants.OPTION_STYLE, TdboutConstants.OPTION_STYLE_CSV)
        setattr(par.values, TdboutConstants.OPTION_FIELDS, val)
    tdbout_group.add_option('-' + TdboutConstants.OPTION_CSV_SHORT,
                            '--' + TdboutConstants.OPTION_CSV,
                            type='string',
                            metavar=TdboutConstants.OPTION_CSV_META,
                            action='callback',
                            help=TdboutConstants.OPTION_CSV_HELP,
                            callback=__synonym_csv) 
    def __synonym_list(opt, str, val, par):
        if getattr(par.values, TdboutConstants.OPTION_STYLE, None): par.error('cannot specify -%s and -%s together' % (TdboutConstants.OPTION_LIST_SHORT, TdboutConstants.OPTION_STYLE_SHORT))
        setattr(par.values, TdboutConstants.OPTION_STYLE, TdboutConstants.OPTION_STYLE_LIST)
        setattr(par.values, TdboutConstants.OPTION_FIELDS, val)
    tdbout_group.add_option('-' + TdboutConstants.OPTION_LIST_SHORT,
                            '--' + TdboutConstants.OPTION_LIST,
                            type='string',
                            metavar=TdboutConstants.OPTION_LIST_META,
                            action='callback',
                            help=TdboutConstants.OPTION_LIST_HELP,
                            callback=__synonym_csv) 
    def __synonym_tsv(opt, str, val, par):
        if getattr(par.values, TdboutConstants.OPTION_STYLE, None): par.error('cannot specify -%s and -%s together' % (TdboutConstants.OPTION_TSV_SHORT, TdboutConstants.OPTION_STYLE_SHORT))
        setattr(par.values, TdboutConstants.OPTION_STYLE, TdboutConstants.OPTION_STYLE_TSV)
        setattr(par.values, TdboutConstants.OPTION_FIELDS, val)
    tdbout_group.add_option('-' + TdboutConstants.OPTION_TSV_SHORT,
                            '--' + TdboutConstants.OPTION_TSV,
                            type='string',
                            metavar=TdboutConstants.OPTION_TSV_META,
                            action='callback',
                            help=TdboutConstants.OPTION_TSV_HELP,
                            callback=__synonym_tsv) 
    tdbout_group.add_option('-' + TdboutConstants.OPTION_NAMES_SHORT,
                            '--' + TdboutConstants.OPTION_NAMES,
                            action='store_true',
                            help=TdboutConstants.OPTION_NAMES_HELP)
    tdbout_group.add_option('-' + TdboutConstants.OPTION_WARNINGS_SHORT,
                            '--' + TdboutConstants.OPTION_WARNINGS,
                            action='store_true',
                            help=TdboutConstants.OPTION_WARNINGS_HELP)
    parser.add_option_group(tdbout_group)
    return parser

def __reprocess_options__(parser, options):
    tdbparse.__reprocess_options__(parser, options)
    if not options.fields: parser.error('no field(s) specified')
    for fd in options.fields.split(','):
        if tdbq.str_to_lambda_au(fd) is None: parser.error('invalid field name: %s' % (fd,))
    if options.warnings and options.style not in [TdboutConstants.OPTION_STYLE_CSV]: parser.error('-%s requires -%s %s' % (TdboutConstants.OPTION_WARNINGS_SHORT, TdboutConstants.OPTION_STYLE_SHORT, TdboutConstants.OPTION_STYLE_CSV))

if __name__ == '__main__':
    parser = __option_parser__()
    (options, args) = parser.parse_args(values=parser.get_default_values())
    __reprocess_options__(parser, options)
    tdb = tdbparse.tdbparse(sys.stdin, options) 
    __process(tdb, options)
