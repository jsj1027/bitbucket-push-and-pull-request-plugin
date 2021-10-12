package io.jenkins.plugins.bitbucketpushandpullrequest;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;

import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.taskdefs.Sleep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.bitbucketpushandpullrequest.common.BitBucketPPRConst;
import io.jenkins.plugins.bitbucketpushandpullrequest.receiver.BitBucketPPRHookReceiver;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import javaposse.jobdsl.plugin.RemovedJobAction;

@RunWith(MockitoJUnitRunner.class)
public class SomeJobIsTriggeredTest {
  private static final int SUCCESS_RESPONSE = 200;

  /* Global Jenkins instance mock */
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Rule
  public LoggerRule l = new LoggerRule();

  @Rule
  public LoggerRule m = new LoggerRule();

  private void createSeedJob(String desc) throws Exception {
    /* Create seed job which will process DSL */
    FreeStyleProject seedJob = j.createFreeStyleProject();
    ExecuteDslScripts dslScript = new ExecuteDslScripts();
    dslScript.setUseScriptText(Boolean.TRUE);
    dslScript.setScriptText(desc);
    dslScript.setTargets(null);
    dslScript.setIgnoreExisting(Boolean.FALSE);
    dslScript.setRemovedJobAction(RemovedJobAction.DELETE);
    seedJob.getBuildersList().add(dslScript);

    j.buildAndAssertSuccess(seedJob);
  }

  private String readDslScript(String path) throws Exception {
    String script = null;
    try {
      ClassLoader classloader = Thread.currentThread().getContextClassLoader();
      InputStream is = classloader.getResourceAsStream(path);
      script = IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return script;
  }

  private String readPayloadScript(String path) throws Exception {
    String script = null;
    try {
      ClassLoader classloader = Thread.currentThread().getContextClassLoader();
      InputStream is = classloader.getResourceAsStream(path);
      script = IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return script;
  }

  @Test
  public void testJobIsTriggered() throws Exception {
    l.capture(10).record(BitBucketPPRHookReceiver.class.getName(), Level.ALL);
    l.capture(10).record(BitBucketPPRJobProbe.class.getName(), Level.ALL);

    /* Create seed job which will process DSL */
    createSeedJob(readDslScript("./dsl/testDslTriggerCreateUpdatedApprovedPRActionsFreeStyle.groovy"));
    /* Fetch the newly created job and check its trigger configuration */
    FreeStyleProject createdJob = (FreeStyleProject) j.getInstance().getItem("test-job");

    assertNull(createdJob.getLastSuccessfulBuild());

    JenkinsRule.WebClient webClient = j.createWebClient();
    WebRequest wrs = new WebRequest(new URL(webClient.getContextPath() + "bitbucket-hook/"), HttpMethod.POST);
    wrs.setAdditionalHeader("x-event-key", "pullrequest:created");
    wrs.setRequestBody(
        URLEncoder.encode(readPayloadScript("./cloud/pr_created.json"), StandardCharsets.UTF_8.toString()));
    wrs.setAdditionalHeader(BitBucketPPRConst.APPLICATION_X_WWW_FORM_URLENCODED,
        BitBucketPPRConst.APPLICATION_X_WWW_FORM_URLENCODED);
    wrs.setCharset("UTF-8");
    WebResponse resp = webClient.getPage(wrs).getWebResponse();

    assertEquals(SUCCESS_RESPONSE, resp.getStatusCode());

    // assert
    l.getRecords().stream().forEach(System.out::println);

    assertThat(l, recorded(
        Level.INFO, containsString("Received POST request over Bitbucket hook")
    ));

    // Triggering trigger... :-)
    assertThat(l, recorded(
      Level.FINE, containsString("Considering to poke")
    ));
    // assertThat(l, recorded(
    //   Level.FINE, containsString("Triggering trigger")
    // ));
  }
}