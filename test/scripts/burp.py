#!/usr/bin/python

'''
Created on Dec 4, 2009

@author: edwardsb, thibgc

This script is part of the toolset to generate the monthly DOI report.
(The other two parts are "BurpReport.py", and "run-burp.py".)  It queries
one other machine (given as the -H parameter), and inserts its results into
a database.

It assumes that there is a MySQL database program running, with appropriate tables.
'''
import datetime
import httplib
import MySQLdb
import optparse
import os
import sys
import time
import urllib2

sys.path.append(os.path.realpath(os.path.join(os.path.dirname(sys.argv[0]), '../frameworks/lib')))
import lockss_daemon


BURP_VERSION = '0.2.2'

OPTION_LONG  = '--'
OPTION_SHORT = '-'

OPTION_HOST                     = 'host'
OPTION_HOST_SHORT               = 'H'
OPTION_PASSWORD                 = 'password'
OPTION_PASSWORD_SHORT           = 'P'
DEFAULT_PASSWORD                = 'lockss-p'
OPTION_USERNAME                 = 'username'
OPTION_USERNAME_SHORT           = 'U'
DEFAULT_USERNAME                = 'lockss-u'

OPTION_DATABASE_PASSWORD        = 'dbpassword'
OPTION_DATABASE_PASSWORD_SHORT  = 'D'

OPTION_LOG_FILE                 = 'logfile'
OPTION_LOG_FILE_SHORT           = 'L'

PARAM_READY_TIMEOUT             = 30
PARAM_REPEAT_LIST_ARTICLES      = 5  
PARAM_REPEAT_GET_STATUS_TABLE   = 5

def _make_command_line_parser():
    from optparse import OptionGroup, OptionParser
    parser = OptionParser(version=BURP_VERSION)

    parser.add_option(OPTION_SHORT + OPTION_HOST_SHORT,
                      OPTION_LONG + OPTION_HOST,
                      dest=OPTION_HOST,
                      help='daemon hostname:port (required)')
    parser.add_option(OPTION_SHORT + OPTION_PASSWORD_SHORT,
                      OPTION_LONG + OPTION_PASSWORD,
                      dest=OPTION_PASSWORD,
                      default=DEFAULT_PASSWORD,
                      help='daemon UI password (default: %default)')
    parser.add_option(OPTION_SHORT + OPTION_USERNAME_SHORT,
                      OPTION_LONG + OPTION_USERNAME,
                      dest=OPTION_USERNAME,
                      default=DEFAULT_USERNAME,
                      help='daemon UI username (default: %default)')
    parser.add_option(OPTION_SHORT + OPTION_DATABASE_PASSWORD_SHORT,
                      OPTION_LONG + OPTION_DATABASE_PASSWORD,
                      dest=OPTION_DATABASE_PASSWORD,
                      help="The password for the database (required)")
    parser.add_option(OPTION_SHORT + OPTION_LOG_FILE_SHORT,
                      OPTION_LONG + OPTION_LOG_FILE,
                      dest=OPTION_LOG_FILE,
                      default=None,
                      help="Where to store extra log information (not required nor usually desirable)")

    
    return parser
    
def _check_required_options(parser, options):
    if options.host is None:
        parser.error('%s%s/%s%s is required' % (OPTION_LONG, OPTION_HOST, OPTION_SHORT, OPTION_HOST_SHORT))
    if options.dbpassword is None:
        parser.error('%s%s/%s%s is required' % (OPTION_LONG, OPTION_DATABASE_PASSWORD, OPTION_SHORT, OPTION_DATABASE_PASSWORD_SHORT))

def _make_client(options):
    client =  lockss_daemon.Client(options.host.split(':',1)[0],
                                   options.host.split(':',1)[1],
                                   options.username,
                                   options.password)
    if not client.waitForDaemonReady(PARAM_READY_TIMEOUT):
        raise RuntimeError, '%s is not ready after %d seconds' % (options.host, PARAM_READY_TIMEOUT)
    return client
    
def _get_auids(client):
    table = client._getStatusTable('AuIds')[1]
    auids = []
    auname = {}
    for map in table:
        id = map['AuId']
        auids.append(id)
        auname[id] = map['AuName']['value']
    return auids, auname
    
def _parse_list_of_articles(lst):
    val = []
    if lst is not None and len(lst) > 0: 
       val = lst.splitlines()
       i = 0
       # Eliminate empty lines and lines that start with "#".
       while i < len(val):
           if val[i].startswith("#") or len(val[i]) == 0 or val[i].isspace():
                del val[i]
           else:
                i = i + 1
    return val

# Verify that the _parse_list_of_articles is likely to work.    
def _test_parse_list_of_articles():
    # No comments, no blank lines.
    arts = "Title1\nTitle 2\n"
    lstArts = _parse_list_of_articles(arts)
    if len(lstArts) != 2:
        raise RuntimeError, "_parse_list_of_articles did not pass the no comments, no blank lines test."
    
    # Test with comments.    
    arts = "# Comment\nTitle 1\nTitle 2\n# Comment"
    lstArts = _parse_list_of_articles(arts)
    if len(lstArts) != 2:
        raise RuntimeError, "_parse_list_of_articles did not pass the comments test."
        
    # Test with blank lines.
    arts = "\n\nTitle 1\n\n\n\n\nTitle 2\n\n"
    lstArts = _parse_list_of_articles(arts)
    if len(lstArts) != 2:
        raise RuntimeError, "_parse_list_of_articles did not pass the blank lines test."
        
    
def _get_list_articles(client, auid, auarticles, options):
    reps = 0

    while (reps < PARAM_REPEAT_LIST_ARTICLES):
        try:
            lst = client.getListOfArticles(lockss_daemon.AU(auid))
            auarticles[auid] = _parse_list_of_articles(lst)
            break
        except urllib2.URLError:
            reps = reps + 1
            print "_get_list_articles has URLError.  This is repeat %d." % (reps,)
    else:
        raise RuntimeError, '%s did not give the list of articles after %d tries' % (options.host, PARAM_REPEAT_LIST_ARTICLES)
    
    if options.logfile is not None:
        f = open(options.logfile, 'a')
        
        f.write(auid + ":\n")
        for art in auarticles[auid]:
            f.write(art + "\n")
            
        f.close()

def _get_list_urls(client, auid, auarticles):
    lst = client.getListOfUrls(lockss_daemon.AU(auid))
    val = []
    if lst is not None and len(lst) > 0:
        for art in lst.splitlines()[2:]:
            if art.startswith('http://www.rsc.org/publishing/journals/') and art.find('/article.asp?doi=') >= 0:
                val.append(art)
    auarticles[auid] = val

# This method counts the number of articles minus the number of articles
# that have been superseded. 

# The table was created with 
# 'create table overrideauid (overridden varchar(2047), 
# overrider varchar(2047));'

# At this time, the 'overrider' column is not used.

def _count_articles(db, listAuids):
    cursorDuplicated = db.cursor()
    cursorDuplicated.execute("CREATE TEMPORARY TABLE listAuids (auid varchar(2047));")
    # There may be a better way to insert large amounts of data into
    # a MySQL database.
    for auid in listAuids:
        cursorDuplicated.execute("INSERT INTO listAuids VALUE (\"{0}\");".format(auid))

    # Count the number of duplicated articles.
    cursorDuplicated.execute("SELECT COUNT(listAuids.auid) FROM listAuids, overrideauid WHERE listAuids.auid = overrideauid.overridden;")
    fetchDuplicated = cursorDuplicated.fetchone()
    numDuplicated = fetchDuplicated[0]

    cursorDuplicated.execute("DROP TEMPORARY TABLE listAuids;")

    return len(listAuids) - numDuplicated


def _need_report(db, options):
    hostname = options.host.split(':',1)[0]
    port = options.host.split(':',1)[1]
    
    cursorQuery = db.cursor()
    cursorQuery.execute("SELECT MAX(completedate) FROM lastcomplete WHERE machinename = \"" + str(hostname) + "\" AND port = " + port + ";")
    lastComplete = cursorQuery.fetchone()
    
    if lastComplete[0] is None:
        return True
    
    # lastComplete[0] seems to be a datetime.
    twodaysago = datetime.datetime.now() - datetime.timedelta(days=2)
    
    return lastComplete[0] < twodaysago

    
def _article_report(client, db, options):
    auids, auname = _get_auids(client)
    host, port = options.host.split(':',1)
    auyear = {}
    austatus = {}
    aucreated = {}
    aulastcrawlresult = {}
    aucontentsize = {}
    audisksize = {}
    aurepository = {}
    auarticles = {}
    cursor = db.cursor()
    
    cursorStarted = db.cursor()
    cursorStarted.execute("SELECT MAX(start) FROM executions");
    arStarted = cursorStarted.fetchone()
    
    if arStarted is None:
        print "You must insert the start time before you do an article report.\n"
        raise RuntimeError("You must insert the start time before you do an article report.")
    
    startExecution = arStarted[0]
    
    for auid in auids:
        # Because it's hard to know if the Burp is running without SOME feedback...
        print options.host + ":" + auname[auid]
        
        # Skip the AUID if it has been seen in this (overall) execution.
        cursor.execute("SELECT MAX(rundate) FROM burp WHERE machinename = '%s' AND port = %s AND auid = '%s'" %
                       (host, port, auid)) 
        arRunDate = cursor.fetchone()
#        if (arRunDate is None) or (arRunDate[0] is None):
#            arRunDate = [datetime.datetime(1900, 1, 1)]
#        if (arRunDate[0] > startExecution):
#            print("Skipping: This AU was last recorded on %s." %
#                (arRunDate[0].strftime("%Y-%m-%d %H:%M:%S"),))
#            print("The execution started on %s." %
#                (startExecution.strftime("%Y-%m-%d %H:%M:%S"),))
#            continue
         
        rerun = True
        numRuns = 0
        while rerun:
            try:
                summary, table = client._getStatusTable('ArchivalUnitTable', auid)
                rerun = False
            except urllib2.URLError:
                numRuns += 1
                print "%s : _article_report has a URL Error.  This is try %d." % (options.host, numRuns)
                if numRuns > PARAM_REPEAT_GET_STATUS_TABLE:
                    print "Giving up."
                    raise
                
        auyear[auid] = summary.get('Year', 0)
        austatus[auid] = summary.get('Status')
        aulastcrawlresult[auid] = summary.get('Last Crawl Result', 'n/a')
        aucontentsize[auid] = summary.get('Content Size')
        if aucontentsize[auid] <> None:
            aucontentsize[auid].replace(",", "")
        else:
            aucontentsize[auid] = ""
        audisksize[auid] = summary.get('Disk Usage (MB)', 'n/a')
        aurepository[auid] = summary.get('Repository')
        
        created = summary.get('Created')
        if created is not None:
            try:
                aucreated[auid] = time.strptime(created, "%H:%M:%S %m/%d/%y")
            except ValueError:
                print "FAIL: 'Created' date was '%s', which is not the right format.  Continuing.\n" % (created,)
                aucreated[auid] = None
        else:
            print "FAIL: created time was not set.\n"
            aucreated[auid] = None
            
        _get_list_articles(client, auid, auarticles, options)
      
        # Note: There is no article iterator for RSC.  This is a work-around.
        if auid.find('ClockssRoyalSocietyOfChemistryPlugin') >= 0 and (options.host.find("ingest") >= 0):
            _get_list_urls(client, auid, auarticles)
            cursor.execute("""INSERT INTO burp(machinename, port, rundate, 
auname, auid, auyear, numarticles, publisher, created)
VALUES ("%s", "%s", NOW(), "%s", "%s", "%s", %d, "rsc", '%s')""" % \
                            (host, port, auname[auid], auid, auyear[auid], _count_articles(db, auarticles[auid]), time.strftime("%Y-%m-%d %H:%M:%S", aucreated[auid])))
        else:
            # Standard article...
            if aucreated[auid] is not None:
                cursor.execute("""INSERT INTO burp(machinename, port, rundate, 
auname, auid, auyear, austatus, aulastcrawlresult, aucontentsize, audisksize, 
aurepository, numarticles, publisher, created)
VALUES ("%s", "%s", NOW(), "%s", "%s", "%s", "%s", "%s", "%s", "%s", 
"%s", "%s", "default", '%s')"""  % \
                       (host, port, auname[auid], auid, auyear[auid], austatus[auid], aulastcrawlresult[auid], aucontentsize[auid], audisksize[auid], aurepository[auid], _count_articles(db, auarticles[auid]), time.strftime("%Y-%m-%d %H:%M:%S", aucreated[auid])))
            else:
                cursor.execute("""INSERT INTO burp(machinename, port, rundate, 
auname, auid, auyear, austatus, aulastcrawlresult, aucontentsize, audisksize, 
aurepository, numarticles, publisher, created)
VALUES ("%s", "%s", NOW(), "%s", "%s", "%s", "%s", "%s", "%s", "%s", 
"%s", "%s", "default", NULL)"""  % \
                       (host, port, auname[auid], auid, auyear[auid], austatus[auid], aulastcrawlresult[auid], aucontentsize[auid], audisksize[auid], aurepository[auid], _count_articles(db, auarticles[auid])))
    
    cursor.execute("INSERT INTO lastcomplete(machinename, port, completedate) VALUES (\"%s\", %d, NOW())" %
                   (host, int(port)))
    
    print "****** " + options.host + " finished ******"

            
def _main_procedure():
    parser = _make_command_line_parser()
    (options, args) = parser.parse_args(values=parser.get_default_values())
    _check_required_options(parser, options)

    # Verification
    _test_parse_list_of_articles()

    try:
        db = MySQLdb.connect(host="localhost", user="edwardsb", passwd=options.dbpassword, db="burp")
    
# Send the reports
        if _need_report(db, options):
            client = _make_client(options)
            _article_report(client, db, options)

    except:
        print "****** " + options.host + ": Error."
        raise
    finally:
        db.commit()
        db.close()

if __name__ == '__main__':    
    _main_procedure()
