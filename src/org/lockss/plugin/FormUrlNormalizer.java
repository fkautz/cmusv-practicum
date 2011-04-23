/**
 * 
 */
package org.lockss.plugin;

import org.apache.commons.lang.StringUtils;
import org.lockss.daemon.PluginException;
import org.lockss.util.Logger;

import java.util.HashMap;
import java.util.Set;
import java.util.Collections;

/**
 * @author mlanken
 *
 */
public class FormUrlNormalizer implements UrlNormalizer {
private boolean _sortAllUrls;
private HashMap<String,Integer> _limits;
private FormUrlHelper _converter;
	/* (non-Javadoc)
	 * @see org.lockss.plugin.UrlNormalizer#normalizeUrl(java.lang.String, org.lockss.plugin.ArchivalUnit)
	 * Does (optional) sorting, limits and mandatory encoding for GET urls
	 */
	@Override
	public String normalizeUrl(String url, ArchivalUnit au)
			throws PluginException {

		if (url == null)  { return url;}
		//return all non-form urls unchanged
		if (StringUtils.indexOf(url, "?") == -1) { return url; }
        //if there is a problem converting the url, return the original url;
		if (!_converter.convertFromString(url)) { return url; }
		
		if (_sortAllUrls) {
			_converter.sortKeyValues();
		}
		
		if (_limits!=null) {
			Set<String> limitedKeys = (Set<String>)_limits.keySet();
			for (String key : limitedKeys) {
				_converter.applyLimit( key, _limits.get(key));
			}
		}

		String outputUrl = _converter.toEncodedString();
		if (!outputUrl.equals(url)) {
			Logger.getLogger("FormUrlNormalizer").debug3(" converted " + url + " to " + outputUrl);
		}
		return outputUrl;
	}

	public FormUrlNormalizer() {
		this(false, null);
	}
	
	public FormUrlNormalizer(boolean sortAllUrls, HashMap<String,Integer> limits) {
    	_sortAllUrls = sortAllUrls;
    	_limits = limits;
    	_converter = new FormUrlHelper();
    }
}
