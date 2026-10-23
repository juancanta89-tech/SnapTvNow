package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.ContextWrapper;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class ProcessExitDiagnosticsTest {
  private Context context;private ActivityManager manager;
  @Before public void setup(){manager=mock(ActivityManager.class);context=new ContextWrapper(RuntimeEnvironment.getApplication()){@Override public Object getSystemService(String name){return ACTIVITY_SERVICE.equals(name)?manager:super.getSystemService(name);}};}
  @Test public void nativeExitEvidenceIsReadOnlyForThisPackageWithoutSensitiveDescriptions(){
    ApplicationExitInfo info=ApplicationExitInfoBuilder.newBuilder().setReason(5).setStatus(11).setTimestamp(1792780000000L).setProcessName("secret-user").setDescription("https://private.invalid/password").build();
    when(manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3)).thenReturn(Arrays.asList(info));
    String report=ProcessExitDiagnostics.report(context);assertTrue(report.contains("Fallo de código nativo"));assertTrue(report.contains("código 11"));assertFalse(report.contains("private.invalid"));assertFalse(report.contains("secret-user"));verify(manager).getHistoricalProcessExitReasons(context.getPackageName(),0,3);
  }
  @Test public void noHistoryIsNotPresentedAsProofThatTheAppNeverCrashed(){
    when(manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3)).thenReturn(Collections.emptyList());assertTrue(ProcessExitDiagnostics.report(context).contains("eso no confirma su ausencia"));
  }
  @Test public void aVendorRestrictionCannotCrashTheDiagnosticScreen(){
    when(manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3)).thenThrow(new SecurityException("restricted"));assertTrue(ProcessExitDiagnostics.report(context).contains("motivo no está confirmado"));
  }
}
