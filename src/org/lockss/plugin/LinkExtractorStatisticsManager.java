package org.lockss.plugin;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractor.Callback;
import org.lockss.util.Logger;

public class LinkExtractorStatisticsManager {
	private HashMap<String, LinkExtractorStatsCallback> callbacks_ = new HashMap<String, LinkExtractorStatsCallback>();
	private HashMap<String, Long>  durations_ = new HashMap<String, Long>();
	private String activeMeasurement_= null;
	private long start_time_millis_;

	//disabled by default because stats were almost all 0 (or 1ms)
	private boolean collect_cpu_stats_ = false;
	private HashMap<String, Long>  user_cpus_ = new HashMap<String, Long>();
	private long start_user_cpu_ns_;
	private HashMap<String, Long>  sys_cpus_ = new HashMap<String, Long>();
	private long start_sys_cpu_ns_;
	
	//code from http://nadeausoftware.com/articles/2008/03/java_tip_how_get_cpu_and_user_time_benchmarking#TimingasinglethreadedtaskusingCPUsystemandusertime	 
	/** Get user time in nanoseconds. */
	public long getUserTime( ) {
	    ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
	    return bean.isCurrentThreadCpuTimeSupported( ) ?
	        bean.getCurrentThreadUserTime( ) : 0L;
	}

	/** Get system time in nanoseconds. */
	public long getSystemTime( ) {
	    ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
	    return bean.isCurrentThreadCpuTimeSupported( ) ?
	        (bean.getCurrentThreadCpuTime( ) - bean.getCurrentThreadUserTime( )) : 0L;
	}
	
	public void stopMeasurement() {
		long duration = System.currentTimeMillis() - start_time_millis_;
		durations_.put(activeMeasurement_,duration);
		if (collect_cpu_stats_) {
			long  user_cpu_ns = getUserTime() - start_user_cpu_ns_;
			user_cpus_.put(activeMeasurement_, user_cpu_ns);
			long  sys_cpu_ns = getSystemTime() - start_sys_cpu_ns_;
			sys_cpus_.put(activeMeasurement_, sys_cpu_ns);
		}
	}
	
	public void startMeasurement(String name) {
		if (activeMeasurement_!=null) {
			stopMeasurement();
		}
		start_time_millis_ = System.currentTimeMillis();
		activeMeasurement_ = name;
		if (collect_cpu_stats_) {
			start_user_cpu_ns_ =  getUserTime();
			start_sys_cpu_ns_ = getSystemTime();
		}
	}
	
	//Compares a base extractor to  a alternate extractor
	public void compareExtractors(String base, String alt, String name) {
		if (!callbacks_.containsKey(base) || ! callbacks_.containsKey(alt)) {
			throw new IllegalArgumentException("invalid base or alternate name");
		} 
		System.out.println("Comparing " + base + " and " + alt +  " for " + name);
		Logger logger = Logger.getLoggerWithInitialLevel("LinkExtractorStats", Logger.LEVEL_DEBUG3);
		Set<String> base_urls = callbacks_.get(base).GetUrls();
		Set<String> alt_urls = callbacks_.get(alt).GetUrls();
		Set<String> common_urls = new HashSet<String>(alt_urls);
		common_urls.retainAll(base_urls);

		int common_url_count = common_urls.size();
		int base_url_count = base_urls.size() - common_url_count;
		int alt_url_count = alt_urls.size() - common_url_count;
		logger.debug2("Stats for " + name + " Common URLs: " + common_url_count + " " + base + " only: "+ base_url_count + " " + alt+ " only: " +alt_url_count );

		if (logger.isDebug3()) {
			if (base_url_count > 0) {
				Set<String> base_only_urls = new HashSet<String>(base_urls);
			    base_only_urls.removeAll(common_urls);
			    logger.debug3(base + " only urls: " + base_only_urls.toString());
			    		
			}
			if  (alt_url_count > 0 ) {
				Set<String> alt_only_urls = new HashSet<String>(alt_urls);
				alt_only_urls.removeAll(common_urls);
				logger.debug3("Alt only urls: " + alt_only_urls.toString());
			}
		}
		if (durations_.containsKey(base) && durations_.containsKey(alt)) {
			System.out.println("Durations - " + base + "=" + durations_.get(base) + ", " + alt + "=" + durations_.get(alt));
			logger.debug2("Durations - " + base + "=" + durations_.get(base) + ", " + alt + "=" + durations_.get(alt));
		}

		if (collect_cpu_stats_) {
			if (user_cpus_.containsKey(base) && user_cpus_.containsKey(alt)) {
				System.out.println("User cpu(ns) - " + base + "=" + user_cpus_.get(base) + ", " + alt + "=" + user_cpus_.get(alt));
				logger.debug2("User cpu(ns) - " + base + "=" + user_cpus_.get(base) + ", " + alt + "=" + user_cpus_.get(alt));
			}
			if (sys_cpus_.containsKey(base) && sys_cpus_.containsKey(alt)) {
				System.out.println("System cpu(ns) - " + base + "=" + sys_cpus_.get(base) + ", " + alt + "=" + sys_cpus_.get(alt));
				logger.debug2("System cpu(ns) - " + base + "=" + sys_cpus_.get(base) + ", " + alt + "=" + sys_cpus_.get(alt));
			}
		}		
	}
	
	public Callback wrapCallback(Callback cb, String name) {
		if (!callbacks_.containsKey(name)) {
			callbacks_.put(name, new LinkExtractorStatsCallback(cb));	
		}
		return callbacks_.get(name);		
	}
	
	private class LinkExtractorStatsCallback implements LinkExtractor.Callback {
		private Set<String> urls_found_ = new HashSet<String>();
		private Callback cb_;
		public LinkExtractorStatsCallback(Callback cb) {
			cb_ = cb;
		}

		public Set<String> GetUrls() {
			return urls_found_;
		}
		
//If we think the callee is actually doing real work in foundLink, it would make sense to stop timing while it is called. 
		public void foundLink(String url) {
			urls_found_.add(url);
			cb_.foundLink(url);
	    }
	  }
}
