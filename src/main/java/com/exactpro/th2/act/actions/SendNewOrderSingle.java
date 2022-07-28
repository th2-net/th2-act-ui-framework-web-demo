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
import com.exactpro.th2.act.TestUIActConfiguration;
import com.exactpro.th2.act.configuration.CustomConfiguration;
import com.exactpro.th2.act.framework.TestUIFramework;
import com.exactpro.th2.act.framework.TestUIFrameworkContext;
import com.exactpro.th2.act.framework.builders.web.WebBuilderManager;
import com.exactpro.th2.act.framework.builders.web.WebLocator;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkBuildingException;
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.framework.ui.constants.SendTextExtraButtons;
import com.exactpro.th2.act.framework.ui.utils.UIUtils;
import com.exactpro.th2.act.grpc.NewOrderSingleParams;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.hand.ResultDetails;
import com.exactpro.th2.act.grpc.hand.RhBatchResponse;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.check1.grpc.Check1Service;
import com.exactpro.th2.check1.grpc.CheckpointRequest;
import com.exactpro.th2.check1.grpc.CheckpointResponse;
import com.exactpro.th2.common.grpc.Checkpoint;
import com.exactpro.th2.common.grpc.EventID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.TextFormat.shortDebugString;

public class SendNewOrderSingle extends TestUIAction<NewOrderSingleParams> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SendNewOrderSingle.class);
	private static final String REPORT_VIEWER_FRAME = "//iframe[contains(@class, 'MuiBox-root')]";
	private static final String OPEN_FILTER_BTN = "//div[@class='messages-list__header']//div[contains(@class, 'filter__title')]";

	private static final String XPATH_FILTER = "(//div[contains(@style, 'visible')]/*[@class='filter']//*[@class='filter__compound'])";
	private static final String TYPE_INCLUDE_TOGGLER = XPATH_FILTER + "[3]//div[@class='toggler '][1]";
	private static final String TYPE_INPUT_FIELD = XPATH_FILTER + "[3]//input";
	private static final String BODY_INPUT_FIELD = XPATH_FILTER + "[5]//input";

	private static final String SUBMIT_FILTER_BUTTON = "//div[@class='filter-row__button' and text()='Submit filter']";

	private static final String MESSAGE_CARD = "//div[contains(@class, 'message-card')]";
	private static final String MESSAGE_MENU_BUTTON = MESSAGE_CARD + "//div[@class='message-card-tools__button']";
	private static final String MESSAGE_COPY_FULL = MESSAGE_CARD + "//span[contains(text(),'Copy full')]";

	private final Check1Service verifierConnector;
	private Checkpoint checkpoint;

	public SendNewOrderSingle(TestUIFramework framework, Check1Service verifierConnector, StreamObserver<RhBatchResponseDemo> responseObserver) {
		super(framework, responseObserver);
		this.verifierConnector = verifierConnector;
	}

	@Override
	protected String getName() {
		String str = "Send New Order Single via GUI";
		if (description != null && !description.isEmpty()) {
			str += " - " + description;
		}
		return str;
	}

	@Override
	protected String getDescription(NewOrderSingleParams input) {
		return input.getDescription();
	}

	private Map<String, String> getServiceParamsMap(NewOrderSingleParams executionReportParams) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("session", executionReportParams.getSession());
		params.put("dictionary", executionReportParams.getDictionary());
		params.put("messageType", executionReportParams.getMessageType());
		return params;
	}

	private Map<String, String> getMgsBodyParamsMap(NewOrderSingleParams executionReportParams) {
		Map<String, String> params = new LinkedHashMap<>();
		var body = executionReportParams.getMessage();
		body.getAllFields().entrySet().stream()
				.filter(ent -> ent.getKey().getJavaType() != JavaType.MESSAGE)
				.forEach(ent -> params.put(ent.getKey().getName(), String.valueOf(ent.getValue())));

		var tradingParty = body.getTradingParty();
		int i = 0;
		for (NewOrderSingleParams.NoPartyID noPartyID : tradingParty.getNoPartyIDsList()) {
			i++;
			var key = "NoPartyId[" + i + "]_";
			noPartyID.getAllFields().forEach((key1, value) -> params.put(key + key1.getName(), String.valueOf(value)));
		}
		return params;
	}
	
	@Override
	protected Map<String, String> convertRequestParams(NewOrderSingleParams executionReportParams) {
		Map<String, String> params = new LinkedHashMap<>(this.getServiceParamsMap(executionReportParams));
		params.putAll(getMgsBodyParamsMap(executionReportParams));
		return params;
	}

	@Override
	protected RhSessionID getSessionID(NewOrderSingleParams executionReportParams) {
		return executionReportParams.getSessionID();
	}

	@Override
	protected EventID getParentEventId(NewOrderSingleParams executionReportParams) {
		return executionReportParams.getEventID();
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

	@Override
	protected void collectActions(NewOrderSingleParams nosParams, TestUIFrameworkContext uiFrameworkContext,
			ActResult actResult) throws UIFrameworkException {
		
		final WebBuilderManager builderManager = uiFrameworkContext.createBuilderManager();

		CustomConfiguration configuration = this.framework.getConfiguration();
		String url ;
		if (!(configuration instanceof TestUIActConfiguration) || 
				StringUtils.isEmpty(url = ((TestUIActConfiguration) configuration).getUrl())) {
			throw new UIFrameworkException("Invalid configuration. Act UI url should be provided");
		}

		// Opening ACT-URL
		builderManager.open().url(url).build();

		if (!nosParams.getSession().isEmpty()) {
			// Opening 'session' dropbox
			builderManager.click().wait(10).locator(WebLocator.byId("session")).build();

			// Choosing session from dropbox
			builderManager.click().wait(5).locator(WebLocator.byXPath("//div[@id='menu-']//li[contains(.,'" + nosParams.getSession() + "')]")).build();
		}

		if (!nosParams.getDictionary().isEmpty()) {
			// Opening 'dictionary' dropbox
			builderManager.click().wait(10).locator(WebLocator.byXPath("//div[@id='dictionary' and not(contains(@class, 'Mui-disabled'))]")).build();

			// Choosing dictionary from dropbox
			builderManager.click().wait(5).locator(WebLocator.byXPath("//div[@id='menu-']//li[contains(.,'" + nosParams.getDictionary() + "')]")).build();
		}
		
		if (!nosParams.getMessageType().isEmpty()) {
			// Opening 'message type' dropbox
			builderManager.click().wait(10).locator(WebLocator.byXPath("//div[@id='msg-type' and not(contains(@class, 'Mui-disabled'))]")).build();

			// Choosing message type from dropbox
			builderManager.click().wait(5).locator(WebLocator.byXPath("//div[@id='menu-']//li[contains(.,'" + nosParams.getMessageType() + "')]")).build();

			builderManager.waitAction().seconds(2).build();
		}
		
		uiFrameworkContext.submit("Filling service parameters");

		// Adding fields from script to message
		builderManager.click().locator(WebLocator.byCssSelector(".view-line")).build();
		builderManager.sendKeysToActive().text(UIUtils.keyCombo(SendTextExtraButtons.CONTROL, "a") +
				SendTextExtraButtons.DELETE).build();
		builderManager.sendKeysToActive().text(SendTextExtraButtons.BACKSPACE.handCommand().repeat(4)).build();
		try {
			builderManager.sendKeysToActive().text(this.createMessageJson(nosParams.getMessage())).build();
		} catch (JsonProcessingException e) {
			throw new UIFrameworkBuildingException("Cannot build json", e);
		}

		// clicking send and extracting table
		builderManager.click().locator(WebLocator.byXPath("//button[contains(text(),'Send')]")).wait(5).build();

		uiFrameworkContext.submit("Filling message body and sending message");

		builderManager.getElementAttribute().locator(WebLocator.byXPath("//a[text()='Report Link']"))
				.attribute("href").wait(20).build();
		
		builderManager.getScreenshot().build();

		// switch to report frame
		builderManager.selectFrame().locator(WebLocator.byXPath(REPORT_VIEWER_FRAME)).wait(3).build();

		//===== setup filter and extract sent message ==================================================================

		// open filter panel
		builderManager.click().locator(WebLocator.byXPath(OPEN_FILTER_BTN)).wait(5).build();

		// toggle 'include/exclude' type filter
		builderManager.click().locator(WebLocator.byXPath(TYPE_INCLUDE_TOGGLER)).wait(3).build();

		// put cursor to type filter input field
		builderManager.click().locator(WebLocator.byXPath(TYPE_INPUT_FIELD)).wait(3).build();

		// clear field and input message type
		builderManager.sendKeysToActive().text("#backspace#NewOrderSingle").wait(3).build();

		// put cursor to body filter input field
		builderManager.click().locator(WebLocator.byXPath(BODY_INPUT_FIELD)).wait(3).build();
		// put ClOrdId to the field
		builderManager.sendKeysToActive().text(nosParams.getMessage().getClOrdID()).wait(3).build();

		// press "Submit filter" button
		builderManager.click().locator(WebLocator.byXPath(SUBMIT_FILTER_BUTTON)).wait(3).build();

		copyMessageFromViewer(builderManager);

		//===== setup filter and extract execution report ==============================================================

		// open filter panel
		builderManager.click().locator(WebLocator.byXPath(OPEN_FILTER_BTN)).wait(5).build();

		// put cursor to type filter input field
		builderManager.click().locator(WebLocator.byXPath(TYPE_INPUT_FIELD)).wait(3).build();

		// clear field and input message type
		builderManager.sendKeysToActive().text("#backspace#ExecutionReport").wait(3).build();

		// press "Submit filter" button
		builderManager.click().locator(WebLocator.byXPath(SUBMIT_FILTER_BUTTON)).wait(3).build();

		copyMessageFromViewer(builderManager);

		//===== executing and collecting result ========================================================================

		final RhBatchResponse sending_nos = uiFrameworkContext.submit("Checking sending result");
		final List<ResultDetails> results = sending_nos.getResultList();
		if (results.isEmpty()) {
			actResult.setErrorInfo("th2-hand didn't return any values (expected URL to rpt-viewer, sent message and execution report).");
			actResult.setScriptStatus(ActResult.ActExecutionStatus.EXECUTION_ERROR);
			return;
		}

		final Map<String, String> res = new HashMap<>(3);

		final ResultDetails resultDetails = results.get(0);
		String urlRpt = resultDetails.getResult();
		if (StringUtils.isNotEmpty(resultDetails.getActionId())) {
			urlRpt = resultDetails.getActionId() + "=" + urlRpt;
		}

		res.put("url", urlRpt);
		if (results.size() == 3) {
			res.put("sent_message", results.get(1).getResult());
			res.put("execution_report", results.get(2).getResult());
		}

		actResult.setData(res);
	}

	private void copyMessageFromViewer(WebBuilderManager builderManager) throws UIFrameworkBuildingException {
		builderManager.click().locator(WebLocator.byXPath(MESSAGE_MENU_BUTTON)).wait(3).build();
		builderManager.click().locator(WebLocator.byXPath(MESSAGE_COPY_FULL)).wait(3).build();
		builderManager.executeJS().command("return await navigator.clipboard.readText()").build();
	}

	private List<?> convertList (List<?> body) {
		List<Object> list = new ArrayList<>();
		for (Object value : body) {
			if (value instanceof String || value instanceof Number) {
				list.add(value);
			} else if (value instanceof GeneratedMessageV3) {
				list.add(convert((GeneratedMessageV3) value));
			} else if (value instanceof List<?>) {
				list.add(convertList((List<?>) value));
			} else {
				LOGGER.error("Unknown list value {}", value.getClass().getSimpleName());
			}
		}
		return list;
	}
	
	private Map<String, Object> convert (GeneratedMessageV3 body) {
		Map<String, Object> param = new LinkedHashMap<>();
		for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : body.getAllFields().entrySet()) {
			var key = entry.getKey();
			var javaType = key.getJavaType();
			if (javaType == JavaType.STRING || javaType == JavaType.INT) {
				param.put(key.getName(), entry.getValue());
			} else if (javaType == JavaType.MESSAGE) {
				var value = entry.getValue();
				if (value instanceof GeneratedMessageV3) {
					param.put(key.getName(), convert((GeneratedMessageV3) value));
				} else if (value instanceof List<?>) {
					param.put(key.getName(), convertList((List<?>) value));
				} else {
					LOGGER.error("Unknown type for {} {}", key.getName(), value.getClass().getName());
				}
			} else {
				LOGGER.error("Unknown type for {} {}", key.getName(), javaType);
			}
		}
		return param;
	}
	
	private String createMessageJson(NewOrderSingleParams.NewOrderSingleBody body) throws JsonProcessingException {
		Map<String, Object> json = convert(body);

		ObjectMapper mapper = new ObjectMapper();
		String str = mapper.writeValueAsString(json);
		
		LOGGER.debug("Built json: {}", str);

		return str;
	}

	@Override
	public void run(NewOrderSingleParams details) {
		LOGGER.debug("Executing SendNewOrderSingle");
		RhSessionID sessionID = getSessionID(details);
		this.description = getDescription(details);

		ActResult actResult = new ActResult();
		TestUIFrameworkContext frameworkContext = null;
		try {
			frameworkContext = framework.newExecution(sessionID);
			EventID currentEventId = getParentEventId(details);
			if (storeParentEvent()) {
				currentEventId = framework.createEvent(currentEventId, getName(), createAdditionalEventInfo());
			}
			frameworkContext.setParentEventId(currentEventId);

			LOGGER.debug("Creating checkpoint");
			checkpoint = registerCheckPoint(currentEventId);
			LOGGER.debug("Executing UI steps");
			this.collectActions(details, frameworkContext, actResult);
			this.submitActions(frameworkContext, actResult);
			actResult.setSessionID(sessionID);

			LOGGER.debug("Execution finished");
		} catch (UIFrameworkException e) {
			LOGGER.error("Cannot execute", e);
			actResult.setScriptStatus(ActResult.ActExecutionStatus.ACT_ERROR);
			actResult.setErrorInfo("Cannot unregister framework session:" + e.getMessage());
		} finally {
			if (frameworkContext != null) {
				framework.onExecutionFinished(frameworkContext);
			}
		}

		try {
			this.processResult(actResult);
		} catch (UIFrameworkException e) {
			LOGGER.error("Cannot process act result", e);
		}
	}

	@Override
	protected Checkpoint getCheckpoint() {
		return checkpoint;
	}

	private Checkpoint registerCheckPoint(EventID parentEventId) {
		LOGGER.debug("Registering the checkpoint started");
		CheckpointResponse response = verifierConnector.createCheckpoint(CheckpointRequest.newBuilder()
				.setParentEventId(parentEventId)
				.build());
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Registering the checkpoint ended. Response " + shortDebugString(response));
		}
		return response.getCheckpoint();
	}

	@Override
	protected String getStatusInfo() {
		return "status info";
	}
}