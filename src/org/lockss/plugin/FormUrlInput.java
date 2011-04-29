/**
 * 
 */
package org.lockss.plugin;

import org.lockss.util.StringUtil;
import org.lockss.util.UrlUtil;

/**
 * @author mlanken
 *
 */
public class FormUrlInput  implements Comparable<FormUrlInput> {
  private String _name;
  private String _value;
  public FormUrlInput(String name, String value) {
	  if (name.length()<=0) {
		  //TODO: throw ?
//		  throw FormUrlException
	  }
	  _name=name;
	  _value=value;
//Possibly this should throw an internal error
	  if (_value==null) {
	    _value = "";
	  }	  
  }
  public String getRawName() {
	  return _name;
  }
  public String getRawValue() {
	  return  _value;
  }
  //application/x-www-form-encoded content requires space be converted to a +.  This will also allow us to match incoming client requests
  public String formEncodeParameter(String url) {
	  //url = StringUtil.replaceString(url, " ", "+");
	  return UrlUtil.encodeUrl(url);
  }
  public String getName() {
	  return formEncodeParameter(_name);
  }
  public String getValue() {
	  return formEncodeParameter(_value);
  }
//default behavior is to behave like a String for GET requests
  public String toString() {
	  return getName() + "=" + getValue();
  }
//using encoded values to match what we see on the other end?
  public int compareTo(FormUrlInput b) {
	  int name_compare = getName().compareTo(b.getName()); 
	  if (name_compare != 0 ) { return name_compare; }
	  int value_compare = getValue().compareTo(b.getValue()); 
	  if (value_compare != 0 ) { return value_compare; }
	  return 0;
  }
}
