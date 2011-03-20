/**
 * 
 */
package de.thiemeconnect.plugin;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.GoslingHtmlLinkExtractor;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;

/**
 * @author vibhor
 *
 */
public class ThiemeJsonLinkExtractorFactory implements LinkExtractorFactory {

	/**
     * @param mimeType ignored
     * @return new ThiemeHtmlLinkExtractor
	 * @see org.lockss.extractor.LinkExtractorFactory#createLinkExtractor(java.lang.String)
	 */
	@Override
	public LinkExtractor createLinkExtractor(String mimeType)
			throws PluginException {
		return new ThiemeJsonLinkExtractor();
	}

}
