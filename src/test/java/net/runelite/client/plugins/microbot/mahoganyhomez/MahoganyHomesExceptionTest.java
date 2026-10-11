package net.runelite.client.plugins.microbot.mahoganyhomez;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesExceptionTest
{
	@Test void originalThrowableAndContextSurviveMissingBridgeAndPlayer() throws Exception
	{
		MahoganyHomesScript script = new MahoganyHomesScript();
		Logger logger = (Logger) LoggerFactory.getLogger(MahoganyHomesScript.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try
		{
			NullPointerException exception = new NullPointerException("player null after pool");
			assertEquals(MahoganyHomesScript.State.ERROR,
				script.onError(MahoganyHomesScript.State.TRAVEL, exception));
			assertNotNull(script.getError(), "Failure reporting must survive an absent live client");
			ILoggingEvent event = appender.list.get(0);
			assertTrue(event.getFormattedMessage().contains("state=TRAVEL"));
			assertTrue(event.getFormattedMessage().contains("target=null"));
			assertTrue(event.getFormattedMessage().contains("bridgeStatus=null"));
			assertNotNull(event.getThrowableProxy());
			assertEquals(exception.getClass().getName(), event.getThrowableProxy().getClassName());
			assertEquals(exception.getMessage(), event.getThrowableProxy().getMessage());
			assertTrue(event.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
		}
		finally
		{
			logger.detachAppender(appender);
			appender.stop();
		}
	}
}
