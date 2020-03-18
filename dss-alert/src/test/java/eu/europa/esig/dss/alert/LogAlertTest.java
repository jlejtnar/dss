package eu.europa.esig.dss.alert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.alert.handler.AlertHandler;
import eu.europa.esig.dss.alert.handler.CompositeAlertHandler;
import eu.europa.esig.dss.alert.handler.log.LogExceptionAlertHandler;
import eu.europa.esig.dss.alert.handler.log.LogExceptionAlertHandler.LogLevel;

public class LogAlertTest {
	
	private static final String EXCEPTION_MESSAGE = "Bye World!";
	
	@Test
	public void warnLogAlertTest() {
		Exception exception = new Exception(EXCEPTION_MESSAGE);
		
		CallbackExceptionAlertHandler callback = new CallbackExceptionAlertHandler();
		LogExceptionAlertHandler logExceptionAlertHandler = new LogExceptionAlertHandler(LogLevel.WARN, false);
		
		CompositeAlertHandler<Exception> alertHandler = new CompositeAlertHandler<Exception>(Arrays.asList(callback, logExceptionAlertHandler));
		
		ExceptionAlert exceptionAlert = new ExceptionAlert(alertHandler);
		exceptionAlert.alert(exception);
		
		assertTrue(callback.called);
	}
	
	@Test
	public void errorLogAlertTest() {
		Exception exception = new Exception(EXCEPTION_MESSAGE);
		
		CallbackExceptionAlertHandler callback = new CallbackExceptionAlertHandler();
		LogExceptionAlertHandler logExceptionAlertHandler = new LogExceptionAlertHandler(LogLevel.ERROR, true);
		
		CompositeAlertHandler<Exception> alertHandler = new CompositeAlertHandler<Exception>(Arrays.asList(callback, logExceptionAlertHandler));
		
		ExceptionAlert exceptionAlert = new ExceptionAlert(alertHandler);
		exceptionAlert.alert(exception);
		
		assertTrue(callback.called);
	}
	
	class CallbackExceptionAlertHandler implements AlertHandler<Exception> {
		
		private boolean called = false;

		@Override
		public void process(Exception e) {
			called = true;
		}
		
	}

}