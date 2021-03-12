/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.TextFormat.shortDebugString;

public class SendNewOrderSingle extends TestUIAction<NewOrderSingleParams>
{
	private static final Logger logger = LoggerFactory.getLogger(SendNewOrderSingle.class);

	private final Check1Service verifierConnector;
	private Checkpoint checkpoint;

	public SendNewOrderSingle(TestUIFramework framework, Check1Service verifierConnector, StreamObserver<RhBatchResponseDemo> responseObserver)
	{
		super(framework, responseObserver);
		this.verifierConnector = verifierConnector;
	}

	@Override
	protected String getName() {
		return "Send New Order Single via GUI";
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
	protected Map<String, String> convertRequestParams(NewOrderSingleParams executionReportParams)
	{
		Map<String, String> params = new LinkedHashMap<>(this.getServiceParamsMap(executionReportParams));
		params.putAll(getMgsBodyParamsMap(executionReportParams));
		return params;
	}

	@Override
	protected RhSessionID getSessionID(NewOrderSingleParams executionReportParams)
	{
		return executionReportParams.getSessionID();
	}

	@Override
	protected EventID getParentEventId(NewOrderSingleParams executionReportParams)
	{
		return executionReportParams.getEventID();
	}

	@Override
	protected Logger getLogger()
	{
		return logger;
	}

	@Override
	protected void collectActions(NewOrderSingleParams nosParams, TestUIFrameworkContext uiFrameworkContext,
			ActResult actResult) throws UIFrameworkException {
		
		WebBuilderManager builderManager = uiFrameworkContext.createBuilderManager();

		CustomConfiguration configuration = this.framework.getConfiguration();
		String url ;
		if (!(configuration instanceof TestUIActConfiguration) || 
				StringUtils.isEmpty(url = ((TestUIActConfiguration) configuration).getUrl())) {
			throw new UIFrameworkException("Invalid configuration. Act UI url should be provided");
		}

		// Opening ACT-URL
		builderManager.open().url(url).build();

		// Waiting 3 sec
		builderManager.waitAction().seconds(3).build();
		
		if (!nosParams.getSession().isEmpty()) {
			// Choosing session from dropbox
			builderManager.sendKeys().locator(WebLocator.byCssSelector("#session")).wait(5).needClick(true)
					.text(nosParams.getSession() + SendTextExtraButtons.ENTER.handCommand()).build();

			builderManager.waitAction().seconds(1).build();
		}

		if (!nosParams.getDictionary().isEmpty()) {
			// Choosing session from dropbox
			builderManager.sendKeys().locator(WebLocator.byCssSelector("#dictionary")).wait(5).needClick(true)
					.text(nosParams.getDictionary() + SendTextExtraButtons.ENTER.handCommand()).build();

			builderManager.waitAction().seconds(1).build();
		}
		
		if (!nosParams.getMessageType().isEmpty()) {
			// Choosing msg type from dropbox
			builderManager.sendKeys().locator(WebLocator.byCssSelector("#msg-type")).wait(5).needClick(true)
					.text(nosParams.getMessageType() + SendTextExtraButtons.ENTER.handCommand()).build();

			// Waiting 3 sec
			builderManager.waitAction().seconds(3).build();
		}
		
		uiFrameworkContext.submit("Filling service parameters", getServiceParamsMap(nosParams));
		

		// Adding fields from script to message
		WebLocator inputAreaLocator = WebLocator.byCssSelector(".inputarea");
		builderManager.click().locator(inputAreaLocator).build();
		builderManager.sendKeysToActive().text(UIUtils.keyCombo(SendTextExtraButtons.CONTROL, "a") + 
				SendTextExtraButtons.DELETE).build();
		builderManager.sendKeysToActive().text(SendTextExtraButtons.BACKSPACE.handCommand().repeat(4)).build();
		try {
			builderManager.sendKeysToActive().text(this.createMessageJson(nosParams.getMessage())).build();
		} catch (JsonProcessingException e) {
			throw new UIFrameworkBuildingException("Cannot build json", e);
		}

		// clicking send and extracting table
		builderManager.click().locator(WebLocator.byCssSelector("div.button:nth-child(2")).wait(5).build();

		// Waiting 3 sec
		builderManager.waitAction().seconds(3).build();

		uiFrameworkContext.submit("Filling message body and sending message", getMgsBodyParamsMap(nosParams));
		
		builderManager.getElementAttribute().locator(WebLocator.byXPath("//*[@class='result ok']/pre/a"))
				.attribute("href").wait(20).build();
		
		builderManager.getScreenshot().build();

		RhBatchResponse sending_nos = uiFrameworkContext.submit("Checking sending result");

		ResultDetails resultDetails = sending_nos.getResultList().get(0);
		String urlRpt = resultDetails.getResult();
		if (StringUtils.isNotEmpty(resultDetails.getActionId())) {
			urlRpt = resultDetails.getActionId() + "=" + urlRpt;
		}
		actResult.setData(Collections.singletonMap("url", urlRpt));
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
				logger.error("Unknown list value {}", value.getClass().getSimpleName());
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
					logger.error("Unknown type for {} {}", key.getName(), value.getClass().getName());
				}
			} else {
				logger.error("Unknown type for {} {}", key.getName(), javaType);
			}
		}
		return param;
	}
	
	private String createMessageJson(NewOrderSingleParams.NewOrderSingleBody body) throws JsonProcessingException {
		Map<String, Object> json = convert(body);

		ObjectMapper mapper = new ObjectMapper();
		String str = mapper.writeValueAsString(json);
		
		logger.debug("Built json: {}", str);

		return str;
	}

	@Override
	public void run(NewOrderSingleParams details)
	{
		logger.debug("Executing SendNewOrderSingle");
		RhSessionID sessionID = getSessionID(details);

		ActResult actResult = new ActResult();
		TestUIFrameworkContext frameworkContext = null;
		try {
			frameworkContext = framework.newExecution(sessionID);
			EventID currentEventId = getParentEventId(details);
			if (storeParentEvent()) {
				Map<String, String> requestParams = convertRequestParams(details);
				currentEventId = framework.createParentEvent(currentEventId, getName(), requestParams);
			}
			frameworkContext.setParentEventId(currentEventId);

			logger.debug("Creating checkpoint");
			checkpoint = registerCheckPoint(currentEventId);
			logger.debug("Executing UI steps");
			this.collectActions(details, frameworkContext, actResult);
			this.submitActions(details, frameworkContext, actResult);
			actResult.setSessionID(sessionID);

			logger.debug("Execution finished");
		} catch (UIFrameworkException e) {
			logger.error("Cannot execute", e);
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
			logger.error("Cannot process act result", e);
		}
	}

	@Override
	protected Checkpoint getCheckpoint() {
		return checkpoint;
	}

	private Checkpoint registerCheckPoint(EventID parentEventId) {
		logger.debug("Registering the checkpoint started");
		CheckpointResponse response = verifierConnector.createCheckpoint(CheckpointRequest.newBuilder()
				.setParentEventId(parentEventId)
				.build());
		if (logger.isDebugEnabled()) {
			logger.debug("Registering the checkpoint ended. Response " + shortDebugString(response));
		}
		return response.getCheckpoint();
	}

	@Override
	protected String getStatusInfo()
	{
		return "status info";
	}
}
