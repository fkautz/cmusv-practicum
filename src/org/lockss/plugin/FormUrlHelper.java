package org.lockss.plugin;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.lang.StringBuilder;

import org.apache.commons.lang.StringUtils;
import org.lockss.util.UrlUtil;

//helper class to convert between string representation of a form URL and the object representation.
//Once in object form, performing modifications will be easier.
public class FormUrlHelper {
    String _baseUrl;
	ArrayList<FormUrlInput> _inputs;
	boolean _valid = false;
	boolean _alreadyEncoded = false;
	
	FormUrlHelper() {
		this("");
	}
	FormUrlHelper(String baseUrl) {
		setBaseUrl(baseUrl);
	}
	public void setBaseUrl(String baseUrl) {
		_baseUrl = baseUrl;
		_valid = true;
		_inputs = new ArrayList<FormUrlInput>();
		_alreadyEncoded = false;
	}

	//add a single key/value pair to the url in order
	public void add(String key, String value) {
		if (value == null) { value = "";}
		if (key == null) { key = "";}
		_inputs.add(new FormUrlInput(key, value));
	}
	
	public boolean isValid() {
		return _valid;
	}
	
	//NOTE: this routine should almost never be called because  it is impossible to tell whether equal signs and ampersands are part of the form parameters or being used as separators. 
	//Use convertFromEncodedString instead
	public boolean convertFromString(String url) {
// invalid url if starts with ?, doesn't contain ?
		if (StringUtils.indexOf(url, "?") == -1
				|| StringUtils.indexOf(url, "?") == 0) {
			_valid = false;
			return _valid;
		}
		
		String prefix = StringUtils.substringBefore(url, "?");
		setBaseUrl(prefix);
		String rest = StringUtils.substringAfter(url, "?");
		String key;
		String value;
		//System.out.println("rest="+ rest);
		while (rest != null && rest.length() > 0) {
			//System.out.println("rest2="+ rest);

			if (StringUtils.indexOf(rest, "=") > 0) {
				key = StringUtils.substringBefore(rest,"=");
				rest = StringUtils.substringAfter(rest,"=");
				//System.out.println("rest3="+ rest);
				if (rest != null && StringUtils.indexOf(rest, "&") != -1) {
					value = StringUtils.substringBefore(rest, "&");
					add(key, value);
					rest = StringUtils.substringAfter(rest, "&");
				} else {
					// last value
					value = rest;
					add(key, value);
					rest = "";
				}
			}
			else {
				//This indicates a form url missing the equals  sign, stop processing at this point
				_valid = false;
				rest="";
			}
		}
		return _valid;
	}

	public boolean convertFromEncodedString(String url) {
		convertFromString(url);
		_alreadyEncoded = true;
		return _valid;
	}
	
//construct a POST or debug URL. 
	public String toString() {
		StringBuilder url = new StringBuilder(_baseUrl);
		int i=0;
		url.append("?");
		for (i=0;i<_inputs.size();i++) {
			url.append(_inputs.get(i).getRawName() + "=" + _inputs.get(i).getRawValue());			
			if (i!=_inputs.size()-1) {url.append("&");}
		}
		return url.toString();
    }
	//construct a get URL. 
	public String toEncodedString() {
		if (_alreadyEncoded) { return toString();}
		StringBuilder url = new StringBuilder(UrlUtil.encodeUrl(_baseUrl));
		int i=0;
		url.append("?");
		for (i=0;i<_inputs.size();i++) {
			url.append(_inputs.get(i).getName() + "=" + _inputs.get(i).getValue());			
			if (i!=_inputs.size()-1) {url.append("&");}
		}
		return url.toString();
    }

    //reorders the keys and values to put them in alphabetically sorted order.
    public void sortKeyValues() {
       Collections.sort(_inputs);
    }
    
//limits the # of values for a parameter to the first #limit
    //Note: expects the unencoded key value because that is what the client passed in the add call
    public void applyLimit(String key, int limit) {
//iterate through the list.  After limit values, start deleting them.
    	int seen = 0;
    	//System.out.println("_inputs.size()="+_inputs.size()+",key="+key);
    	for (int i=0;i<_inputs.size();i++) {
    		if (_inputs.get(i).getRawName().equals(key)) {
				seen++;
    			if ( seen > limit ) {
    				//System.out.println("removing element at i="+i);
    				_inputs.remove(i);
    				i--; //looks ugly
    			}
    		}
    	}    	
    }
}
