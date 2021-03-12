/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.act.actions;

import com.exactpro.th2.act.ActResult;
import com.exactpro.th2.act.framework.TestUIFramework;
import com.exactpro.th2.act.framework.TestUIFrameworkContext;
import com.exactpro.th2.act.framework.builders.web.WebBuilderManager;
import com.exactpro.th2.act.framework.builders.web.WebLocator;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkBuildingException;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.RptViewerDetails;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.common.grpc.EventID;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public class ExtractMessage extends TestUIAction<RptViewerDetails>{

	private static final Logger logger = LoggerFactory.getLogger(ExtractMessage.class);
	
	public static final String EVENT_XPATH = "//*[@class='event-header-card__title' and starts-with(@title, 'Send')]";
	public static final String MESSAGE_SHOW_RAW_XPATH = "//div[contains(@class, 'message-card') and contains(@class, 'selected')]//*[contains(@class, 'ascii')]";
	public static final String MESSAGE_HEADER_XPATH = "//div[contains(@class, 'message-card') and contains(@class, 'selected')]//div[contains(@class, 'mc-header')]";
	public static final String MESSAGE_COPY_ALL_XPATH = "//div[contains(@class, 'message-card') and contains(@class, 'selected')]//div[contains(@class, 'mc-raw__copy-all')]";
	
	public ExtractMessage(TestUIFramework framework, StreamObserver<RhBatchResponseDemo> responseObserver) {
		super(framework, responseObserver);
	}

	@Override
	protected String getName() {
		return "Extract sent message from GUI";
	}

	@Override
	protected Map<String, String> convertRequestParams(RptViewerDetails executionReportParams) {
		return Collections.singletonMap("url", executionReportParams.getUrl());
	}

	@Override
	protected RhSessionID getSessionID(RptViewerDetails executionReportParams) {
		return executionReportParams.getSessionID();
	}

	@Override
	protected EventID getParentEventId(RptViewerDetails executionReportParams) {
		return executionReportParams.getEventID();
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	protected void collectActions(RptViewerDetails rptViewerDetails, TestUIFrameworkContext testUIFrameworkContext, ActResult actResult) throws UIFrameworkException {
			
		
		WebBuilderManager builderManager = testUIFrameworkContext.createBuilderManager();

		//check that URL is correct
		try {
			new URL(rptViewerDetails.getUrl());
		} catch (MalformedURLException e) {
			throw new UIFrameworkBuildingException("Attached URL is not valid");
		}

		builderManager.open().url(rptViewerDetails.getUrl()).build();

		//waits that event is loaded
		//clicks on events to filter and highlight messages
		builderManager.click().locator(WebLocator.byXPath(EVENT_XPATH)).wait(5).build();
		
		//clicks on show raw
//		builderManager.click().locator(WebLocator.byXPath(MESSAGE_HEADER_XPATH)).wait(30).build();
//		builderManager.waitAction().seconds(1).build();
		builderManager.executeJSElement().locator(WebLocator.byXPath(MESSAGE_SHOW_RAW_XPATH)).wait(30)
				.command("@Element@.click()").build();
		//clicks on copy all to clipboard
		builderManager.click().locator(WebLocator.byXPath(MESSAGE_COPY_ALL_XPATH)).wait(5).build();
		
		builderManager.waitAction().seconds(5).build();

		builderManager.executeJS().command("return await navigator.clipboard.readText()").build();
		
		builderManager.getScreenshot().build();
	}

	
	@Override
	protected String getStatusInfo() {
		return "message extracted";
	}
}
