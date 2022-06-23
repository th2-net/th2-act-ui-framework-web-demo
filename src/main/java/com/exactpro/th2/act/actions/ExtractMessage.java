/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.act.events.AdditionalEventInfo;
import com.exactpro.th2.act.framework.TestUIFramework;
import com.exactpro.th2.act.framework.TestUIFrameworkContext;
import com.exactpro.th2.act.framework.UIFrameworkContext;
import com.exactpro.th2.act.framework.builders.web.WebBuilderManager;
import com.exactpro.th2.act.framework.builders.web.WebLocator;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkBuildingException;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.RptViewerDetails;
import com.exactpro.th2.act.grpc.hand.RhBatchResponse;
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
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractMessage.class);
	
	private static final String EVENT_XPATH = "//*[@class='event-header-card__title' and starts-with(@title, 'Send')]";
	private static final String EVENT_EXPAND_XPATH = "//div[@class='event-tree-card' and @style='padding-left: 20px;']//div[contains(@class, 'selected')]/../div[contains(@class, 'expand-icon')]";
	private static final String MESSAGE_SHOW_MESSAGE_MENU_XPATH = "//div[contains(@class, 'attached') and contains(@class, 'message-card')]//div[@class='message-card-tools__ellipsis']";
	private static final String MESSAGE_SHOW_JSON_XPATH = "//div[contains(@class, 'attached') and contains(@class, 'message-card')]//div[@class='message-card-tools__icon json']";
	private static final String MESSAGE_COPY_FULL_XPATH = "//div[contains(@class, 'attached') and contains(@class, 'message-card')]//span[contains(text(),'Copy full')]";
	
	public ExtractMessage(TestUIFramework framework, StreamObserver<RhBatchResponseDemo> responseObserver) {
		super(framework, responseObserver);
	}

	@Override
	protected String getName() {
		String str = "Extract sent message from GUI";
		if (description != null && !description.isEmpty()) {
			str += " - " + description;
		}
		return str;
	}

	@Override
	protected String getDescription(RptViewerDetails input) {
		return input.getDescription();
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
		return LOGGER;
	}

	static void clickOnSendEvent(WebBuilderManager builderManager) throws UIFrameworkBuildingException {
		// waits that event is loaded
		// expand subroot event
		builderManager.waitForElement().locator(WebLocator.byXPath(EVENT_EXPAND_XPATH)).seconds(5).build();
		builderManager.waitAction().seconds(1).build();
		builderManager.click().locator(WebLocator.byXPath(EVENT_EXPAND_XPATH)).build();
		
		// clicks on events to filter and highlight messages
		builderManager.click().locator(WebLocator.byXPath(EVENT_XPATH)).wait(5).build();
	}
	
	@Override
	protected void collectActions(RptViewerDetails rptViewerDetails, TestUIFrameworkContext testUIFrameworkContext, ActResult actResult) throws UIFrameworkException {
		final WebBuilderManager builderManager = testUIFrameworkContext.createBuilderManager();

		//check that URL is correct
		try {
			new URL(rptViewerDetails.getUrl());
		} catch (MalformedURLException e) {
			throw new UIFrameworkBuildingException("Attached URL is not valid");
		}

		builderManager.open().url(rptViewerDetails.getUrl()).build();

		clickOnSendEvent(builderManager);

		// clicks on 'show message menu'
		builderManager.executeJSElement().locator(WebLocator.byXPath(MESSAGE_SHOW_MESSAGE_MENU_XPATH)).wait(30)
				.command("@Element@.click()").build();
		builderManager.click().locator(WebLocator.byXPath(MESSAGE_SHOW_JSON_XPATH)).wait(5).build();

		// clicks 'copy to clipboard'
		builderManager.click().locator(WebLocator.byXPath(MESSAGE_COPY_FULL_XPATH)).wait(5).build();

		builderManager.waitAction().seconds(5).build();

		builderManager.executeJS().command("return await navigator.clipboard.readText()").build();

		builderManager.getScreenshot().build();
	}

	@Override
	protected void submitActions(UIFrameworkContext<?> frameworkContext, ActResult respBuild) {
		AdditionalEventInfo info = null;
		if (!storeParentEvent()) {
			info = createAdditionalEventInfo();
		}
		RhBatchResponse response = frameworkContext.submit(getName(), storeActionMessages(), info);
		if (response == null || response.getScriptStatus() == RhBatchResponse.ScriptExecutionStatus.SUCCESS) {
			respBuild.setStatusInfo(getStatusInfo());
			respBuild.setScriptStatus(ActResult.ActExecutionStatus.SUCCESS);

			if (response != null && !response.getResultList().isEmpty()) {
				respBuild.setData(Map.of("message", response.getResult(0).getResult()));
			}

		} else {
			respBuild.setErrorInfo(response.getErrorMessage());
			respBuild.setScriptStatus(this.convertStatusFromRh(response.getScriptStatus()));
		}
	}

	@Override
	protected String getStatusInfo() {
		return "message extracted";
	}
}