package com.cloudbees.jenkins.plugins.advisor.client;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AdvisorClientConfigTest {

    private static final int DEFAULT_UPLOAD_TIMEOUT_MINUTES = 60;
    private static final int DEFAULT_UPLOAD_IDLE_TIMEOUT_MINUTES = 60;

    @Test
    public void advisorUploadTimeoutMinutes() throws Exception {
        int newAdvisorUploadTimeoutMinutes = 1;

        assertThat(AdvisorClientConfig.insightsUploadTimeoutMilliseconds(), is(equalTo((int)TimeUnit.MINUTES.toMillis(DEFAULT_UPLOAD_TIMEOUT_MINUTES))));

        System.setProperty("com.cloudbees.jenkins.plugins.advisor.client.AdvisorClientConfig.advisorUploadTimeoutMinutes", Integer.toString(newAdvisorUploadTimeoutMinutes));

        assertThat(AdvisorClientConfig.insightsUploadTimeoutMilliseconds(), is(equalTo((int)TimeUnit.MINUTES.toMillis(newAdvisorUploadTimeoutMinutes))));

        System.setProperty("com.cloudbees.jenkins.plugins.advisor.client.AdvisorClientConfig.advisorUploadTimeoutMinutes", Integer.toString(DEFAULT_UPLOAD_TIMEOUT_MINUTES));
    }

    @Test
    public void advisorUploadIdleTimeoutMinutes() throws Exception {
        int newAdvisorUploadIdleTimeoutMinutes = 1;

        assertThat(AdvisorClientConfig.insightsUploadIdleTimeoutMilliseconds(), is(equalTo((int)TimeUnit.MINUTES.toMillis(DEFAULT_UPLOAD_IDLE_TIMEOUT_MINUTES))));

        System.setProperty("com.cloudbees.jenkins.plugins.advisor.client.AdvisorClientConfig.advisorUploadIdleTimeoutMinutes", Integer.toString(newAdvisorUploadIdleTimeoutMinutes));

        assertThat(AdvisorClientConfig.insightsUploadIdleTimeoutMilliseconds(), is(equalTo((int)TimeUnit.MINUTES.toMillis(newAdvisorUploadIdleTimeoutMinutes))));

        System.setProperty("com.cloudbees.jenkins.plugins.advisor.client.AdvisorClientConfig.advisorUploadIdleTimeoutMinutes", Integer.toString(DEFAULT_UPLOAD_IDLE_TIMEOUT_MINUTES));
    }
}
