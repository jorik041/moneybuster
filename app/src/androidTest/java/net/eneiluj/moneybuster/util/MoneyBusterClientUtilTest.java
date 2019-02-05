package net.eneiluj.moneybuster.util;

import junit.framework.TestCase;

/**
 * Tests the PaybackClientUtil
 * Created by stefan on 24.09.15.
 */
public class MoneyBusterClientUtilTest extends TestCase {
    public void testFormatURL() {
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/"));
        assertEquals("http://example.com/", PaybackClientUtil.formatURL("http://example.com/"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/apps"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/apps/ihatemoney"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2"));
        assertEquals("https://example.com/", PaybackClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
        assertEquals("https://example.com/nextcloud/", PaybackClientUtil.formatURL("example.com/nextcloud"));
        assertEquals("http://example.com:443/nextcloud/", PaybackClientUtil.formatURL("http://example.com:443/nextcloud/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
    }

    public void testIsHttp() {
        assertTrue(PaybackClientUtil.isHttp("http://example.com"));
        assertTrue(PaybackClientUtil.isHttp("http://www.example.com/"));
        assertFalse(PaybackClientUtil.isHttp("https://www.example.com/"));
        assertFalse(PaybackClientUtil.isHttp(null));
    }

    public void testIsValidURLTest() {
        assertTrue(PaybackClientUtil.isValidURL(null, "https://demo.nextcloud.org/"));
        assertFalse(PaybackClientUtil.isValidURL(null, "https://www.example.com/"));
        assertFalse(PaybackClientUtil.isValidURL(null, "htp://www.example.com/"));
        assertFalse(PaybackClientUtil.isValidURL(null, null));
    }
}