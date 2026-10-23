package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CrashDiagnosticsTest {
  private Context context;
  @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("vpn_diagnostics",0).edit().clear().commit();}
  private void crash(){RuntimeException error=new RuntimeException("secret-password https://private.invalid/token");error.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.snaptvnow.tv.Fixture","start","Fixture.java",12)});CrashDiagnostics.record(context,Thread.currentThread(),error);}
  @Test public void aSecondInterruptedStartupCannotConsumeAndLoseTheFirstCrash(){
    crash();String first=CrashDiagnostics.consume(context);assertNotNull(first);assertEquals(first,CrashDiagnostics.consume(context));
    assertEquals(first,CrashDiagnostics.consume(context.getApplicationContext()));assertTrue(CrashDiagnostics.report(context).contains("Fixture.start:12"));
  }
  @Test public void explicitAcknowledgementStopsThePopupButKeepsEvidenceAvailableInSettings(){
    crash();String shown=CrashDiagnostics.consume(context);CrashDiagnostics.acknowledge(context,shown);
    assertNull(CrashDiagnostics.consume(context));assertTrue(CrashDiagnostics.report(context).contains("Fixture.start:12"));
  }
  @Test public void anOldDialogCannotAcknowledgeANewerOccurrenceOfTheSameError(){
    crash();String old=CrashDiagnostics.consume(context);crash();String recent=CrashDiagnostics.consume(context);assertNotEquals(old,recent);
    CrashDiagnostics.acknowledge(context,old);assertEquals(recent,CrashDiagnostics.consume(context));
  }
  @Test public void anUnrelatedVpnStatusChangeDoesNotInvalidateAcknowledgementOfTheSameCrash(){
    crash();String shown=CrashDiagnostics.consume(context);CrashDiagnostics.state(context,"LEVEL_NOTCONNECTED");CrashDiagnostics.acknowledge(context,shown);assertNull(CrashDiagnostics.consume(context));assertTrue(CrashDiagnostics.report(context).contains("Fixture.start:12"));
  }
  @Test public void reportsDoNotIncludeExceptionMessagesCredentialsOrLinks(){
    crash();String report=CrashDiagnostics.report(context);assertFalse(report.contains("secret-password"));assertFalse(report.contains("private.invalid"));assertTrue(report.contains("RuntimeException"));
  }
  @Test public void olderFireOsWithoutAnExitHistoryDoesNotPresentAnInventedCause(){
    String report=CrashDiagnostics.report(context);assertTrue(report.contains("motivo del cierre no está confirmado"));assertTrue(report.contains("No hay una excepción Java registrada"));
    assertTrue(ProcessExitDiagnostics.reason(16).contains("actualización"));assertTrue(ProcessExitDiagnostics.reason(5).contains("nativo"));assertTrue(ProcessExitDiagnostics.reason(10).contains("también puede corresponder"));
  }
}
