/**
 * 
 */
package de.thiemeconnect.plugin;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;

/**
 * Factory for creating new link extractors for thieme. Mime type is currently ignored
 *
 * @author nvibhor
 *
 */
public class ThiemeHtmlLinkExtractorFactory implements LinkExtractorFactory {

	/** 
     * @param mimeType ignored
     * @return new ThiemeHtmlLinkExtractor
	 * @see org.lockss.extractor.LinkExtractorFactory#createLinkExtractor(java.lang.String)
	 */
	@Override
	public LinkExtractor createLinkExtractor(String mimeType)
			throws PluginException {
		return new ThiemeHtmlLinkExtractor();
	}

}
