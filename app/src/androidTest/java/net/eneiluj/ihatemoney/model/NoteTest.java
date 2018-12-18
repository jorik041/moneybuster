package net.eneiluj.ihatemoney.model;

import junit.framework.TestCase;

import java.util.Calendar;

/**
 * Tests the Session Model
 */
public class SessionTest extends TestCase {

    public void testMarkDownStrip() {
        CloudSession session = new CloudSession(0, Calendar.getInstance(), "#Title", "", false, null, null);
        assertTrue("Title".equals(session.getTitle()));
        session.setTitle("* Aufzählung");
        assertTrue("Aufzählung".equals(session.getTitle()));
    }
}
