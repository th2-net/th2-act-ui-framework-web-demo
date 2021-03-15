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
import com.exactpro.th2.act.framework.ui.constants.SendTextExtraButtons;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.RptViewerDetails;
import com.exactpro.th2.act.grpc.RptViewerSearchDetails;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.common.grpc.EventID;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

public class FindMessageInGui extends TestUIAction<RptViewerSearchDetails> {

	private static final Logger logger = LoggerFactory.getLogger(FindMessageInGui.class);
	
	public static final String OPEN_FILTER_BTN = "//*[@class='messages-window-header']//*[contains(@class, 'filter__title')]";
	public static final String ROW_MSG_TYPE = "(//*[@class='filter']//*[@class='filter__compound'])[2]//*[@class='filter-content']";
	public static final String ROW_BODY_TYPE = "(//*[@class='filter']//*[@class='filter__compound'])[3]//*[@class='filter-content']";
	public static final String BUTTON_APPLY_TYPE = "(//*[@class='filter-row__button'])[2]";
	
	public static final String MESSAGE_SHOW_RAW_XPATH = "(//div[contains(@class, 'message-card')])[1]//*[contains(@class, 'ascii')]";
	public static final String MESSAGE_HEADER_XPATH = "(//div[contains(@class, 'message-card')])[1]//div[contains(@class, 'mc-header')]";
	public static final String MESSAGE_COPY_ALL_XPATH = "(//div[contains(@class, 'message-card')])[1]//div[contains(@class, 'mc-raw__copy-all')]";


	public FindMessageInGui(TestUIFramework framework, StreamObserver<RhBatchResponseDemo> responseObserver) {
		super(framework, responseObserver);
	}


	@Override
	protected String getName() {
		String str = "Search Message In GUI";
		if (description != null && !description.isEmpty()) {
			str += " - " + description;
		}
		return str;
	}

	@Override
	protected String getDescription(RptViewerSearchDetails input) {
		return input.getDescription();
	}

	@Override
	protected Map<String, String> convertRequestParams(RptViewerSearchDetails rptViewerDetails) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("url", rptViewerDetails.getUrl());
		params.put("msg type", rptViewerDetails.getMsgType());
		params.put("msg body", rptViewerDetails.getMsgBody());
		return params;
	}

	@Override
	protected RhSessionID getSessionID(RptViewerSearchDetails rptViewerDetails) {
		return rptViewerDetails.getSessionID();
	}

	@Override
	protected EventID getParentEventId(RptViewerSearchDetails rptViewerDetails) {
		return rptViewerDetails.getEventID();
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	protected void collectActions(RptViewerSearchDetails rptViewerDetails, TestUIFrameworkContext testUIFrameworkContext, ActResult actResult) throws UIFrameworkException {
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
		builderManager.click().locator(WebLocator.byXPath(ExtractMessage.EVENT_XPATH)).wait(5).build();

		builderManager.waitAction().seconds(2).build();
		//filtering
		builderManager.click().locator(WebLocator.byXPath(OPEN_FILTER_BTN)).wait(5).build();
		builderManager.waitAction().seconds(1).build();
		builderManager.click().locator(WebLocator.byXPath(ROW_MSG_TYPE)).build();
		builderManager.sendKeysToActive().text(rptViewerDetails.getMsgType() + SendTextExtraButtons.ENTER.handCommand()).build();
		builderManager.click().locator(WebLocator.byXPath(ROW_BODY_TYPE)).build();
		builderManager.sendKeysToActive().text(rptViewerDetails.getMsgBody() + SendTextExtraButtons.ENTER.handCommand()).build();
		builderManager.click().locator(WebLocator.byXPath(BUTTON_APPLY_TYPE)).build();

		//clicks on show raw
//		builderManager.click().locator(WebLocator.byXPath(MESSAGE_HEADER_XPATH)).wait(30).build();
//		builderManager.waitAction().seconds(1).build();
		builderManager.executeJSElement().locator(WebLocator.byXPath(MESSAGE_SHOW_RAW_XPATH)).wait(20)
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
