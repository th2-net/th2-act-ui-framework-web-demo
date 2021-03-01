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
import com.exactpro.th2.act.framework.exceptions.UIFrameworkException;
import com.exactpro.th2.act.framework.ui.constants.SendTextExtraButtons;
import com.exactpro.th2.act.grpc.ExecutionReportParams;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import com.exactpro.th2.act.grpc.hand.RhBatchResponse;
import com.exactpro.th2.act.grpc.hand.RhSessionID;
import com.exactpro.th2.check1.grpc.Check1Service;
import com.exactpro.th2.check1.grpc.CheckpointRequest;
import com.exactpro.th2.check1.grpc.CheckpointResponse;
import com.exactpro.th2.common.grpc.Checkpoint;
import com.exactpro.th2.common.grpc.EventID;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.protobuf.TextFormat.shortDebugString;

public class SendExecutionReport extends TestUIAction<ExecutionReportParams>
{
	private static final Logger logger = LoggerFactory.getLogger(SendExecutionReport.class);

	private final StreamObserver<RhBatchResponseDemo> responseObserver;
	private final Check1Service verifierConnector;
	private Checkpoint checkpoint;

	public SendExecutionReport(TestUIFramework framework, Check1Service verifierConnector, StreamObserver<RhBatchResponseDemo> responseObserver)
	{
		super(framework);
		this.responseObserver = responseObserver;
		this.verifierConnector = verifierConnector;
	}

	@Override
	protected String getName()
	{
		String[] splitted = StringUtils.splitByCharacterTypeCamelCase(getClass().getSimpleName());
		return StringUtils.join(splitted, " ");
	}

	@Override
	protected Map<String, String> convertRequestParams(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getAllFields().entrySet()
			.stream().filter(e -> e.getValue() instanceof String)
			.collect(Collectors.toMap(e -> e.getKey().getName(), e -> (String) e.getValue()));
	}

	@Override
	protected RhSessionID getSessionID(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getSessionID();
	}

	@Override
	protected EventID getParentEventId(ExecutionReportParams executionReportParams)
	{
		return executionReportParams.getEventID();
	}

	@Override
	protected Logger getLogger()
	{
		return logger;
	}

	@Override
	protected void collectActions(ExecutionReportParams executionReportParams, TestUIFrameworkContext uiFrameworkContext,
			ActResult actResult) throws UIFrameworkException
	{

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
		
		// Choosing session from dropbox
		builderManager.sendKeys().locator(WebLocator.byCssSelector("#session")).wait(5).needClick(true)
				.text(executionReportParams.getSession()).build();
		
		// Choosing msg type from dropbox
		builderManager.sendKeys().locator(WebLocator.byCssSelector("#msg-type")).wait(5).needClick(true)
				.text(executionReportParams.getMessageType() + SendTextExtraButtons.ENTER.handCommand()).build();

		// Waiting 3 sec
		builderManager.waitAction().seconds(3).build();

		// Adding fields from script to message
		WebLocator inputAreaLocator = WebLocator.byCssSelector(".inputarea");
		builderManager.sendKeys().locator(inputAreaLocator).wait(5).needClick(true)
				.text(fillFields(executionReportParams)).build();

		builderManager.getElementScreenshot().locator(inputAreaLocator).build();
		
		// clicking send and extracting table
		builderManager.click().locator(WebLocator.byCssSelector("div.button:nth-child(2")).wait(5).build();
		WebLocator resultTable = WebLocator.byCssSelector("div.result-table");
		builderManager.getElementInnerHtml().locator(resultTable).wait(5).build();

		// Waiting 10 seconds
		builderManager.waitAction().seconds(10).build();
		builderManager.getScreenshot().build();
	}

	private String fillFields(ExecutionReportParams params)
	{
		Map<String, String> fields = convertRequestParams(params);
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> entry : getParamsPositions().entrySet())
		{
			String fieldValue = fields.getOrDefault(entry.getKey(), "");
			Integer relativePosition = entry.getValue();
			sb.append(StringUtils.repeat(SendTextExtraButtons.DOWN.handCommand(), relativePosition))
					.append(SendTextExtraButtons.END.handCommand()).append(SendTextExtraButtons.LEFT.handCommand())
					.append(fieldValue.isEmpty() ? fieldValue : StringUtils.wrap(fieldValue,'"'))
					.append(SendTextExtraButtons.RIGHT.handCommand());
		}

		return sb.toString();
	}

	private Map<String, Integer> getParamsPositions()
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		result.put("BeginString", 2);
		result.put("BodyLength", 1);
		result.put("MsgSeqNum", 2);
		result.put("SenderCompID", 1);
		result.put("TargetCompID", 1);
		result.put("ExecID", 3);
		result.put("ClOrdID", 1);
		result.put("OrderID", 1);
		result.put("LeavesQty", 1);
		result.put("CumQty", 1);
		result.put("SecurityID", 1);
		result.put("PartyID", 5);
		result.put("OrderQty", 8);
		result.put("TransactTime", 1);
		result.put("CheckSum", 2);

		return result;
	}

	@Override
	public void run(ExecutionReportParams details)
	{

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
			
			checkpoint = registerCheckPoint(currentEventId);
			this.collectActions(details, frameworkContext, actResult);
			this.submitActions(details, frameworkContext, actResult);
			actResult.setSessionID(sessionID);

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

	protected static RhBatchResponse.ScriptExecutionStatus convertStatus(ActResult.ActExecutionStatus actStatus)
	{
		switch (actStatus)
		{
			case SUCCESS:
				return RhBatchResponse.ScriptExecutionStatus.SUCCESS;
			case COMPILE_ERROR:
				return RhBatchResponse.ScriptExecutionStatus.COMPILE_ERROR;
			case EXECUTION_ERROR:
				return RhBatchResponse.ScriptExecutionStatus.EXECUTION_ERROR;
			case UNKNOWN_ERROR:
			case ACT_ERROR:
			case HAND_ERROR:
			default:
				return RhBatchResponse.ScriptExecutionStatus.UNRECOGNIZED;
		}
	}

	@Override
	protected void processResult(ActResult actResult) throws UIFrameworkException
	{
		RhBatchResponse.Builder rhBatchResponseBuilder = RhBatchResponse.newBuilder()
				.setScriptStatus(convertStatus(actResult.getScriptStatus()))
				.setSessionId(actResult.getSessionID().toString());
		if (actResult.getErrorInfo() != null)
			rhBatchResponseBuilder.setErrorMessage(actResult.getErrorInfo());
		RhBatchResponse rhBatchResponse = rhBatchResponseBuilder.build();

		RhBatchResponseDemo response = RhBatchResponseDemo.newBuilder()
				.setBatchResponse(rhBatchResponse)
				.setCheckpoint(checkpoint)
				.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
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
