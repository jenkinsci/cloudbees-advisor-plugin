package com.cloudbees.jenkins.plugins.advisor;

import com.cloudbees.jenkins.plugins.advisor.client.model.AccountCredentials;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.Gson;
import hudson.util.FormValidation;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.testng.PowerMockTestCase;
import org.powermock.reflect.Whitebox;

import java.net.URL;
import java.util.concurrent.Callable;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;

/**
 * Test the AdvisorGlobalConfiguration page; essentially the core of 
 * the CloudBees Jenkins Advisor connection.
 */
@PowerMockIgnore({"org.apache.http.conn.ssl.*", "javax.net.ssl.*" , "javax.crypto.*"})
public class AdvisorGlobalConfigurationTest extends PowerMockTestCase {

  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().port(9999));

  private AdvisorGlobalConfiguration advisor;
  private final String email = "test@cloudbees.com";
  private final String password = "test123";

  
  @Before
  public void setup() {
    advisor = AdvisorGlobalConfiguration.getInstance();
  }
  

  @Test
  public void testBaseConfigurationPage() throws Exception {
    WebClient wc = j.createWebClient();
    wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
    WebRequest req = new WebRequest(
        new URL(j.jenkins.getRootUrl() + advisor.getUrlName()),
        HttpMethod.GET
    );

    Page page = wc.getPage(wc.addCrumb(req));
    j.assertGoodStatus(page);
  }


  @Test
  public void testHelpOnPage() throws Exception {
      j.assertHelpExists(AdvisorGlobalConfiguration.class, "-nagDisabled");
  }

  @Test
  public void testConnection() throws Exception {
    String wrongPassword = "sosowrong";
    stubFor(post(urlEqualTo("/login"))
      .withHeader("Content-Type", WireMock.equalTo("application/json"))
      .withRequestBody(equalToJson(new Gson().toJson(new AccountCredentials(email, password))))
      .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Authorization", "Bearer 327hfeaw7ewa9")));

    stubFor(post(urlEqualTo("/login"))
      .withHeader("Content-Type", WireMock.equalTo("application/json"))
      .withRequestBody(equalToJson(new Gson().toJson(new AccountCredentials(email, wrongPassword))))
      .willReturn(aResponse()
          .withStatus(404)));
            
    final AdvisorGlobalConfiguration.DescriptorImpl advisorDescriptor = (AdvisorGlobalConfiguration.DescriptorImpl) advisor.getDescriptor();
    FormValidation formValidation = advisorDescriptor.doTestConnection(email, Secret.fromString(wrongPassword));
    assertEquals("Test connection fail was expected", FormValidation.Kind.ERROR, formValidation.kind);
    formValidation = advisorDescriptor.doTestConnection(email, Secret.fromString(password));
    assertEquals("Test connection pass was expected", FormValidation.Kind.OK, formValidation.kind);
  }

  public void testConnectionUI() throws Exception {
    String wrongPassword = "sosowrong";
    stubFor(post(urlEqualTo("/login"))
      .withHeader("Content-Type", WireMock.equalTo("application/json"))
      .withRequestBody(equalToJson(new Gson().toJson(new AccountCredentials(email, password))))
      .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Authorization", "Bearer 327hfeaw7ewa9")));

    stubFor(post(urlEqualTo("/login"))
      .withHeader("Content-Type", WireMock.equalTo("application/json"))
      .withRequestBody(equalToJson(new Gson().toJson(new AccountCredentials(email, wrongPassword))))
      .willReturn(aResponse()
          .withStatus(404)));

    WebClient wc = j.createWebClient();
    HtmlPage managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertFalse(managePage.asText().contains("There was a connection failure"));
    assertFalse(managePage.asText().contains("You are connected"));
    
    DoConfigureInfo doConfigure = new DoConfigureInfo();

    doConfigure.setUp(email, wrongPassword);
    j.executeOnServer(doConfigure);
    managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertTrue(managePage.asText().contains("There was a connection failure"));

    doConfigure.setUp(email, password);
    j.executeOnServer(doConfigure);
    managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertTrue(managePage.asText().contains("You are connected"));
  }

  @Test
  public void testConfigure() throws Exception {
    WebClient wc = j.createWebClient();

    DoConfigureInfo doConfigure = new DoConfigureInfo();
    // Invalid email - send back to main page
    doConfigure.setUp("", password);
    HttpRedirect hr1 = (HttpRedirect)j.executeOnServer(doConfigure);
    String url1 = Whitebox.getInternalState(hr1, "url");
    assertEquals("Rerouted back to configuration", url1, "/jenkins/cloudbees-jenkins-advisor");

    // Invalid password - send back to main page
    doConfigure.setUp("fake@test.com", "");
    HttpRedirect hr2 = (HttpRedirect)j.executeOnServer(doConfigure);
    String url2 = Whitebox.getInternalState(hr2, "url");
    assertEquals("Rerouted back to configuration", url2, "/jenkins/cloudbees-jenkins-advisor");

    // Redirect to Pardot
    doConfigure.setUp(email, password);
    HttpRedirect hr3 = (HttpRedirect)j.executeOnServer(doConfigure);
    String url3 = Whitebox.getInternalState(hr3, "url");
    assertThat(url3.contains("go.pardot.com"), is(true));

    // Redirect to main page
    HttpRedirect hr4 = (HttpRedirect)j.executeOnServer(doConfigure);
    String url4 = Whitebox.getInternalState(hr4, "url");
    assertEquals("Rerouted back to main mpage", url4, "/jenkins/manage");
  }


  @Test
  public void testPersistance() throws Exception {
    assertNull("Email before configuration save - ", advisor.getEmail());

    DoConfigureInfo doConfigure = new DoConfigureInfo();
    doConfigure.setUp(email, password);
    j.executeOnServer(doConfigure);
    advisor.load();
    assertEquals("Email after configuration save - ", email, advisor.getEmail());
    assertThat(advisor.getExcludedComponents().size(), is(5));
  }

  @Test
  public void testSaveExcludedComponents() throws Exception {
    WebClient wc = j.createWebClient();

    String noComponentsSelected = "{\"components\": [{\"selected\": false, \"name\": \"JenkinsLogs\"}, {\"selected\": false, \"name\"\r\n: \"SlaveLogs\"}, {\"selected\": false, \"name\": \"GCLogs\"}, {\"selected\": false, \"name\": \"AgentsConfigFile\"\r\n}, {\"selected\": false, \"name\": \"ConfigFileComponent\"}, {\"selected\": false, \"name\": \"OtherConfigFilesComponent\"\r\n}, {\"selected\": false, \"name\": \"AboutBrowser\"}, {\"selected\": false, \"name\": \"AboutJenkins\"}, {\"selected\"\r\n: true, \"name\": \"AboutUser\"}, {\"selected\": false, \"name\": \"AdministrativeMonitors\"}, {\"selected\": false\r\n, \"name\": \"BuildQueue\"}, {\"selected\": false, \"name\": \"DumpExportTable\"}, {\"selected\": false, \"name\": \"EnvironmentVariables\"\r\n}, {\"selected\": false, \"name\": \"FileDescriptorLimit\"}, {\"selected\": false, \"name\": \"JVMProcessSystemMetricsContents\"\r\n}, {\"selected\": false, \"name\": \"LoadStats\"}, {\"selected\": false, \"name\": \"LoggerManager\"}, {\"selected\"\r\n: true, \"name\": \"Metrics\"}, {\"selected\": false, \"name\": \"NetworkInterfaces\"}, {\"selected\": false, \"name\"\r\n: \"NodeMonitors\"}, {\"selected\": false, \"name\": \"RootCAs\"}, {\"selected\": false, \"name\": \"SystemConfiguration\"\r\n}, {\"selected\": false, \"name\": \"SystemProperties\"}, {\"selected\": false, \"name\": \"UpdateCenter\"}, {\"selected\"\r\n: true, \"name\": \"SlowRequestComponent\"}, {\"selected\": false, \"name\": \"DeadlockRequestComponent\"}, {\"selected\"\r\n: true, \"name\": \"PipelineTimings\"}, {\"selected\": false, \"name\": \"PipelineThreadDump\"}, {\"selected\": false\r\n, \"name\": \"ThreadDumps\"}]}";
    DoConfigureInfo doConfigure = new DoConfigureInfo();
    doConfigure.setUp(noComponentsSelected);
    j.executeOnServer(doConfigure);
    assertThat(advisor.getExcludedComponents().size(), is(25));
    HtmlPage managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertTrue(managePage.asText().contains("uncheckedJenkinsLogs"));
    assertTrue(managePage.asText().contains("uncheckedSlaveLogs"));
    
    String allComponentsSelected = "{\"components\": [{\"selected\": true, \"name\": \"JenkinsLogs\"}, {\"selected\": true, \"name\"\r\n: \"SlaveLogs\"}, {\"selected\": true, \"name\": \"GCLogs\"}, {\"selected\": true, \"name\": \"AgentsConfigFile\"\r\n}, {\"selected\": true, \"name\": \"ConfigFileComponent\"}, {\"selected\": true, \"name\": \"OtherConfigFilesComponent\"\r\n}, {\"selected\": true, \"name\": \"AboutBrowser\"}, {\"selected\": true, \"name\": \"AboutJenkins\"}, {\"selected\"\r\n: true, \"name\": \"AboutUser\"}, {\"selected\": true, \"name\": \"AdministrativeMonitors\"}, {\"selected\": true\r\n, \"name\": \"BuildQueue\"}, {\"selected\": true, \"name\": \"DumpExportTable\"}, {\"selected\": true, \"name\": \"EnvironmentVariables\"\r\n}, {\"selected\": true, \"name\": \"FileDescriptorLimit\"}, {\"selected\": true, \"name\": \"JVMProcessSystemMetricsContents\"\r\n}, {\"selected\": true, \"name\": \"LoadStats\"}, {\"selected\": true, \"name\": \"LoggerManager\"}, {\"selected\"\r\n: true, \"name\": \"Metrics\"}, {\"selected\": true, \"name\": \"NetworkInterfaces\"}, {\"selected\": true, \"name\"\r\n: \"NodeMonitors\"}, {\"selected\": true, \"name\": \"RootCAs\"}, {\"selected\": true, \"name\": \"SystemConfiguration\"\r\n}, {\"selected\": true, \"name\": \"SystemProperties\"}, {\"selected\": true, \"name\": \"UpdateCenter\"}, {\"selected\"\r\n: true, \"name\": \"SlowRequestComponent\"}, {\"selected\": true, \"name\": \"DeadlockRequestComponent\"}, {\"selected\"\r\n: true, \"name\": \"PipelineTimings\"}, {\"selected\": true, \"name\": \"PipelineThreadDump\"}, {\"selected\": true\r\n, \"name\": \"ThreadDumps\"}]}";
    DoConfigureInfo doConfigure2 = new DoConfigureInfo();
    doConfigure2.setUp(allComponentsSelected);
    j.executeOnServer(doConfigure2);
    assertThat(advisor.getExcludedComponents().size(), is(1));
    assertTrue(advisor.getExcludedComponents().contains("SENDALL"));
    managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertTrue(managePage.asText().contains("checkedJenkinsLogs"));
    assertTrue(managePage.asText().contains("checkedSlaveLogs"));

    String mixCompoentsSelected = "{\"components\": [{\"selected\": false, \"name\": \"JenkinsLogs\"}, {\"selected\": true, \"name\"\r\n: \"SlaveLogs\"}, {\"selected\": false, \"name\": \"GCLogs\"}, {\"selected\": true, \"name\": \"AgentsConfigFile\"\r\n}, {\"selected\": true, \"name\": \"ConfigFileComponent\"}, {\"selected\": true, \"name\": \"OtherConfigFilesComponent\"\r\n}, {\"selected\": false, \"name\": \"AboutBrowser\"}, {\"selected\": true, \"name\": \"AboutJenkins\"}, {\"selected\"\r\n: true, \"name\": \"AboutUser\"}, {\"selected\": false, \"name\": \"AdministrativeMonitors\"}, {\"selected\": true\r\n, \"name\": \"BuildQueue\"}, {\"selected\": true, \"name\": \"DumpExportTable\"}, {\"selected\": false, \"name\": \"EnvironmentVariables\"\r\n}, {\"selected\": true, \"name\": \"FileDescriptorLimit\"}, {\"selected\": false, \"name\": \"JVMProcessSystemMetricsContents\"\r\n}, {\"selected\": true, \"name\": \"LoadStats\"}, {\"selected\": true, \"name\": \"LoggerManager\"}, {\"selected\"\r\n: true, \"name\": \"Metrics\"}, {\"selected\": true, \"name\": \"NetworkInterfaces\"}, {\"selected\": true, \"name\"\r\n: \"NodeMonitors\"}, {\"selected\": false, \"name\": \"RootCAs\"}, {\"selected\": false, \"name\": \"SystemConfiguration\"\r\n}, {\"selected\": false, \"name\": \"SystemProperties\"}, {\"selected\": false, \"name\": \"UpdateCenter\"}, {\"selected\"\r\n: true, \"name\": \"SlowRequestComponent\"}, {\"selected\": true, \"name\": \"DeadlockRequestComponent\"}, {\"selected\"\r\n: true, \"name\": \"PipelineTimings\"}, {\"selected\": false, \"name\": \"PipelineThreadDump\"}, {\"selected\": true\r\n, \"name\": \"ThreadDumps\"}]}";
    DoConfigureInfo doConfigure3 = new DoConfigureInfo();
    doConfigure3.setUp(mixCompoentsSelected);
    j.executeOnServer(doConfigure3);
    assertThat(advisor.getExcludedComponents().size(), is(11));
    managePage = wc.goTo("cloudbees-jenkins-advisor");
    assertTrue(managePage.asText().contains("uncheckedJenkinsLogs"));
    assertTrue(managePage.asText().contains("checkedSlaveLogs"));
  }

  private class DoConfigureInfo implements Callable<HttpResponse> {
    private String testEmail = "";
    private String testPassword = "";
    private String allToEnable = "{\"components\": [{\"selected\": true, \"name\": \"JenkinsLogs\"}, {\"selected\": false, \"name\"\r\n: \"SlaveLogs\"}, {\"selected\": true, \"name\": \"GCLogs\"}, {\"selected\": false, \"name\": \"AgentsConfigFile\"\r\n}, {\"selected\": false, \"name\": \"ConfigFileComponent\"}, {\"selected\": false, \"name\": \"OtherConfigFilesComponent\"\r\n}, {\"selected\": true, \"name\": \"AboutBrowser\"}, {\"selected\": true, \"name\": \"AboutJenkins\"}, {\"selected\"\r\n: true, \"name\": \"AboutUser\"}, {\"selected\": true, \"name\": \"AdministrativeMonitors\"}, {\"selected\": true\r\n, \"name\": \"BuildQueue\"}, {\"selected\": true, \"name\": \"DumpExportTable\"}, {\"selected\": true, \"name\": \"EnvironmentVariables\"\r\n}, {\"selected\": true, \"name\": \"FileDescriptorLimit\"}, {\"selected\": true, \"name\": \"JVMProcessSystemMetricsContents\"\r\n}, {\"selected\": true, \"name\": \"LoadStats\"}, {\"selected\": true, \"name\": \"LoggerManager\"}, {\"selected\"\r\n: true, \"name\": \"Metrics\"}, {\"selected\": true, \"name\": \"NetworkInterfaces\"}, {\"selected\": true, \"name\"\r\n: \"NodeMonitors\"}, {\"selected\": false, \"name\": \"RootCAs\"}, {\"selected\": true, \"name\": \"SystemConfiguration\"\r\n}, {\"selected\": true, \"name\": \"SystemProperties\"}, {\"selected\": true, \"name\": \"UpdateCenter\"}, {\"selected\"\r\n: true, \"name\": \"SlowRequestComponent\"}, {\"selected\": true, \"name\": \"DeadlockRequestComponent\"}, {\"selected\"\r\n: true, \"name\": \"PipelineTimings\"}, {\"selected\": true, \"name\": \"PipelineThreadDump\"}, {\"selected\": true\r\n, \"name\": \"ThreadDumps\"}]}";
    
    public void setUp(String testEmail, String testPassword) {
        this.testEmail = testEmail;
        this.testPassword = testPassword;
    }

    /**
     * Because this is more painful than it should be, just accept a string 
     * that should already be formatted.  It's the burden of the test case
     * to make sure the components are formatted correctly.
     *
     * This call is designed to run successfully.
     */
    public void setUp(String allToEnable) {
      testEmail = email;
      testPassword = password;
      this.allToEnable = allToEnable;
    }

    @Override public HttpResponse call() throws Exception {
        StaplerRequest spyRequest = PowerMockito.spy(Stapler.getCurrentRequest());

        JSONObject json1 = new JSONObject();
        json1.element("email", testEmail);
        json1.element("password", testPassword);
        json1.element("nagDisabled", false);
        json1.element("advanced", new Gson().fromJson(allToEnable, JSONObject.class));
        doReturn(json1)
            .when(spyRequest).getSubmittedForm();
        return advisor.doConfigure(spyRequest);
    }
  }
 
}